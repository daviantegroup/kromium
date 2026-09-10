package dev.daviante.kromium.presentation.browser

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationAwaiterTest {

    private fun setupMockBrowser(): Triple<KromiumClient, CefBrowser, KromiumBrowser> {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val client = KromiumClient(mockRawClient)
        val mockRawBrowser = mockk<CefBrowser>(relaxed = true)
        every { mockRawBrowser.identifier } returns 42
        every { mockRawBrowser.isLoading } returns false

        val browser = KromiumBrowser(client, mockRawBrowser)
        return Triple(client, mockRawBrowser, browser)
    }

    private fun createMockFrame(isMain: Boolean): CefFrame {
        val frame = mockk<CefFrame>(relaxed = true)
        every { frame.isMain } returns isMain
        return frame
    }

    @Test
    fun testIsLoadingProperty() {
        val (_, mockRawBrowser, browser) = setupMockBrowser()
        every { mockRawBrowser.isLoading } returns true
        assertTrue(browser.isLoading)

        every { mockRawBrowser.isLoading } returns false
        assertFalse(browser.isLoading)
    }

    @Test
    fun testWaitForNavigationStarted() = runTest {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val client = KromiumClient(mockRawClient)
        val loadHandlerSlot = slot<CefLoadHandler>()
        verify(atLeast = 1) { mockRawClient.addLoadHandler(capture(loadHandlerSlot)) }

        val mockRawBrowser = mockk<CefBrowser>(relaxed = true)
        every { mockRawBrowser.identifier } returns 101

        val browser = KromiumBrowser(client, mockRawBrowser)
        val mainFrame = createMockFrame(isMain = true)
        val compositeHandler = loadHandlerSlot.captured

        launch {
            delay(20)
            compositeHandler.onLoadStart(mockRawBrowser, mainFrame, null)
        }

        val reached = browser.waitForNavigation(NavigationStage.STARTED, timeoutMs = 1000L)
        assertTrue(reached, "waitForNavigation(STARTED) should return true when main frame starts loading")
    }

    @Test
    fun testWaitForNavigationLoaded() = runTest {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val client = KromiumClient(mockRawClient)
        val loadHandlerSlot = slot<CefLoadHandler>()
        verify(atLeast = 1) { mockRawClient.addLoadHandler(capture(loadHandlerSlot)) }

        val mockRawBrowser = mockk<CefBrowser>(relaxed = true)
        every { mockRawBrowser.identifier } returns 102

        val browser = KromiumBrowser(client, mockRawBrowser)
        val mainFrame = createMockFrame(isMain = true)
        val compositeHandler = loadHandlerSlot.captured

        launch {
            delay(20)
            compositeHandler.onLoadEnd(mockRawBrowser, mainFrame, 200)
        }

        val reached = browser.waitForNavigation(NavigationStage.LOADED, timeoutMs = 1000L)
        assertTrue(reached, "waitForNavigation(LOADED) should return true when main frame finishes loading")
    }

    @Test
    fun testWaitForNavigationSubframeIgnored() = runTest {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val client = KromiumClient(mockRawClient)
        val loadHandlerSlot = slot<CefLoadHandler>()
        verify(atLeast = 1) { mockRawClient.addLoadHandler(capture(loadHandlerSlot)) }

        val mockRawBrowser = mockk<CefBrowser>(relaxed = true)
        every { mockRawBrowser.identifier } returns 103

        val browser = KromiumBrowser(client, mockRawBrowser)
        val subframe = createMockFrame(isMain = false)
        val compositeHandler = loadHandlerSlot.captured

        launch {
            delay(15)
            compositeHandler.onLoadEnd(mockRawBrowser, subframe, 200)
        }

        val reached = browser.waitForNavigation(NavigationStage.LOADED, timeoutMs = 60L)
        assertFalse(reached, "waitForNavigation should ignore subframe load completions")
    }

    @Test
    fun testWaitForNavigationTimeout() = runTest {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val client = KromiumClient(mockRawClient)
        val mockRawBrowser = mockk<CefBrowser>(relaxed = true)
        val browser = KromiumBrowser(client, mockRawBrowser)

        val reached = browser.waitForNavigation(NavigationStage.LOADED, timeoutMs = 40L)
        assertFalse(reached, "waitForNavigation should return false on timeout")
    }

    @Test
    fun testLoadUrlWithWaitUntil() = runTest {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val client = KromiumClient(mockRawClient)
        val loadHandlerSlot = slot<CefLoadHandler>()
        verify(atLeast = 1) { mockRawClient.addLoadHandler(capture(loadHandlerSlot)) }

        val mockRawBrowser = mockk<CefBrowser>(relaxed = true)
        every { mockRawBrowser.identifier } returns 104

        val browser = KromiumBrowser(client, mockRawBrowser)
        val mainFrame = createMockFrame(isMain = true)
        val compositeHandler = loadHandlerSlot.captured

        launch {
            delay(20)
            compositeHandler.onLoadEnd(mockRawBrowser, mainFrame, 200)
        }

        val success = browser.loadUrl("https://example.com/login", waitUntil = NavigationStage.LOADED, timeoutMs = 1000L)
        assertTrue(success, "loadUrl with waitUntil should return true when navigation finishes")
        verify(exactly = 1) { mockRawBrowser.loadURL("https://example.com/login") }
    }
}
