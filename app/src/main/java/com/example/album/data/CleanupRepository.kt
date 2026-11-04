package com.example.album.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject

data class DuplicateGroup(val hash: String, val items: List<MediaItem>)

data class DuplicateScanSnapshot(
    val groups: List<DuplicateGroup>,
    val completed: Boolean
)

internal data class SavedDuplicateGroup(
    val hash: String,
    val uris: List<String>
)

internal data class DuplicateScanCache(
    val groups: List<SavedDuplicateGroup>,
    val completed: Boolean
)

internal fun encodeDuplicateScanCache(cache: DuplicateScanCache): String = JSONObject().apply {
    put("completed", cache.completed)
    put("groups", JSONArray().apply {
        cache.groups.forEach { group ->
            put(JSONObject().apply {
                put("hash", group.hash)
                put("uris", JSONArray(group.uris))
            })
        }
    })
}.toString()

internal fun decodeDuplicateScanCache(raw: String): DuplicateScanCache = runCatching {
    val root = JSONObject(raw)
    val groupsJson = root.optJSONArray("groups") ?: JSONArray()
    val groups = buildList {
        repeat(groupsJson.length()) { index ->
            val groupJson = groupsJson.optJSONObject(index) ?: return@repeat
            val urisJson = groupJson.optJSONArray("uris") ?: JSONArray()
            val uris = buildList {
                repeat(urisJson.length()) { uriIndex ->
                    urisJson.optString(uriIndex).takeIf(String::isNotBlank)?.let(::add)
                }
            }.distinct()
            if (uris.size > 1) {
                add(
                    SavedDuplicateGroup(
                        hash = groupJson.optString("hash").ifBlank {
                            uris.joinToString("-") { it.hashCode().toString(16) }
                        },
                        uris = uris
                    )
                )
            }
        }
    }
    DuplicateScanCache(
        groups = groups,
        completed = root.optBoolean("completed", false)
    )
}.getOrDefault(DuplicateScanCache(emptyList(), completed = false))

/**
 * Candidates for "identical bytes", for pictures of any origin and any name.
 *
 * Two files can only be byte-identical when their size *and* their pixel
 * dimensions agree, and both values are already in the media metadata -- so this
 * step touches no file at all. Measured on a 47k-image library this leaves 408
 * files by size and 42 files once the dimensions have to match as well, which is
 * what turns the check into a handful of reads instead of a library-wide scan.
 *
 * A file whose dimensions are unknown falls back to the size-only group: it must
 * not be skipped just because the metadata is incomplete.
 */
internal fun duplicateCandidates(
    sizes: List<Long>,
    widths: List<Int>,
    heights: List<Int>
): List<Int> {
    val groups = mutableMapOf<String, MutableList<Int>>()
    sizes.forEachIndexed { position, size ->
        if (size <= 0L) return@forEachIndexed
        val width = widths.getOrElse(position) { 0 }
        val height = heights.getOrElse(position) { 0 }
        val key = if (width > 0 && height > 0) "$size:$width:$height" else "$size:?"
        groups.getOrPut(key) { mutableListOf() } += position
    }
    return groups.values.filter { it.size > 1 }.flatten()
}

/**
 * A strict visual fingerprint. Two independent 64-bit hashes catch different
 * kinds of compression/resizing damage, while the 16x16 luminance grid is the
 * final check that prevents two merely similar pictures from being offered for
 * deletion.
 */
internal data class ImageFingerprint(
    val perceptualHash: Long,
    val differenceHash: Long,
    val luminance16: IntArray,
    val red: Float,
    val green: Float,
    val blue: Float,
    val aspect: Float
)

/**
 * The 64-bit dHash of a 9x8 luminance grid: a bit is set when the cell on the
 * left is brighter than the one to its right. Pure, so the bit layout is pinned
 * by a test instead of only existing inside the bitmap code.
 */
