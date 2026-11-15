package com.example.album.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadStateTest {
    @Test
    fun `a fresh state has not completed its first scan`() {
        assertFalse(LoadState().initialScanComplete)
        assertFalse(LoadState().isRefreshing)
    }

    @Test
    fun `a running user pull is a user pull`() {
        val state = LoadState(initialScanComplete = true, active = RefreshSource.UserPull)
        assertTrue(state.isUserPull)
        assertTrue(state.isRefreshing)
    }

    @Test
    fun `a queued user pull is still a user pull`() {
        val state = LoadState(
            initialScanComplete = true,
            active = RefreshSource.Observer,
            queued = RefreshSource.UserPull
        )
        assertTrue(state.isUserPull)
    }

    @Test
    fun `background scans are not user pulls`() {
        assertFalse(LoadState(active = RefreshSource.Observer).isUserPull)
        assertFalse(LoadState(active = RefreshSource.Startup).isUserPull)
        assertFalse(LoadState(active = RefreshSource.Operation).isUserPull)
    }

    @Test
    fun `ready is a completed idle state`() {
        assertTrue(LoadState.Ready.initialScanComplete)
        assertFalse(LoadState.Ready.isRefreshing)
    }

    @Test
    fun `copy keeps the queued source while an active scan runs`() {
        val queued = LoadState(initialScanComplete = true).copy(queued = RefreshSource.Operation)
        val active = queued.copy(active = RefreshSource.Startup, queued = null)
        assertTrue(active.isRefreshing)
        assertFalse(active.isUserPull)
    }
}
