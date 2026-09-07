package dev.daviante.kromium.presentation.network

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
}