internal fun dHashFromLuminanceGrid(grid: IntArray): Long {
    require(grid.size == 9 * 8)
    var hash = 0L
    var bit = 0
    for (row in 0 until 8) {
        for (column in 0 until 8) {
            if (grid[row * 9 + column] > grid[row * 9 + column + 1]) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

/**
 * The 64-bit perceptual hash used by the widely adopted pHash approach. The
 * 32x32 luminance grid is reduced to the low-frequency 8x8 DCT coefficients;
 * bits are set above their median. This survives re-encoding and resizing much
 * better than a single gradient hash while remaining independent of it.
 */
internal fun perceptualHashFromLuminanceGrid(grid: IntArray): Long {
    require(grid.size == 32 * 32)
    val cosines = Array(8) { frequency ->
        DoubleArray(32) { coordinate ->
            kotlin.math.cos((2.0 * coordinate + 1.0) * frequency * Math.PI / 64.0)
        }
    }
    val coefficients = DoubleArray(63)
    val horizontal = Array(8) { DoubleArray(32) }
    for (u in 0 until 8) {
        for (y in 0 until 32) {
            var sum = 0.0
            for (x in 0 until 32) {
                sum += grid[y * 32 + x] * cosines[u][x]
            }
            horizontal[u][y] = sum
        }
    }
    var coefficientIndex = 0
    for (u in 0 until 8) {
        for (v in 0 until 8) {
            if (u == 0 && v == 0) continue
            var sum = 0.0
            for (y in 0 until 32) {
                sum += horizontal[u][y] * cosines[v][y]
            }
            coefficients[coefficientIndex++] = sum
        }
    }
    val median = coefficients.sortedArray()[coefficients.size / 2]
    var hash = 0L
    coefficients.forEachIndexed { bit, value ->
        if (value > median) hash = hash or (1L shl bit)
    }
    return hash
}

private fun lumaMeanAbsoluteDifference(first: IntArray, second: IntArray): Double {
    if (first.size != second.size || first.isEmpty()) return Double.POSITIVE_INFINITY
    var total = 0L
    for (index in first.indices) total += kotlin.math.abs(first[index] - second[index])
    return total.toDouble() / first.size
}

private fun lumaNormalizedCorrelation(first: IntArray, second: IntArray): Double {
    if (first.size != second.size || first.isEmpty()) return Double.NEGATIVE_INFINITY
    val firstMean = first.average()
    val secondMean = second.average()
    var covariance = 0.0
    var firstVariance = 0.0
    var secondVariance = 0.0
    for (index in first.indices) {
        val firstDelta = first[index] - firstMean
        val secondDelta = second[index] - secondMean
        covariance += firstDelta * secondDelta
        firstVariance += firstDelta * firstDelta
        secondVariance += secondDelta * secondDelta
    }
    val denominator = kotlin.math.sqrt(firstVariance * secondVariance)
    return if (denominator <= 1e-9) Double.NEGATIVE_INFINITY else covariance / denominator
}

private fun hasUsefulDetails(luminance: IntArray): Boolean {
    if (luminance.size < 2) return false
    var total = 0L
    var count = 0
    for (index in 1 until luminance.size) {
        total += kotlin.math.abs(luminance[index] - luminance[index - 1])
        count++
    }
    return total.toDouble() / count >= STRICT_MIN_LUMA_DETAIL
}

private fun isUsableVisualHash(hash: Long): Boolean = hash != 0L && hash != -1L

/**
 * Whether two fingerprints represent the same picture rather than merely a
 * similar one. The independent hashes, aspect ratio, mean colour and normalized
 * pixel grid all have to agree. Exact byte duplicates are handled separately,
 * so flat or extremely simple images are intentionally excluded here.
 */
internal fun looksLikeSamePicture(first: ImageFingerprint, second: ImageFingerprint): Boolean {
    if (
        !isUsableVisualHash(first.perceptualHash) ||
        !isUsableVisualHash(second.perceptualHash) ||
        !isUsableVisualHash(first.differenceHash) ||
        !isUsableVisualHash(second.differenceHash)
    ) return false
    if (!hasUsefulDetails(first.luminance16) || !hasUsefulDetails(second.luminance16)) return false
    if (java.lang.Long.bitCount(first.perceptualHash xor second.perceptualHash) > STRICT_PHASH_DISTANCE) return false
    if (java.lang.Long.bitCount(first.differenceHash xor second.differenceHash) > STRICT_DHASH_DISTANCE) return false
    if (!first.aspect.isFinite() || !second.aspect.isFinite()) return false
    val largerAspect = max(first.aspect, second.aspect)
    if (largerAspect <= 0f) return false
    if (kotlin.math.abs(first.aspect - second.aspect) / largerAspect > STRICT_ASPECT_TOLERANCE) return false
    val red = (first.red - second.red).toDouble()
    val green = (first.green - second.green).toDouble()
    val blue = (first.blue - second.blue).toDouble()
    if (kotlin.math.sqrt(red * red + green * green + blue * blue) > STRICT_COLOUR_DISTANCE) return false
    if (lumaMeanAbsoluteDifference(first.luminance16, second.luminance16) > STRICT_LUMA_MEAN_ERROR) return false
    return lumaNormalizedCorrelation(first.luminance16, second.luminance16) >= STRICT_LUMA_CORRELATION
}

/**
 * Groups strict matches with a BK-tree over pHash, following the efficient
 * approximate-search design used by Czkawka. A new picture is compared only
 * with an existing group representative: A-B and B-C can therefore never chain
 * unrelated A and C into one deletion group.
 */
internal fun strictPerceptualGroups(fingerprints: List<ImageFingerprint>): List<List<Int>> {
    if (fingerprints.size < 2) return emptyList()
    val tree = HashTree()
    val groups = mutableListOf<MutableList<Int>>()
    val groupByPosition = IntArray(fingerprints.size) { -1 }
    fingerprints.forEachIndexed { position, candidate ->
        if (
            !isUsableVisualHash(candidate.perceptualHash) ||
            !isUsableVisualHash(candidate.differenceHash) ||
            !hasUsefulDetails(candidate.luminance16)
        ) return@forEachIndexed

        val candidateGroups = linkedSetOf<Int>()
        tree.find(candidate.perceptualHash, STRICT_PHASH_DISTANCE).forEach { nearby ->
            groupByPosition[nearby].takeIf { it >= 0 }?.let(candidateGroups::add)
        }
        val matchingGroup = candidateGroups.firstOrNull { group ->
            looksLikeSamePicture(candidate, fingerprints[groups[group].first()])
        }
        val group = matchingGroup ?: groups.size.also { groups += mutableListOf<Int>() }
        groups[group] += position
        groupByPosition[position] = group
        tree.add(candidate.perceptualHash, position)
    }
    return groups.filter { it.size > 1 }
}

private class HashTree {
    private class Node(val hash: Long) {
        val positions = mutableListOf<Int>()
        val children = mutableMapOf<Int, Node>()
    }

    private var root: Node? = null

    fun add(hash: Long, position: Int) {
        val currentRoot = root
        val start = if (currentRoot == null) {
            Node(hash).also {
                it.positions += position
                root = it
            }
            return
        } else {
            currentRoot
        }
        var node = start
        while (true) {
            val distance = java.lang.Long.bitCount(hash xor node.hash)
            if (distance == 0) {
                node.positions += position
                return
            }
            val child = node.children[distance]
            if (child == null) {
                node.children[distance] = Node(hash).also { it.positions += position }
                return
            }
            node = child
        }
    }

    fun find(hash: Long, radius: Int): List<Int> {
        val start = root ?: return emptyList()
        val result = mutableListOf<Int>()
        val pending = ArrayDeque<Node>()
        pending.add(start)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            val distance = java.lang.Long.bitCount(hash xor node.hash)
            if (distance <= radius) result += node.positions
            val low = (distance - radius).coerceAtLeast(0)
            val high = distance + radius
            node.children.forEach { (edge, child) ->
                if (edge in low..high) pending.add(child)
            }
        }
        return result
    }
}

/**
 * Streams two files side by side and gives up at the first difference. Byte
 * equality is what "duplicate" means here, and stopping early is what makes a
 * library-wide check take seconds instead of minutes.
 */
internal fun sameContent(left: java.io.InputStream, right: java.io.InputStream): Boolean {
    val leftBuffer = ByteArray(COMPARE_BUFFER_BYTES)
    val rightBuffer = ByteArray(COMPARE_BUFFER_BYTES)
    while (true) {
        val leftRead = left.read(leftBuffer)
        val rightRead = right.read(rightBuffer)
        if (leftRead != rightRead) return false
        if (leftRead <= 0) return true
        for (index in 0 until leftRead) {
            if (leftBuffer[index] != rightBuffer[index]) return false
        }
    }
}

data class RecycleEntry(
    val id: String,
    val sourceUri: String,
    val storedPath: String,
    val originalName: String,
    val originalFolder: String,
    val originalRelativePath: String?,
    val mimeType: String,
    val dateTaken: Long,
    val duration: Long,
    val isVideo: Boolean,
    val deletedAt: Long,
    val systemTrashed: Boolean = false,
    /** MediaStore DATE_MODIFIED, stored in epoch seconds. */
    val dateModified: Long = 0L
)

class CleanupRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val thumbnailPreferences = context.getSharedPreferences("album_settings", Context.MODE_PRIVATE)
    private val recycleDirectory = File(context.filesDir, "recycle_bin").apply { mkdirs() }

    /**
     * Analysis results (SHA-256 and perceptual fingerprints) live in one compact
     * text file instead of SharedPreferences: a library-sized preference set is
     * slow to read, slow to prune and rewrites the whole XML on commit, which was
     * a large part of why a repeat scan still felt heavy.
     */
    private val analysisCacheFile = File(context.filesDir, "cleanup_analysis_cache.txt")
    private val duplicateScanCacheFile = File(context.filesDir, "duplicate_scan_results.json")

    /**
     * Finds both byte-identical files and strict visual duplicates. Byte
     * candidates are cheaply narrowed first; the remaining pictures then get a
     * cached visual fingerprint. The caller sees one combined progress stream.
     */
    suspend fun findDuplicateGroups(
        items: List<MediaItem>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        withAnalysisCache(items) { cache ->
            duplicateGroups(items, cache, onProgress).also(::saveDuplicateScanCache)
        }
    }

    suspend fun loadDuplicateScanSnapshot(items: List<MediaItem>): DuplicateScanSnapshot =
        withContext(Dispatchers.IO) {
            val persisted = runCatching {
                if (duplicateScanCacheFile.isFile) {
                    decodeDuplicateScanCache(duplicateScanCacheFile.readText())
                } else {
                    DuplicateScanCache(emptyList(), completed = false)
                }
            }.getOrDefault(DuplicateScanCache(emptyList(), completed = false))
            val byUri = items.associateBy { it.uri.toString() }
            val groups = persisted.groups.mapNotNull { saved ->
                val currentItems = saved.uris.mapNotNull(byUri::get)
                    .distinctBy { it.uri }
                    .sortedByDescending { it.dateTaken }
                if (currentItems.size > 1) DuplicateGroup(saved.hash, currentItems) else null
            }
            DuplicateScanSnapshot(groups, persisted.completed)
        }

    private suspend fun <T> withAnalysisCache(
        items: List<MediaItem>,
        block: suspend (MutableMap<String, String>) -> T
    ): T {
        val cache = loadAnalysisCache()
        val activeUris = items.mapTo(hashSetOf()) { it.uri.toString() }
        return try {
            block(cache)
        } finally {
            // Persist whatever was computed, even when the user cancelled: the
            // next scan picks up where this one stopped instead of redoing it.
            saveAnalysisCache(cache, activeUris)
        }
    }

    private fun cachedVisualFingerprint(
        item: MediaItem,
        cache: MutableMap<String, String>
    ): ImageFingerprint? {
        val key = "visual:${item.uri}"
        val signature = "${item.size}:${item.dateModified}:${VISUAL_FINGERPRINT_VERSION}"
        val stored = cache[key] ?: return null
        val parts = stored.split('|')
        if (parts.size != 8 || parts[0] != signature) return null
        return runCatching {
            val luminance = parts[7].chunked(2).map { it.toInt(16) }.toIntArray()
            require(luminance.size == 16 * 16)
            ImageFingerprint(
                perceptualHash = parts[1].toLong(),
                differenceHash = parts[2].toLong(),
                luminance16 = luminance,
                red = parts[3].toFloat(),
                green = parts[4].toFloat(),
                blue = parts[5].toFloat(),
                aspect = parts[6].toFloat()
            )
        }.getOrNull()
    }

    private fun encodeVisualFingerprint(item: MediaItem, fingerprint: ImageFingerprint): String =
        listOf(
            "${item.size}:${item.dateModified}:${VISUAL_FINGERPRINT_VERSION}",
            fingerprint.perceptualHash.toString(),
            fingerprint.differenceHash.toString(),
            fingerprint.red.toString(),
            fingerprint.green.toString(),
            fingerprint.blue.toString(),
            fingerprint.aspect.toString(),
            fingerprint.luminance16.joinToString("") { value ->
                value.coerceIn(0, 255).toString(16).padStart(2, '0')
            }
        ).joinToString("|")

    /** pHash, dHash, colour and normalized luminance of a decoded thumbnail. */
    private fun fingerprintOf(item: MediaItem, bitmap: android.graphics.Bitmap): ImageFingerprint? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        // One bulk read: the per-pixel accessor is a JNI call each time.
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var red = 0L
        var green = 0L
        var blue = 0L
        val grid = IntArray(32 * 32)
        val cellCounts = IntArray(32 * 32)
        for (y in 0 until height) {
            val rowIndex = (y * 32 / height).coerceIn(0, 31)
            val rowStart = y * width
            for (x in 0 until width) {
                val color = pixels[rowStart + x]
                val r = color shr 16 and 0xff
                val g = color shr 8 and 0xff
                val b = color and 0xff
                red += r.toLong()
                green += g.toLong()
                blue += b.toLong()
                val columnIndex = (x * 32 / width).coerceIn(0, 31)
                val cell = rowIndex * 32 + columnIndex
                grid[cell] += (r * 299 + g * 587 + b * 114) / 1000
                cellCounts[cell] += 1
            }
        }
        for (cell in grid.indices) {
            grid[cell] = if (cellCounts[cell] == 0) 0 else grid[cell] / cellCounts[cell]
        }
        val count = (width.toLong() * height).coerceAtLeast(1L)
        val aspect = when {
            item.width > 0 && item.height > 0 -> item.width.toFloat() / item.height
            else -> width.toFloat() / height
        }
        return ImageFingerprint(
            perceptualHash = perceptualHashFromLuminanceGrid(grid),
            differenceHash = dHashFromLuminanceGrid(resizeLuminanceGrid(grid, 32, 32, 9, 8)),
            luminance16 = resizeLuminanceGrid(grid, 32, 32, 16, 16),
            red = red.toFloat() / count,
            green = green.toFloat() / count,
            blue = blue.toFloat() / count,
            aspect = aspect
        )
    }

    private fun resizeLuminanceGrid(
        source: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): IntArray {
        val target = IntArray(targetWidth * targetHeight)
        val counts = IntArray(target.size)
        for (y in 0 until sourceHeight) {
            val targetY = (y * targetHeight / sourceHeight).coerceIn(0, targetHeight - 1)
            for (x in 0 until sourceWidth) {
                val targetX = (x * targetWidth / sourceWidth).coerceIn(0, targetWidth - 1)
                val targetIndex = targetY * targetWidth + targetX
                target[targetIndex] += source[y * sourceWidth + x]
                counts[targetIndex]++
            }
        }
        for (index in target.indices) {
            target[index] = if (counts[index] == 0) 0 else target[index] / counts[index]
        }
        return target
    }

    private suspend fun duplicateGroups(
        items: List<MediaItem>,
        cache: MutableMap<String, String>,
        onProgress: (completed: Int, total: Int) -> Unit
    ): List<DuplicateGroup> {
        // Only files whose size and pixel dimensions repeat can be identical, and
        // both come straight from the media metadata: no picture is decoded and
        // the loop below only reads the handful of files that could match. That
        // is what keeps the check quick and the phone cool.
        val sizes = items.map { mediaSize(it) ?: 0L }
        val hashingCandidates = duplicateCandidates(
            sizes = sizes,
            widths = items.map { it.width },
            heights = items.map { it.height }
        )
        val pictureIndices = items.indices.filterNot { items[it].isVideo }
        val totalWork = hashingCandidates.size + pictureIndices.size
        val completedWork = AtomicInteger(0)
        fun reportProgress() {
            val completed = completedWork.incrementAndGet().coerceAtMost(totalWork)
            onProgress(completed, totalWork)
        }
        onProgress(0, totalWork)

        val parents = IntArray(items.size) { it }
        fun root(value: Int): Int {
            var current = value
            while (parents[current] != current) {
                parents[current] = parents[parents[current]]
                current = parents[current]
            }
            return current
        }
        fun union(first: Int, second: Int) {
            val a = root(first)
            val b = root(second)
            if (a != b) parents[b] = a
        }

        // Identical bytes, the way a phone's storage cleaner does it. Only files
        // of the same size can match, and only files that also agree on their
        // first 16 KB are worth comparing: the comparison itself streams both
        // files and stops at the first difference, so nothing is hashed.
        val quickSignatures = coroutineScope {
            val gate = Semaphore(ANALYSIS_CONCURRENCY)
            hashingCandidates.map { index ->
                async {
                    gate.withPermit {
                        val value = cachedHeadSignature(items[index], cache)?.let { it to index }
                        reportProgress()
                        value
                    }
                }
            }.awaitAll().filterNotNull()
        }
        quickSignatures.groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }.forEach { same ->
            coroutineContext.ensureActive()
            // Compare against the first member: byte equality is transitive, so
            // one representative per group is enough, and every comparison stops
            // as soon as the two files differ.
            val representative = same.first()
            same.drop(1).forEach { index ->
                coroutineContext.ensureActive()
                if (sameBytes(items[representative], items[index])) union(representative, index)
            }
        }

        val fingerprints = coroutineScope {
            val gate = Semaphore(ANALYSIS_CONCURRENCY)
            pictureIndices.mapIndexed { fingerprintIndex, itemIndex ->
                async {
                    gate.withPermit {
                        coroutineContext.ensureActive()
                        val item = items[itemIndex]
                        var fingerprint = cachedVisualFingerprint(item, cache)
                        if (fingerprint == null) {
                            // The thumbnail cache is already used by the grid, so
                            // this reuses decoded previews whenever available.
                            val bitmap = ThumbnailRepository.load(
                                context,
                                item,
                                VISUAL_FINGERPRINT_SIZE,
                                thumbnailPreferences
                            )
                            if (bitmap != null) {
                                fingerprint = fingerprintOf(item, bitmap)
                                fingerprint?.let {
                                    cache["visual:${item.uri}"] = encodeVisualFingerprint(item, it)
                                }
                            }
                        }
                        reportProgress()
                        fingerprint?.let { fingerprintIndex to it }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        strictPerceptualGroups(fingerprints.map { it.second }).forEach { nearGroup ->
            val members = nearGroup.map { pictureIndices[fingerprints[it].first] }
            val representative = members.first()
            members.drop(1).forEach { union(representative, it) }
        }

        val result = items.indices.groupBy(::root).values
            .filter { it.size > 1 }
            .map { members ->
                val duplicates = members.map { items[it] }.sortedByDescending { it.dateTaken }
                DuplicateGroup(
                    hash = duplicates.joinToString("-") { it.uri.toString().hashCode().toString(16) },
                    items = duplicates
                )
            }
            .sortedByDescending { it.items.size }
        return result
    }

    suspend fun stageForRecycle(items: List<MediaItem>): List<RecycleEntry> = withContext(Dispatchers.IO) {
        val existing = loadRecycleEntries().toMutableList()
        val pendingItems = pendingRecycleItems(items, existing)
        if (pendingItems.isEmpty()) return@withContext emptyList()
        val gate = Semaphore(3)
        // Three copies run at the same time, so a free-space reading taken right
        // before each one starts would let all three pass a check that only one
        // of them really fits. Reserving the size of every copy in flight makes a
        // batch of large videos fail before it fills the data partition.
        val reserved = AtomicLong(0L)
        val staged = coroutineScope {
            pendingItems.map { item ->
                async {
                    gate.withPermit {
                        val size = item.size.coerceAtLeast(1L)
                        val available = recycleDirectory.usableSpace - reserved.get()
                        if (!hasEnoughBackupSpace(item.size, available)) return@withPermit null
                        reserved.addAndGet(size)
                        try {
                            copyIntoRecycle(item)
                        } finally {
                            reserved.addAndGet(-size)
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (staged.isNotEmpty()) saveRecycleEntries(existing + staged)
        staged
    }

    /** Copies one item into the private recycle folder; null when the copy fails. */
    private fun copyIntoRecycle(item: MediaItem): RecycleEntry? {
        var target: File? = null
        return runCatching {
            val id = UUID.randomUUID().toString()
            val extension = item.name.substringAfterLast('.', if (item.isVideo) "mp4" else "jpg")
            target = File(recycleDirectory, "$id.$extension")
            openMediaInputStream(context, item.uri).use { input ->
                requireNotNull(input)
                requireNotNull(target).outputStream().use(input::copyTo)
            }
            RecycleEntry(
                id = id,
                sourceUri = item.uri.toString(),
                storedPath = requireNotNull(target).absolutePath,
                originalName = item.name,
                originalFolder = item.folder,
                originalRelativePath = item.relativePath,
                mimeType = item.mimeType,
                dateTaken = item.dateTaken,
                dateModified = item.dateModified,
                duration = item.duration,
                isVideo = item.isVideo,
                deletedAt = System.currentTimeMillis()
            )
        }.getOrElse {
            // A failed copy must not leave an untracked partial backup consuming storage.
            runCatching { target?.delete() }
            null
        }
    }

    suspend fun stageForSystemRecycle(items: List<MediaItem>): List<RecycleEntry> = withContext(Dispatchers.IO) {
        val existing = loadRecycleEntries()
        val pendingItems = pendingRecycleItems(items, existing)
        if (pendingItems.isEmpty()) return@withContext emptyList()
        val staged = pendingItems.map { item ->
            RecycleEntry(
                id = UUID.randomUUID().toString(),
                sourceUri = item.uri.toString(),
                storedPath = "",
                originalName = item.name,
                originalFolder = item.folder,
                originalRelativePath = item.relativePath,
                mimeType = item.mimeType,
                dateTaken = item.dateTaken,
                dateModified = item.dateModified,
                duration = item.duration,
                isVideo = item.isVideo,
                deletedAt = System.currentTimeMillis(),
                systemTrashed = true
            )
        }
        if (staged.isNotEmpty()) saveRecycleEntries(existing + staged)
        staged
    }

    fun loadRecycleEntries(): List<RecycleEntry> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECYCLE, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                val entry = RecycleEntry(
                    id = json.getString("id"),
                    sourceUri = json.optString("sourceUri"),
                    storedPath = json.getString("storedPath"),
                    originalName = json.getString("originalName"),
                    originalFolder = json.optString("originalFolder", "其他"),
                    originalRelativePath = json.optString("originalRelativePath").takeIf { it.isNotBlank() },
                    mimeType = json.optString("mimeType", "image/*"),
                    dateTaken = json.optLong("dateTaken"),
                    dateModified = json.optLong("dateModified"),
                    duration = json.optLong("duration"),
                    isVideo = json.optBoolean("isVideo"),
                    deletedAt = json.optLong("deletedAt"),
                    systemTrashed = json.optBoolean("systemTrashed")
                )
                if (entry.systemTrashed || File(entry.storedPath).exists()) add(entry)
            }
        }
    }.getOrDefault(emptyList())

    suspend fun restore(entry: RecycleEntry): Boolean = restore(listOf(entry)).isNotEmpty()

    suspend fun restore(entries: List<RecycleEntry>): List<RecycleEntry> = withContext(Dispatchers.IO) {
        val candidates = entries.filterNot { it.systemTrashed }
        if (candidates.isEmpty()) return@withContext emptyList()
        val gate = Semaphore(3)
        val restored = coroutineScope {
            candidates.map { entry ->
                async { gate.withPermit { restoreToMediaStore(entry) } }
            }.awaitAll().mapNotNull { it }
        }
        if (restored.isNotEmpty()) {
            restored.forEach(::deletePrivateBackup)
            val restoredIds = restored.mapTo(hashSetOf()) { it.id }
            saveRecycleEntries(loadRecycleEntries().filterNot { it.id in restoredIds })
        }
        restored
    }

    private suspend fun restoreToMediaStore(entry: RecycleEntry): RecycleEntry? = withContext(Dispatchers.IO) {
        if (entry.systemTrashed) return@withContext null
        val file = File(entry.storedPath)
        if (!file.exists()) return@withContext null
        val collection = if (entry.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, entry.originalName)
            put(MediaStore.MediaColumns.MIME_TYPE, entry.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val folder = entry.originalFolder.replace(Regex("[\\/:*?\"<>|]"), "_")
                val mediaRoot = if (entry.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                val fallbackPath = "$mediaRoot/相册/已还原/$folder/"
                val restorePath = entry.originalRelativePath
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { "${it.trimEnd('/')}/" }
                    ?: fallbackPath
                put(MediaStore.MediaColumns.RELATIVE_PATH, restorePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            if (entry.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, entry.dateTaken)
            if (entry.dateModified > 0) {
                put(MediaStore.MediaColumns.DATE_MODIFIED, entry.dateModified)
            }
        }
        val target = context.contentResolver.insert(collection, values) ?: return@withContext null
        runCatching {
            openMediaOutputStream(context, target).use { output ->
                requireNotNull(output)
                file.inputStream().use { it.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(target, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    if (entry.dateModified > 0) {
                        put(MediaStore.MediaColumns.DATE_MODIFIED, entry.dateModified)
                    }
                }, null, null)
            }
            entry
        }.getOrElse {
            context.contentResolver.delete(target, null, null)
            null
        }
    }

    fun removeRecycleEntry(entry: RecycleEntry): Boolean {
        deletePrivateBackup(entry)
        val remaining = loadRecycleEntries().filterNot { it.id == entry.id }
        saveRecycleEntries(remaining)
        return true
    }

    fun removeRecycleEntries(entries: List<RecycleEntry>): Boolean {
        if (entries.isEmpty()) return true
        val ids = entries.mapTo(hashSetOf()) { it.id }
        entries.forEach(::deletePrivateBackup)
        saveRecycleEntries(loadRecycleEntries().filterNot { it.id in ids })
        return true
    }

    fun discardRecycleEntries(ids: Set<String>) {
        val entries = loadRecycleEntries()
        entries.filter { it.id in ids }.forEach(::deletePrivateBackup)
        saveRecycleEntries(entries.filterNot { it.id in ids })
    }

    fun purgeExpired(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays * RECYCLE_DAY_MILLIS
        val entries = loadRecycleEntries()
        val expiredPrivateEntries = entries.filter { !it.systemTrashed && it.deletedAt < cutoff }
        expiredPrivateEntries.forEach(::deletePrivateBackup)
        saveRecycleEntries(entries - expiredPrivateEntries.toSet())
    }

    private fun deletePrivateBackup(entry: RecycleEntry) {
        if (!entry.systemTrashed && entry.storedPath.isNotBlank()) File(entry.storedPath).delete()
    }

    private fun mediaSize(item: MediaItem): Long? {
        if (item.size > 0L) return item.size
        context.contentResolver.query(item.uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return runCatching { context.contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { it.length } }.getOrNull()
    }

    /**
     * Size plus the first 16 KB. This is the whole pre-filter for "identical
     * bytes": files that disagree here cannot be identical, and files that agree
     * are compared directly instead of being hashed end to end.
     */
    private fun cachedHeadSignature(item: MediaItem, cache: MutableMap<String, String>): String? {
        val key = "head:${item.uri}"
        val signature = analysisSignature(item)
        cache[key]?.let { stored ->
            if (stored.startsWith("$signature|")) return stored.substringAfter('|')
        }
        val value = headSignature(item) ?: return null
        cache[key] = "$signature|$value"
        return value
    }

    private fun headSignature(item: MediaItem): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(item.size.toString().toByteArray())
        openMediaInputStream(context, item.uri).use { input ->
            requireNotNull(input)
            val buffer = ByteArray(HEAD_SIGNATURE_BYTES)
            val head = input.read(buffer)
            if (head > 0) digest.update(buffer, 0, head)
        }
        digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }.getOrNull()

    /** Opens both files and compares them with [sameContent]. */
    private fun sameBytes(first: MediaItem, second: MediaItem): Boolean = runCatching {
        openMediaInputStream(context, first.uri).use { left ->
            openMediaInputStream(context, second.uri).use { right ->
                left != null && right != null && sameContent(left, right)
            }
        }
    }.getOrDefault(false)

    /**
     * One line per cached value, `<key>\t<value>`. Loading is a single buffered
     * read; saving drops entries whose files are no longer in the library.
     */
    private fun loadAnalysisCache(): MutableMap<String, String> = runCatching {
        val entries = java.util.concurrent.ConcurrentHashMap<String, String>()
        if (!analysisCacheFile.isFile) return@runCatching entries
        analysisCacheFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val separator = line.indexOf('\t')
                if (separator > 0) entries[line.substring(0, separator)] = line.substring(separator + 1)
            }
        }
        entries
    }.getOrDefault(java.util.concurrent.ConcurrentHashMap())

    private fun saveAnalysisCache(cache: Map<String, String>, activeUris: Set<String>) {
        runCatching {
            val temporary = File(context.filesDir, "${analysisCacheFile.name}.tmp")
            temporary.bufferedWriter().use { writer ->
                cache.forEach { (key, value) ->
                    val uri = key.substringAfter(':', "")
                    // Keep both byte heads and visual fingerprints so repeat
                    // scans do not decode or compare the same pictures again.
                    if (!key.startsWith("head:") && !key.startsWith("visual:")) return@forEach
                    if (uri.isNotEmpty() && uri !in activeUris) return@forEach
                    writer.append(key).append('\t').append(value).append('\n')
                }
            }
            if (!temporary.renameTo(analysisCacheFile)) {
                analysisCacheFile.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    private fun saveDuplicateScanCache(groups: List<DuplicateGroup>) {
        runCatching {
            val serialized = encodeDuplicateScanCache(
                DuplicateScanCache(
                    groups = groups.map { group ->
                        SavedDuplicateGroup(
                            hash = group.hash,
                            uris = group.items.map { it.uri.toString() }
                        )
                    },
                    completed = true
                )
            )
            val temporary = File(context.filesDir, "${duplicateScanCacheFile.name}.tmp")
            temporary.writeText(serialized)
            if (!temporary.renameTo(duplicateScanCacheFile)) {
                duplicateScanCacheFile.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    // The version suffix invalidates values computed by an older decoder or hash
    // implementation: the same picture would hash differently.
    private fun analysisSignature(item: MediaItem): String = "${item.size}:${item.dateModified}:px2"

    private fun saveRecycleEntries(entries: List<RecycleEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("sourceUri", entry.sourceUri)
                put("storedPath", entry.storedPath)
                put("originalName", entry.originalName)
                put("originalFolder", entry.originalFolder)
                put("originalRelativePath", entry.originalRelativePath ?: "")
                put("mimeType", entry.mimeType)
                put("dateTaken", entry.dateTaken)
                put("dateModified", entry.dateModified)
                put("duration", entry.duration)
                put("isVideo", entry.isVideo)
                put("deletedAt", entry.deletedAt)
                put("systemTrashed", entry.systemTrashed)
            })
        }
        preferences.edit().putString(KEY_RECYCLE, array.toString()).apply()
    }

    companion object {
        private const val PREFERENCES = "cleanup_preferences"
        private const val ANALYSIS_PREFERENCES = "cleanup_analysis_cache"
        private const val KEY_RECYCLE = "recycle_entries"
    }
}

internal fun pendingRecycleItems(items: List<MediaItem>, existing: List<RecycleEntry>): List<MediaItem> {
    val pendingSources = pendingRecycleSourceUris(
        requestedSources = items.map { it.uri.toString() },
        existingSources = existing.map { it.sourceUri }.toSet()
    ).toHashSet()
    return items.filter { pendingSources.remove(it.uri.toString()) }
}

internal fun pendingRecycleSourceUris(
    requestedSources: List<String>,
    existingSources: Set<String>
): List<String> = requestedSources.distinct().filterNot { it in existingSources }

internal fun hasEnoughBackupSpace(fileSize: Long, usableSpace: Long): Boolean =
    usableSpace >= fileSize.coerceAtLeast(1L) + MIN_BACKUP_FREE_SPACE_BYTES

/** Bytes the private recycle bin currently occupies on disk. */
internal fun privateRecycleBytes(entries: List<RecycleEntry>): Long =
    entries.filterNot { it.systemTrashed }
        .sumOf { entry -> runCatching { File(entry.storedPath).length() }.getOrDefault(0L) }

/**
 * Whole days left before the retention pass removes the entry, so a file with
 * a few hours to go still reads as "1 day" instead of "0". Zero means the entry
 * is due today; callers should skip the label when the retention is disabled.
 */
internal fun recycleRemainingDays(
    deletedAt: Long,
    retentionDays: Int,
    now: Long = System.currentTimeMillis()
): Int {
    if (retentionDays <= 0) return 0
    val remaining = deletedAt + retentionDays * RECYCLE_DAY_MILLIS - now
    if (remaining <= 0L) return 0
    return ((remaining + RECYCLE_DAY_MILLIS - 1) / RECYCLE_DAY_MILLIS).toInt()
}

internal const val RECYCLE_DAY_MILLIS = 24L * 60L * 60L * 1000L

private const val MIN_BACKUP_FREE_SPACE_BYTES = 8L * 1024L * 1024L

/**
 * How many candidate files are read at once while checking for duplicates. Only
 * a few dozen files reach this point, so a small number keeps the disk busy
 * enough to be quick without heating the phone up.
 */
private const val ANALYSIS_CONCURRENCY = 4

/** Thumbnail size used for visual fingerprints; matches a common grid size. */
private const val VISUAL_FINGERPRINT_SIZE = 256

/** Bumped when the fingerprint changes, so stale cached values are ignored. */
private const val VISUAL_FINGERPRINT_VERSION = "v2"

// These are deliberately tighter than general "similar image" tools because
// their results are offered for destructive cleanup.
private const val STRICT_PHASH_DISTANCE = 4
private const val STRICT_DHASH_DISTANCE = 5
private const val STRICT_ASPECT_TOLERANCE = .01f
private const val STRICT_COLOUR_DISTANCE = 12.0
private const val STRICT_LUMA_MEAN_ERROR = 8.0
private const val STRICT_LUMA_CORRELATION = .995
private const val STRICT_MIN_LUMA_DETAIL = 2.0

/** Head bytes hashed by the cheap signature before a direct comparison. */
private const val HEAD_SIGNATURE_BYTES = 16 * 1024

/** Chunk size used while comparing two candidate files. */
private const val COMPARE_BUFFER_BYTES = 64 * 1024
