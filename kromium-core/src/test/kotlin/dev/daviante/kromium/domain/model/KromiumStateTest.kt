package dev.daviante.kromium.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KromiumStateTest {

    @Test
    fun testStateHierarchy() {
        val idle: KromiumState = KromiumState.Idle
        val locating: KromiumState = KromiumState.Locating
        val downloading: KromiumState = KromiumState.Downloading(DownloadProgress.Initial)
        val extracting: KromiumState = KromiumState.Extracting
        val initializing: KromiumState = KromiumState.Initializing
        val ready: KromiumState = KromiumState.Ready
        val error: KromiumState = KromiumState.Error(RuntimeException("Test error"))
        val disposed: KromiumState = KromiumState.Disposed

        // Ensure distinct types and correct hierarchy
        assertTrue(idle is KromiumState.Idle)
        assertTrue(locating is KromiumState.Locating)
        assertTrue(downloading is KromiumState.Downloading)
        assertTrue(extracting is KromiumState.Extracting)
        assertTrue(initializing is KromiumState.Initializing)
        assertTrue(ready is KromiumState.Ready)
        assertTrue(error is KromiumState.Error)
        assertTrue(disposed is KromiumState.Disposed)

        assertEquals("Test error", (error as KromiumState.Error).cause.message)
    }
}
