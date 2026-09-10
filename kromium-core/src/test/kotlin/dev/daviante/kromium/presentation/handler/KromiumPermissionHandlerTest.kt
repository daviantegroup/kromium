package dev.daviante.kromium.presentation.handler

import dev.daviante.kromium.presentation.browser.KromiumClient
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.cef.CefClient
import org.cef.callback.CefMediaAccessCallback
import org.cef.handler.CefPermissionHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KromiumPermissionHandlerTest {

    @Test
    fun `KromiumPermissionType bitmask conversions correctly translate flags`() {
        assertEquals(emptySet(), KromiumPermissionType.fromFlags(0))
        assertEquals(setOf(KromiumPermissionType.AUDIO_CAPTURE), KromiumPermissionType.fromFlags(1))
        assertEquals(setOf(KromiumPermissionType.VIDEO_CAPTURE), KromiumPermissionType.fromFlags(2))
        assertEquals(
            setOf(KromiumPermissionType.AUDIO_CAPTURE, KromiumPermissionType.VIDEO_CAPTURE),
            KromiumPermissionType.fromFlags(3)
        )
        assertEquals(setOf(KromiumPermissionType.DESKTOP_AUDIO), KromiumPermissionType.fromFlags(4))
        assertEquals(setOf(KromiumPermissionType.DESKTOP_VIDEO), KromiumPermissionType.fromFlags(8))
        assertEquals(
            setOf(
                KromiumPermissionType.AUDIO_CAPTURE,
                KromiumPermissionType.VIDEO_CAPTURE,
                KromiumPermissionType.DESKTOP_AUDIO,
                KromiumPermissionType.DESKTOP_VIDEO
            ),
            KromiumPermissionType.fromFlags(15)
        )

        assertEquals(0, KromiumPermissionType.toFlags(emptyList()))
        assertEquals(1, KromiumPermissionType.toFlags(listOf(KromiumPermissionType.AUDIO_CAPTURE)))
        assertEquals(2, KromiumPermissionType.toFlags(listOf(KromiumPermissionType.VIDEO_CAPTURE)))
        assertEquals(
            3,
            KromiumPermissionType.toFlags(
                listOf(KromiumPermissionType.AUDIO_CAPTURE, KromiumPermissionType.VIDEO_CAPTURE)
            )
        )
        assertEquals(
            15,
            KromiumPermissionType.toFlags(
                listOf(
                    KromiumPermissionType.AUDIO_CAPTURE,
                    KromiumPermissionType.VIDEO_CAPTURE,
                    KromiumPermissionType.DESKTOP_AUDIO,
                    KromiumPermissionType.DESKTOP_VIDEO
                )
            )
        )
    }

    @Test
    fun `KromiumPermissionRequest parses origins and exposes predicate helpers`() {
        val request = KromiumPermissionRequest.from("https://meet.google.com/abc-xyz", 3)
        assertEquals("https://meet.google.com/abc-xyz", request.url)
        assertEquals("https://meet.google.com", request.origin)
        assertEquals(3, request.rawFlags)
        assertTrue(request.hasAudio())
        assertTrue(request.hasVideo())
        assertFalse(request.hasScreenShare())
        assertFalse(request.hasDesktopAudio())
        assertTrue(request.contains(KromiumPermissionType.AUDIO_CAPTURE))
        assertTrue(request.contains(KromiumPermissionType.VIDEO_CAPTURE))

        val screenShareReq = KromiumPermissionRequest.from("https://zoom.us:8443/j/123", 12)
        assertEquals("https://zoom.us:8443", screenShareReq.origin)
        assertFalse(screenShareReq.hasAudio())
        assertFalse(screenShareReq.hasVideo())
        assertTrue(screenShareReq.hasDesktopAudio())
        assertTrue(screenShareReq.hasScreenShare())

        val customAppReq = KromiumPermissionRequest.from("app://kromium-desktop/media", 1)
        assertEquals("app://kromium-desktop", customAppReq.origin)
    }

    @Test
    fun `KromiumPermissionDecision factories instantiate valid grant and deny models`() {
        assertEquals(KromiumPermissionDecision.Grant(null), KromiumPermissionDecision.GRANT)
        assertEquals(KromiumPermissionDecision.Deny, KromiumPermissionDecision.DENY)
        assertEquals(KromiumPermissionDecision.Deny, KromiumPermissionDecision.deny())

        val selectiveGrant = KromiumPermissionDecision.grant(KromiumPermissionType.AUDIO_CAPTURE)
        assertEquals(setOf(KromiumPermissionType.AUDIO_CAPTURE), selectiveGrant.allowedTypes)
    }

    @Test
    fun `KromiumPermissionHandler presets grantAll and denyAll function properly`() {
        val req = KromiumPermissionRequest.from("https://example.com", 3)

        val grantAll = KromiumPermissionHandler.grantAll()
        assertEquals(KromiumPermissionDecision.GRANT, grantAll.onRequestPermission(req))

        val denyAll = KromiumPermissionHandler.denyAll()
        assertEquals(KromiumPermissionDecision.DENY, denyAll.onRequestPermission(req))
    }

    @Test
    fun `KromiumPermissionHandler forOrigins whitelists trusted domains`() {
        val handler = KromiumPermissionHandler.forOrigins("meet.google.com", "zoom.us")

        val trustedReq = KromiumPermissionRequest.from("https://meet.google.com/call", 3)
        val trustedReq2 = KromiumPermissionRequest.from("https://sub.zoom.us/join", 3)
        val untrustedReq = KromiumPermissionRequest.from("https://malicious-site.com/phish", 3)

        assertEquals(KromiumPermissionDecision.GRANT, handler.onRequestPermission(trustedReq))
        assertEquals(KromiumPermissionDecision.GRANT, handler.onRequestPermission(trustedReq2))
        assertEquals(KromiumPermissionDecision.DENY, handler.onRequestPermission(untrustedReq))
    }

    @Test
    fun `KromiumPermissionHandler forOrigins supports ports and wildcards`() {
        val handler = KromiumPermissionHandler.forOrigins("meet.corp.internal", "*.internal.net", "localhost")

        val portReq = KromiumPermissionRequest.from("https://meet.corp.internal:8443/room", 3)
        val wildcardPortReq = KromiumPermissionRequest.from("https://video.internal.net:9090/session", 3)
        val localhostPortReq = KromiumPermissionRequest.from("http://localhost:3000/app", 3)
        val untrustedPortReq = KromiumPermissionRequest.from("https://evil.corp.internal.fake:8443", 3)

        assertEquals(KromiumPermissionDecision.GRANT, handler.onRequestPermission(portReq))
        assertEquals(KromiumPermissionDecision.GRANT, handler.onRequestPermission(wildcardPortReq))
        assertEquals(KromiumPermissionDecision.GRANT, handler.onRequestPermission(localhostPortReq))
        assertEquals(KromiumPermissionDecision.DENY, handler.onRequestPermission(untrustedPortReq))
    }

    @Test
    fun `KromiumClient defaults to Deny when no permission handler is configured`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val permHandlerSlot = slot<CefPermissionHandler>()

        val client = KromiumClient(mockRawClient)
        verify { mockRawClient.addPermissionHandler(capture(permHandlerSlot)) }

        val handler = permHandlerSlot.captured
        val mockCallback = mockk<CefMediaAccessCallback>(relaxed = true)

        val handled = handler.onRequestMediaAccessPermission(
            null, null, "https://untrusted.com/page", 3, mockCallback
        )

        assertTrue(handled)
        verify(exactly = 1) { mockCallback.Cancel() }
        verify(exactly = 0) { mockCallback.Continue(any()) }
    }

    @Test
    fun `KromiumClient delegates to KromiumPermissionHandler and continues with mask`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val permHandlerSlot = slot<CefPermissionHandler>()

        val client = KromiumClient(mockRawClient)
        verify { mockRawClient.addPermissionHandler(capture(permHandlerSlot)) }

        val handler = permHandlerSlot.captured
        val mockCallback = mockk<CefMediaAccessCallback>(relaxed = true)

        // Selective grant: request both audio (1) and video (2), but only grant audio (1)
        client.permissionHandler = KromiumPermissionHandler { request ->
            if (request.origin == "https://meet.google.com") {
                KromiumPermissionDecision.grant(KromiumPermissionType.AUDIO_CAPTURE)
            } else {
                KromiumPermissionDecision.DENY
            }
        }

        val handled = handler.onRequestMediaAccessPermission(
            null, null, "https://meet.google.com/call", 3, mockCallback
        )

        assertTrue(handled)
        verify(exactly = 1) { mockCallback.Continue(1) } // 3 and 1 = 1
        verify(exactly = 0) { mockCallback.Cancel() }
    }

    @Test
    fun `KromiumClient caches granted permission per session and clears on demand`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val permHandlerSlot = slot<CefPermissionHandler>()

        val client = KromiumClient(mockRawClient)
        verify { mockRawClient.addPermissionHandler(capture(permHandlerSlot)) }

        val handler = permHandlerSlot.captured
        var invocationCount = 0

        client.permissionHandler = KromiumPermissionHandler {
            invocationCount++
            KromiumPermissionDecision.GRANT
        }

        val mockCb1 = mockk<CefMediaAccessCallback>(relaxed = true)
        handler.onRequestMediaAccessPermission(null, null, "https://meet.google.com/room1", 3, mockCb1)
        assertEquals(1, invocationCount)
        verify(exactly = 1) { mockCb1.Continue(3) }

        // Second request from same origin during session: must reuse cache, handler not invoked again
        val mockCb2 = mockk<CefMediaAccessCallback>(relaxed = true)
        handler.onRequestMediaAccessPermission(null, null, "https://meet.google.com/room2", 3, mockCb2)
        assertEquals(1, invocationCount)
        verify(exactly = 1) { mockCb2.Continue(3) }

        // Clear cache and request again: handler must be re-invoked
        client.clearPermissionCache()
        val mockCb3 = mockk<CefMediaAccessCallback>(relaxed = true)
        handler.onRequestMediaAccessPermission(null, null, "https://meet.google.com/room3", 3, mockCb3)
        assertEquals(2, invocationCount)
        verify(exactly = 1) { mockCb3.Continue(3) }
    }

    @Test
    fun `KromiumClient caches negative decision per session`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val permHandlerSlot = slot<CefPermissionHandler>()

        val client = KromiumClient(mockRawClient)
        verify { mockRawClient.addPermissionHandler(capture(permHandlerSlot)) }

        val handler = permHandlerSlot.captured
        var invocationCount = 0

        client.permissionHandler = KromiumPermissionHandler {
            invocationCount++
            KromiumPermissionDecision.DENY
        }

        val mockCb1 = mockk<CefMediaAccessCallback>(relaxed = true)
        handler.onRequestMediaAccessPermission(null, null, "https://suspicious.com/stream", 3, mockCb1)
        assertEquals(1, invocationCount)
        verify(exactly = 1) { mockCb1.Cancel() }

        // Second request from same origin: negative cache hit
        val mockCb2 = mockk<CefMediaAccessCallback>(relaxed = true)
        handler.onRequestMediaAccessPermission(null, null, "https://suspicious.com/other", 3, mockCb2)
        assertEquals(1, invocationCount)
        verify(exactly = 1) { mockCb2.Cancel() }
    }

    @Test
    fun `KromiumClient respects rememberPermissions false setting`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val permHandlerSlot = slot<CefPermissionHandler>()

        val client = KromiumClient(mockRawClient)
        client.rememberPermissions = false
        verify { mockRawClient.addPermissionHandler(capture(permHandlerSlot)) }

        val handler = permHandlerSlot.captured
        var invocationCount = 0

        client.permissionHandler = KromiumPermissionHandler {
            invocationCount++
            KromiumPermissionDecision.GRANT
        }

        val mockCb1 = mockk<CefMediaAccessCallback>(relaxed = true)
        handler.onRequestMediaAccessPermission(null, null, "https://meet.google.com/room1", 3, mockCb1)
        val mockCb2 = mockk<CefMediaAccessCallback>(relaxed = true)
        handler.onRequestMediaAccessPermission(null, null, "https://meet.google.com/room2", 3, mockCb2)

        assertEquals(2, invocationCount)
    }

    @Test
    fun `KromiumClient falls back to legacy onPermissionRequest when permissionHandler is null`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val permHandlerSlot = slot<CefPermissionHandler>()

        val client = KromiumClient(mockRawClient)
        verify { mockRawClient.addPermissionHandler(capture(permHandlerSlot)) }

        val handler = permHandlerSlot.captured
        val mockCb1 = mockk<CefMediaAccessCallback>(relaxed = true)

        client.onPermissionRequest = { url -> url.startsWith("https://trusted.com") }

        handler.onRequestMediaAccessPermission(null, null, "https://trusted.com/camera", 3, mockCb1)
        verify(exactly = 1) { mockCb1.Continue(3) }

        val mockCb2 = mockk<CefMediaAccessCallback>(relaxed = true)
        handler.onRequestMediaAccessPermission(null, null, "https://other.com/camera", 3, mockCb2)
        verify(exactly = 1) { mockCb2.Cancel() }
    }

    @Test
    fun `KromiumClient multiplexes custom CefPermissionHandler registered directly`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val permHandlerSlot = slot<CefPermissionHandler>()

        val client = KromiumClient(mockRawClient)
        verify { mockRawClient.addPermissionHandler(capture(permHandlerSlot)) }

        val compositeHandler = permHandlerSlot.captured
        val customRawHandler = mockk<CefPermissionHandler>(relaxed = true)
        every {
            customRawHandler.onRequestMediaAccessPermission(any(), any(), any(), any(), any())
        } returns true

        client.addPermissionHandler(customRawHandler)

        val mockCallback = mockk<CefMediaAccessCallback>(relaxed = true)
        val handled = compositeHandler.onRequestMediaAccessPermission(
            null, null, "https://test.com", 3, mockCallback
        )

        assertTrue(handled)
        verify(exactly = 1) {
            customRawHandler.onRequestMediaAccessPermission(null, null, "https://test.com", 3, mockCallback)
        }

        // Remove handler and verify it is not called anymore
        client.removePermissionHandler(customRawHandler)
        clearMocks(customRawHandler)

        compositeHandler.onRequestMediaAccessPermission(
            null, null, "https://test.com", 3, mockCallback
        )
        verify(exactly = 0) {
            customRawHandler.onRequestMediaAccessPermission(any(), any(), any(), any(), any())
        }
    }
}
