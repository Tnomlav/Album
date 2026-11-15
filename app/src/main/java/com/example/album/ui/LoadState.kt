package com.example.album.ui

/**
 * Where a library scan came from.
 *
 * The pull-to-refresh indicator is only appropriate for [UserPull]; a startup
 * scan, a ContentObserver reload or a reload after an operation must not look
 * like something the user asked for.
 */
enum class RefreshSource {
    Startup,
    UserPull,
    Observer,
    Operation
}

/**
 * The single description of "is the library loading, and why".
 *
 * This replaces the `loading` / `refreshSource` / `initialLoadComplete` triple
 * that used to be three independent booleans: a refresh that had been requested
 * but not started yet (queued behind another scan) could not be represented, so
 * the UI needed extra flags to guess.
 *
 * @property initialScanComplete false until the first scan finishes. An empty
 * library means "not loaded yet" before that point.
 * @property active the scan that is running right now.
 * @property queued a scan that has been requested but is waiting for [active].
 */
data class LoadState(
    val initialScanComplete: Boolean = false,
    val active: RefreshSource? = null,
    val queued: RefreshSource? = null
) {
    val isRefreshing: Boolean get() = active != null || queued != null

    /** True while a finger pull is being served, including while it is queued. */
    val isUserPull: Boolean get() =
        active == RefreshSource.UserPull || queued == RefreshSource.UserPull

    companion object {
        /** The state to use where there is no library (previews, tests). */
        val Ready = LoadState(initialScanComplete = true)
    }
}
