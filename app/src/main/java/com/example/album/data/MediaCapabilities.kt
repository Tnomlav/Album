package com.example.album.data

/** Where a media item physically lives, from the app's point of view. */
enum class StorageKind {
    /** Indexed by MediaStore (the normal gallery case). */
    MediaStore,

    /** A SAF document (a folder the user authorized instead of MediaStore). */
    Document,

    /** A direct file path (the app's own recycle bin, downloads, ...). */
    Physical
}

/**
 * What the app may do to an item of a given kind on this platform.
 *
 * This replaces ad-hoc `if (item.isDocument || Build.VERSION.SDK_INT < Q)`
 * checks scattered over the transfer code: callers ask for a capability
 * instead of re-deriving it from three different fields. The values are pure
 * functions of the kind and the API level so they can be unit tested.
 */
data class MediaCapabilities(
    val kind: StorageKind,
    /** The provider can move the item in place (rename/move without copying). */
    val supportsProviderMove: Boolean,
    /** The app can write a new copy (resolver insert or direct file write). */
    val supportsDirectWrite: Boolean,
    /** A move keeps the original timestamps instead of stamping "now". */
    val preservesTimestamps: Boolean,
    /** The item can be renamed in place. */
    val supportsRename: Boolean
)

/** Android 10: MediaStore gained RELATIVE_PATH, which is what enables in-place moves. */
internal const val MIN_PROVIDER_MOVE_API = 29

fun storageKindOf(item: MediaItem): StorageKind = when {
    item.isDocument -> StorageKind.Document
    item.uri.scheme.equals("file", ignoreCase = true) -> StorageKind.Physical
    else -> StorageKind.MediaStore
}

fun capabilitiesOf(kind: StorageKind, apiLevel: Int): MediaCapabilities = when (kind) {
    StorageKind.MediaStore -> MediaCapabilities(
        kind = kind,
        supportsProviderMove = apiLevel >= MIN_PROVIDER_MOVE_API,
        supportsDirectWrite = true,
        preservesTimestamps = apiLevel >= MIN_PROVIDER_MOVE_API,
        supportsRename = apiLevel >= MIN_PROVIDER_MOVE_API
    )
    // SAF can move a document through its provider, but the app does not use
    // DocumentFile.renameTo for moves (providers disagree); it copies and then
    // deletes, which keeps timestamps.
    StorageKind.Document -> MediaCapabilities(
        kind = kind,
        supportsProviderMove = false,
        supportsDirectWrite = true,
        preservesTimestamps = true,
        supportsRename = false
    )
    StorageKind.Physical -> MediaCapabilities(
        kind = kind,
        supportsProviderMove = false,
        supportsDirectWrite = true,
        preservesTimestamps = true,
        supportsRename = true
    )
}

/** Which move/copy mechanism to try, in order. */
enum class TransferStrategy {
    /** Provider-side move (MediaStore RELATIVE_PATH update). */
    ProviderMove,

    /** DocumentFile rename inside an authorized tree. */
    DocumentMove,

    /** Write a copy into an authorized tree or a physical directory. */
    DirectoryCopy,

    /** Insert into MediaStore and copy the bytes through the resolver. */
    MediaStoreCopy,

    /** Copy next to the destination and delete the source afterwards (Move). */
    CopyThenDelete
}

/**
 * The ordered strategies for one transfer.
 *
 * The implementation still discovers the destination at runtime (an authorized
 * tree, a writable directory or MediaStore itself); this plan decides the order
 * of attempts within that discovery, and keeps the "why" in one testable place.
 */
data class TransferPlan(
    val strategies: List<TransferStrategy>,
    /** True when a provider move may be attempted before touching the tree. */
    val tryProviderMoveFirst: Boolean
)

fun planTransfer(
    mode: TransferMode,
    sourceKind: StorageKind,
    apiLevel: Int
): TransferPlan {
    val capabilities = capabilitiesOf(sourceKind, apiLevel)
    val strategies = buildList {
        if (mode == TransferMode.Move && capabilities.supportsProviderMove) {
            add(TransferStrategy.ProviderMove)
        }
        if (sourceKind == StorageKind.MediaStore) {
            add(TransferStrategy.MediaStoreCopy)
        } else {
            add(TransferStrategy.DirectoryCopy)
        }
        if (mode == TransferMode.Move) add(TransferStrategy.CopyThenDelete)
    }
    return TransferPlan(
        strategies = strategies,
        tryProviderMoveFirst = strategies.firstOrNull() == TransferStrategy.ProviderMove
    )
}
