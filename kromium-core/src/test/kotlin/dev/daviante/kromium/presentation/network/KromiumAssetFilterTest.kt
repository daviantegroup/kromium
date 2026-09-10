package dev.daviante.kromium.presentation.network

import io.mockk.every
import io.mockk.mockk
import org.cef.network.CefRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KromiumAssetFilterTest {

    @Test
    fun testDefaultFilterDoesNotBlock() {
        val filter = KromiumAssetFilter()
        assertFalse(filter.isEnabled)
        assertFalse(filter.shouldBlockUrl("https://example.com/logo.png"))
        assertFalse(filter.shouldBlockUrl("https://example.com/styles.css"))
        assertFalse(filter.shouldBlockUrl("https://example.com/video.mp4"))
        assertFalse(filter.shouldBlockUrl("https://example.com/font.woff2"))
    }

    @Test
    fun testBlockImagesOnly() {
        val filter = KromiumAssetFilter(blockImages = true)
        assertTrue(filter.isEnabled)
        assertTrue(filter.shouldBlockUrl("https://example.com/images/avatar.png"))
        assertTrue(filter.shouldBlockUrl("https://example.com/banner.jpg?query=1"))
        assertTrue(filter.shouldBlockUrl("https://example.com/icon.svg#hash"))
        assertTrue(filter.shouldBlockUrl("https://example.com/favicon.ico"))
        assertTrue(filter.shouldBlockUrl("https://example.com/photo.webp"))
        assertTrue(filter.shouldBlockUrl("https://example.com/photo.avif"))

        // Should not block styles, scripts, html, media
        assertFalse(filter.shouldBlockUrl("https://example.com/app.css"))
        assertFalse(filter.shouldBlockUrl("https://example.com/script.js"))
        assertFalse(filter.shouldBlockUrl("https://example.com/index.html"))
        assertFalse(filter.shouldBlockUrl("https://example.com/song.mp3"))
    }

    @Test
    fun testBlockMediaOnly() {
        val filter = KromiumAssetFilter(blockMedia = true)
        assertTrue(filter.isEnabled)
        assertTrue(filter.shouldBlockUrl("https://example.com/video.mp4"))
        assertTrue(filter.shouldBlockUrl("https://example.com/audio.mp3"))
        assertTrue(filter.shouldBlockUrl("https://example.com/movie.webm"))
        assertTrue(filter.shouldBlockUrl("https://example.com/track.ogg"))

        assertFalse(filter.shouldBlockUrl("https://example.com/image.png"))
        assertFalse(filter.shouldBlockUrl("https://example.com/style.css"))
    }

    @Test
    fun testBlockFontsOnly() {
        val filter = KromiumAssetFilter(blockFonts = true)
        assertTrue(filter.isEnabled)
        assertTrue(filter.shouldBlockUrl("https://example.com/fonts/inter.woff2"))
        assertTrue(filter.shouldBlockUrl("https://example.com/fonts/roboto.ttf"))
        assertTrue(filter.shouldBlockUrl("https://example.com/fonts/custom.otf"))

        assertFalse(filter.shouldBlockUrl("https://example.com/image.png"))
    }

    @Test
    fun testBlockStylesheets() {
        val filter = KromiumAssetFilter(blockStylesheets = true)
        assertTrue(filter.isEnabled)
        assertTrue(filter.shouldBlockUrl("https://example.com/css/main.css"))
        assertTrue(filter.shouldBlockUrl("https://example.com/css/theme.css?v=2"))

        assertFalse(filter.shouldBlockUrl("https://example.com/image.png"))
        assertFalse(filter.shouldBlockUrl("https://example.com/index.html"))
    }

    @Test
    fun testAggressiveHeadlessPreset() {
        val filter = KromiumAssetFilter.AGGRESSIVE_HEADLESS
        assertTrue(filter.shouldBlockUrl("https://example.com/image.png"))
        assertTrue(filter.shouldBlockUrl("https://example.com/clip.mp4"))
        assertTrue(filter.shouldBlockUrl("https://example.com/font.woff2"))
        assertTrue(filter.shouldBlockUrl("https://example.com/styles.css"))

        // HTML and JS remain unblocked
        assertFalse(filter.shouldBlockUrl("https://example.com/page.html"))
        assertFalse(filter.shouldBlockUrl("https://example.com/app.js"))

        // ALL_BLOCKED must be identical to AGGRESSIVE_HEADLESS
        assertEquals(KromiumAssetFilter.AGGRESSIVE_HEADLESS, KromiumAssetFilter.ALL_BLOCKED)
    }

    @Test
    fun testAllBlockedPreset() {
        val filter = KromiumAssetFilter.ALL_BLOCKED
        assertTrue(filter.shouldBlockUrl("https://example.com/image.png"))
        assertTrue(filter.shouldBlockUrl("https://example.com/clip.mp4"))
        assertTrue(filter.shouldBlockUrl("https://example.com/font.woff2"))
        assertTrue(filter.shouldBlockUrl("https://example.com/styles.css"))

        // HTML and JS remain unblocked
        assertFalse(filter.shouldBlockUrl("https://example.com/page.html"))
        assertFalse(filter.shouldBlockUrl("https://example.com/app.js"))
    }

    @Test
    fun testMediaOnlyPreset() {
        val filter = KromiumAssetFilter.MEDIA_ONLY
        assertTrue(filter.shouldBlockUrl("https://example.com/image.png"))
        assertTrue(filter.shouldBlockUrl("https://example.com/clip.mp4"))
        assertTrue(filter.shouldBlockUrl("https://example.com/font.woff2"))

        // Stylesheets are preserved in MEDIA_ONLY
        assertFalse(filter.shouldBlockUrl("https://example.com/styles.css"))
        assertFalse(filter.shouldBlockUrl("https://example.com/app.js"))
    }

    @Test
    fun testIsHostAllowedExactAndSubdomain() {
        val allowed = setOf("example.com", "api.github.com")

        // Exact matches
        assertTrue(KromiumAssetFilter.isHostAllowed("https://example.com/index.html", allowed))
        assertTrue(KromiumAssetFilter.isHostAllowed("https://api.github.com/users", allowed))

        // Subdomain of allowed parent domain
        assertTrue(KromiumAssetFilter.isHostAllowed("https://sub.example.com/test", allowed))
        assertTrue(KromiumAssetFilter.isHostAllowed("https://deep.nested.sub.example.com/test", allowed))

        // Negative matches: completely different domain or attacker domain suffix spoofing
        assertFalse(KromiumAssetFilter.isHostAllowed("https://google.com/", allowed))
        assertFalse(KromiumAssetFilter.isHostAllowed("https://fakeexample.com/", allowed))
        assertFalse(KromiumAssetFilter.isHostAllowed("https://notexample.com/", allowed))
        assertFalse(KromiumAssetFilter.isHostAllowed("https://example.com.evil.com/", allowed))
    }

    @Test
    fun testIsHostAllowedWildcard() {
        val allowed = setOf("*.myservice.io")

        assertTrue(KromiumAssetFilter.isHostAllowed("https://myservice.io/", allowed))
        assertTrue(KromiumAssetFilter.isHostAllowed("https://app.myservice.io/dashboard", allowed))
        assertTrue(KromiumAssetFilter.isHostAllowed("https://auth.myservice.io/login", allowed))

        assertFalse(KromiumAssetFilter.isHostAllowed("https://other.io/", allowed))
    }

    @Test
    fun testEmptyAllowedHostsAllowsAll() {
        assertTrue(KromiumAssetFilter.isHostAllowed("https://anything.com/", emptySet()))
    }

    @Test
    fun testPseudoSchemesAlwaysAllowed() {
        val allowed = setOf("example.com")
        assertTrue(KromiumAssetFilter.isHostAllowed("about:blank", allowed))
        assertTrue(KromiumAssetFilter.isHostAllowed("data:text/html,<h1>test</h1>", allowed))
        assertTrue(KromiumAssetFilter.isHostAllowed("chrome://version", allowed))
        assertTrue(KromiumAssetFilter.isHostAllowed("file:///local/path/doc.html", allowed))
    }

    @Test
    fun testHostWithPortAndSpaces() {
        val allowed = setOf("example.com")
        // With port
        assertTrue(KromiumAssetFilter.isHostAllowed("http://example.com:8080/path", allowed))
        // With unencoded path with space
        assertTrue(KromiumAssetFilter.isHostAllowed("http://example.com/some path", allowed))
        // Non-allowed with space and port
        assertFalse(KromiumAssetFilter.isHostAllowed("http://evil.com:8080/some path", allowed))
    }

    @Test
    fun testExtractHost() {
        assertEquals("example.com", KromiumAssetFilter.extractHost("https://example.com/path"))
        assertEquals("sub.example.com", KromiumAssetFilter.extractHost("http://sub.example.com:8080/api?a=1"))
        assertEquals("ws.host.io", KromiumAssetFilter.extractHost("wss://ws.host.io:443/ws"))
        assertEquals(null, KromiumAssetFilter.extractHost("about:blank"))
        assertEquals(null, KromiumAssetFilter.extractHost("data:text/plain,hello"))
    }

    @Test
    fun testCustomBlockedExtensions() {
        val filter = KromiumAssetFilter(customBlockedExtensions = setOf(".wasm", "pdf"))
        assertTrue(filter.isEnabled)
        assertTrue(filter.shouldBlockUrl("https://example.com/engine.wasm"))
        assertTrue(filter.shouldBlockUrl("https://example.com/docs/manual.pdf"))
        assertTrue(filter.shouldBlockUrl("https://example.com/engine.wasm?v=1#hash"))

        assertFalse(filter.shouldBlockUrl("https://example.com/index.html"))
        assertFalse(filter.shouldBlockUrl("https://example.com/styles.css"))
        assertFalse(filter.shouldBlockUrl("https://example.com/logo.png"))
    }

    @Test
    fun testCustomBlockedUrlPatterns() {
        val filter = KromiumAssetFilter(customBlockedUrlPatterns = setOf("analytics", "/ads/", "tracker.js"))
        assertTrue(filter.isEnabled)
        assertTrue(filter.shouldBlockUrl("https://google-analytics.com/collect"))
        assertTrue(filter.shouldBlockUrl("https://example.com/ads/banner.html"))
        assertTrue(filter.shouldBlockUrl("https://cdn.example.com/tracker.js"))

        assertFalse(filter.shouldBlockUrl("https://example.com/home"))
        assertFalse(filter.shouldBlockUrl("https://example.com/products/view"))
    }

    @Test
    fun testCustomBlockedResourceTypes() {
        val filter = KromiumAssetFilter(
            customBlockedResourceTypes = setOf(CefRequest.ResourceType.RT_PING, CefRequest.ResourceType.RT_CSP_REPORT)
        )
        assertTrue(filter.isEnabled)

        val pingReq = mockk<CefRequest>(relaxed = true)
        every { pingReq.resourceType } returns CefRequest.ResourceType.RT_PING
        every { pingReq.url } returns "https://example.com/beacon"
        assertTrue(filter.shouldBlock(pingReq))

        val xhrReq = mockk<CefRequest>(relaxed = true)
        every { xhrReq.resourceType } returns CefRequest.ResourceType.RT_XHR
        every { xhrReq.url } returns "https://example.com/api/data"
        assertFalse(filter.shouldBlock(xhrReq))
    }

    @Test
    fun testCustomFilterPredicate() {
        val filter = KromiumAssetFilter(
            customFilter = { req -> req.url?.contains("blockme") == true }
        )
        assertTrue(filter.isEnabled)

        val blockedReq = mockk<CefRequest>(relaxed = true)
        every { blockedReq.url } returns "https://example.com/path?blockme=true"
        assertTrue(filter.shouldBlock(blockedReq))

        val allowedReq = mockk<CefRequest>(relaxed = true)
        every { allowedReq.url } returns "https://example.com/path?allow=true"
        assertFalse(filter.shouldBlock(allowedReq))
    }

    @Test
    fun testAllowOnlyExtensions() {
        val filter = KromiumAssetFilter.allowOnly(
            extensions = setOf("js", ".css")
        )
        assertEquals(AssetFilterMode.ALLOWLIST, filter.mode)
        assertTrue(filter.isEnabled)

        // Allowed extensions
        assertFalse(filter.shouldBlockUrl("https://example.com/bundle.js"))
        assertFalse(filter.shouldBlockUrl("https://example.com/styles.css"))
        assertFalse(filter.shouldBlockUrl("https://example.com/styles.css?v=2"))

        // Everything else blocked by default
        assertTrue(filter.shouldBlockUrl("https://example.com/image.png"))
        assertTrue(filter.shouldBlockUrl("https://example.com/font.woff2"))
        assertTrue(filter.shouldBlockUrl("https://example.com/video.mp4"))
    }

    @Test
    fun testAllowOnlyUrlPatterns() {
        val filter = KromiumAssetFilter.allowOnly(
            urlPatterns = setOf("mycdn.com", "/api/")
        )
        assertTrue(filter.isEnabled)

        // Matching patterns allowed
        assertFalse(filter.shouldBlockUrl("https://mycdn.com/any/resource.png"))
        assertFalse(filter.shouldBlockUrl("https://example.com/api/v1/user"))

        // Non-matching blocked
        assertTrue(filter.shouldBlockUrl("https://evil.com/tracker.js"))
        assertTrue(filter.shouldBlockUrl("https://example.com/images/hero.jpg"))
    }

    @Test
    fun testAllowOnlyResourceTypes() {
        val filter = KromiumAssetFilter.allowOnly(
            resourceTypes = setOf(CefRequest.ResourceType.RT_SCRIPT, CefRequest.ResourceType.RT_STYLESHEET)
        )
        assertTrue(filter.isEnabled)

        val scriptReq = mockk<CefRequest>(relaxed = true)
        every { scriptReq.resourceType } returns CefRequest.ResourceType.RT_SCRIPT
        every { scriptReq.url } returns "https://example.com/app.js"
        assertFalse(filter.shouldBlock(scriptReq))

        val imgReq = mockk<CefRequest>(relaxed = true)
        every { imgReq.resourceType } returns CefRequest.ResourceType.RT_IMAGE
        every { imgReq.url } returns "https://example.com/pic.png"
        assertTrue(filter.shouldBlock(imgReq))
    }

    @Test
    fun testAllowOnlyMainFrameNavigation() {
        // By default allowMainFrame is true so the top-level document navigates
        val filterDefault = KromiumAssetFilter.allowOnly(extensions = setOf("js"))
        val mainReq = mockk<CefRequest>(relaxed = true)
        every { mainReq.resourceType } returns CefRequest.ResourceType.RT_MAIN_FRAME
        every { mainReq.url } returns "https://example.com/"
        assertFalse(filterDefault.shouldBlock(mainReq))

        // When allowMainFrame is false, top-level document is blocked unless matching allow rules
        val filterStrict = KromiumAssetFilter.allowOnly(extensions = setOf("js"), allowMainFrame = false)
        assertTrue(filterStrict.shouldBlock(mainReq))
    }

    @Test
    fun testAllowOnlyCustomFilter() {
        val filter = KromiumAssetFilter.allowOnly(
            filter = { req -> req.url?.contains("/trusted/") == true }
        )
        val trustedReq = mockk<CefRequest>(relaxed = true)
        every { trustedReq.resourceType } returns CefRequest.ResourceType.RT_SUB_RESOURCE
        every { trustedReq.url } returns "https://example.com/trusted/data"
        assertFalse(filter.shouldBlock(trustedReq))

        val blockedReq = mockk<CefRequest>(relaxed = true)
        every { blockedReq.resourceType } returns CefRequest.ResourceType.RT_SUB_RESOURCE
        every { blockedReq.url } returns "https://example.com/blocked/data"
        assertTrue(filter.shouldBlock(blockedReq))
    }

    @Test
    fun testBlocklistWithAllowlistBypass() {
        // Block all images EXCEPT a specific captcha image
        val filter = KromiumAssetFilter(
            blockImages = true,
            allowedUrlPatterns = setOf("captcha.png", "logo.svg")
        )
        assertEquals(AssetFilterMode.BLOCKLIST, filter.mode)
        assertTrue(filter.isEnabled)

        // Normal images are blocked
        assertTrue(filter.shouldBlockUrl("https://example.com/hero.jpg"))
        assertTrue(filter.shouldBlockUrl("https://example.com/photo.png"))

        // Allowed patterns bypass the image block rule
        assertFalse(filter.shouldBlockUrl("https://example.com/captcha.png"))
        assertFalse(filter.shouldBlockUrl("https://example.com/images/logo.svg"))
    }

    @Test
    fun testPredefinedExtensionSets() {
        assertTrue(KromiumAssetFilter.IMAGE_EXTENSIONS.contains(".png"))
        assertTrue(KromiumAssetFilter.IMAGE_EXTENSIONS.contains(".webp"))
        assertTrue(KromiumAssetFilter.MEDIA_EXTENSIONS.contains(".mp4"))
        assertTrue(KromiumAssetFilter.FONT_EXTENSIONS.contains(".woff2"))
        assertTrue(KromiumAssetFilter.STYLESHEET_EXTENSIONS.contains(".css"))
        assertTrue(KromiumAssetFilter.SCRIPT_EXTENSIONS.contains(".js"))
    }
}
