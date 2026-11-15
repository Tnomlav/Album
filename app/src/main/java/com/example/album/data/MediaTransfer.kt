package com.example.album.data

import android.net.Uri

enum class TransferMode {
    Copy,
    Move
}

enum class ConflictPolicy(val label: String) {
    KeepBoth("保留两者"),
    Overwrite("覆盖"),
    Skip("跳过")
}

/** Why a copy/move failed, so the UI can explain instead of only saying "failed". */
enum class TransferFailure {
    SourceUnreadable,
    DestinationUnwritable,
    ConflictUnresolved,
    VerificationFailed,
    SourceDeleteFailed,
    Unknown
}

data class TransferResult(
    val item: MediaItem,
    val success: Boolean,
    val skipped: Boolean = false,
    val targetName: String? = null,
    val movedDirectly: Boolean = false,
    val failure: TransferFailure? = null
)

/** Result of moving a document into an exact destination directory. */
data class DirectMoveResult(
    val uri: Uri,
    val name: String
)

data class TransferRequest(
    val items: List<MediaItem>,
    val mode: TransferMode,
    /**
     * The transfer was started from the Pixiv archive page. Its destination is
     * an artist folder that the Pixiv page owns, so the app returns there and
     * opens the folder instead of jumping to the album grid.
     */
    val fromPixivArchive: Boolean = false
)

data class TransferTargetName(
    val name: String,
    val skipped: Boolean = false
)

internal fun resolveTransferTargetName(
    originalName: String,
    existingNames: Set<String>,
    conflictPolicy: ConflictPolicy
): TransferTargetName {
    if (originalName !in existingNames) return TransferTargetName(originalName)
    return when (conflictPolicy) {
        ConflictPolicy.Skip -> TransferTargetName(originalName, skipped = true)
        ConflictPolicy.Overwrite -> TransferTargetName(originalName)
        ConflictPolicy.KeepBoth -> {
            val dot = originalName.lastIndexOf('.')
            val base = if (dot > 0) originalName.substring(0, dot) else originalName
            val extension = if (dot > 0) originalName.substring(dot) else ""
            var index = 1
            var candidate: String
            do {
                candidate = "$base ($index)$extension"
                index++
            } while (candidate in existingNames)
            TransferTargetName(candidate)
        }
    }
}


/**
 * Name used while an overwrite is in flight.
 *
 * The new copy is written under this name first; only after it is complete does
 * the old destination get removed and this one take the real name. That keeps
 * the destination file intact if the copy fails halfway.
 */
internal fun temporaryTransferName(originalName: String): String {
    val dot = originalName.lastIndexOf('.')
    val base = if (dot > 0) originalName.substring(0, dot) else originalName
    val extension = if (dot > 0) originalName.substring(dot) else ""
    return base + "~album-tmp" + System.nanoTime() + extension
}

internal fun resolveMediaStoreRelativePath(destinationFolder: String): String {
    val normalized = destinationFolder.trim('/').replace('\\', '/')
    return "$normalized/"
}
