package com.example.album.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The single entry point for asking the library to reload.
 *
 * It marks the request as queued synchronously (on the UI thread) before the
 * scan coroutine is dispatched, so the pull-to-refresh indicator appears in the
 * same frame as the gesture even when another scan is still running. The scan
 * itself is serialized by [MediaLibraryState.refresh].
 */
class RefreshCoordinator(
    private val scope: CoroutineScope,
    private val library: MediaLibraryState
) {
    fun request(
        source: RefreshSource = RefreshSource.Operation,
        granted: Boolean = library.permissionGranted,
        scheduleThumbnailOptimization: Boolean = true
    ) {
        library.markQueued(source)
        scope.launch {
            try {
                library.refresh(granted, scheduleThumbnailOptimization, source)
            } finally {
                library.clearQueued(source)
            }
        }
    }
}
