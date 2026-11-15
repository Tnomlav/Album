package com.example.album.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** How often a copy reports progress, so callbacks stay cheap. */
internal const val COPY_PROGRESS_STEP_BYTES = 256L * 1024L

/**
 * Copies [input] to [output], reporting the number of bytes written so far.
 * Returns the total number of bytes copied.
 */
internal fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    onProgress: ((Long) -> Unit)? = null
): Long {
    val buffer = ByteArray(COPY_PROGRESS_STEP_BYTES.toInt())
    var total = 0L
    var pending = 0L
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
        total += read
        pending += read
        if (pending >= COPY_PROGRESS_STEP_BYTES) {
            pending = 0L
            onProgress?.invoke(total)
        }
    }
    onProgress?.invoke(total)
    return total
}

/**
 * Writes one file next to [target] under a temporary name and only replaces
 * [target] once the copy is complete.
 *
 * This is the plain-file half of an overwrite: the destination keeps its old
 * content while the new bytes are still being written, and a failed copy leaves
 * both the destination and the disk free of a half-written file.
 */
internal fun writeFileReplacing(
    target: File,
    input: InputStream,
    onProgress: ((Long) -> Unit)? = null,
    timestampMs: Long = 0L
): Boolean {
    val directory = target.parentFile ?: return false
    val temporary = File(directory, temporaryTransferName(target.name))
    return try {
        temporary.outputStream().use { output -> copyWithProgress(input, output, onProgress) }
        if (!temporary.isFile || temporary.length() <= 0L) {
            temporary.delete()
            return false
        }
        if (target.exists() && !target.delete()) {
            temporary.delete()
            return false
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            return false
        }
        if (timestampMs > 0L) target.setLastModified(timestampMs)
        true
    } catch (_: Exception) {
        runCatching { temporary.delete() }
        false
    }
}
