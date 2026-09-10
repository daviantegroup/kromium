package dev.daviante.kromium.presentation.browser

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandler
import org.cef.network.CefRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostLockFrameAwareTest {

    private fun setupClientWithRequestHandler(): Pair<KromiumClient, CefRequestHandler> {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val requestHandlerSlot = slot<CefRequestHandler>()

        val client = KromiumClient(mockRawClient)
        io.mockk.verify { mockRawClient.addRequestHandler(capture(requestHandlerSlot)) }
        return Pair(client, requestHandlerSlot.captured)
    }

    private fun createMockRequest(url: String): CefRequest {
        val req = mockk<CefRequest>(relaxed = true)
        every { req.url } returns url
        return req
    }

    private fun createMockFrame(isMain: Boolean): CefFrame {
        val frame = mockk<CefFrame>(relaxed = true)
        every { frame.isMain } returns isMain
        return frame
    }

    @Test
    fun testMainFrameBlockedOnDisallowedHost() {
        val (client, handler) = setupClientWithRequestHandler()
        client.hostLock = setOf("example.com")
        client.hostLockSubframes = false

        val mainFrame = createMockFrame(isMain = true)
        val request = createMockRequest("https://unauthorized.com/login")

        val blocked = handler.onBeforeBrowse(null, mainFrame, request, false, false)
        assertTrue(blocked, "Main frame navigation to unauthorized host must be blocked")
    }

    @Test
    fun testMainFrameAllowedOnAllowedHost() {
        val (client, handler) = setupClientWithRequestHandler()
        client.hostLock = setOf("example.com")
        client.hostLockSubframes = false

        val mainFrame = createMockFrame(isMain = true)
        val request = createMockRequest("https://example.com/dashboard")

        val blocked = handler.onBeforeBrowse(null, mainFrame, request, false, false)
        assertFalse(blocked, "Main frame navigation to allowed host must NOT be blocked")
    }

    @Test
    fun testSubframeAllowedWhenHostLockSubframesIsFalse() {
        val (client, handler) = setupClientWithRequestHandler()
        client.hostLock = setOf("example.com")
        client.hostLockSubframes = false // Default

        val subframe = createMockFrame(isMain = false)
        // E.g., Cloudflare Turnstile or Google reCAPTCHA widget in an iframe
        val request = createMockRequest("https://challenges.cloudflare.com/turnstile/v0/api.js")

        val blocked = handler.onBeforeBrowse(null, subframe, request, false, false)
        assertFalse(blocked, "Subframe navigation to third-party host must be allowed by default for verification widgets")
    }

    @Test
    fun testSubframeBlockedWhenHostLockSubframesIsTrue() {
        val (client, handler) = setupClientWithRequestHandler()
        client.hostLock = setOf("example.com")
        client.hostLockSubframes = true // Explicitly locking subframes

        val subframe = createMockFrame(isMain = false)
        val request = createMockRequest("https://challenges.cloudflare.com/turnstile/v0/api.js")

        val blocked = handler.onBeforeBrowse(null, subframe, request, false, false)
        assertTrue(blocked, "Subframe navigation to unauthorized host must be blocked when hostLockSubframes is true")
    }

    @Test
    fun testSubframeAllowedWhenHostLockSubframesIsTrueAndHostAllowed() {
        val (client, handler) = setupClientWithRequestHandler()
        client.hostLock = setOf("example.com", "challenges.cloudflare.com")
        client.hostLockSubframes = true

        val subframe = createMockFrame(isMain = false)
        val request = createMockRequest("https://challenges.cloudflare.com/turnstile/v0/api.js")

        val blocked = handler.onBeforeBrowse(null, subframe, request, false, false)
        assertFalse(blocked, "Subframe navigation to allowed host must be allowed even when hostLockSubframes is true")
    }
}
