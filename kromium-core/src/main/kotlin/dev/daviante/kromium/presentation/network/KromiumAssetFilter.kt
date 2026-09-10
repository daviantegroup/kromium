package dev.daviante.kromium.presentation.network

import org.cef.network.CefRequest
import java.net.URI

/**
 * Operating mode for [KromiumAssetFilter].
 */
enum class AssetFilterMode {
    /**
     * Default allow: requests proceed unless matched by a blocking rule.
     * Any configured allow rules act as bypass exceptions that unblock specific requests.
     */
    BLOCKLIST,

    /**
     * Default block: all requests are blocked unless explicitly permitted by an allow rule.
     */
    ALLOWLIST
}

/**
 * Filter configuration for blocking or allowlisting network assets (images, media, fonts, stylesheets,
 * custom extensions, URL patterns, resource types, and dynamic predicates).
 * Significantly speeds up headless automation, content extraction, and reduces bandwidth usage.
 */
data class KromiumAssetFilter(
    val blockImages: Boolean = false,
    val blockMedia: Boolean = false,
    val blockFonts: Boolean = false,
    val blockStylesheets: Boolean = false,
    val customBlockedExtensions: Set<String> = emptySet(),
    val customBlockedUrlPatterns: Set<String> = emptySet(),
    val customBlockedResourceTypes: Set<CefRequest.ResourceType> = emptySet(),
    val customFilter: ((request: CefRequest) -> Boolean)? = null,
    val allowedExtensions: Set<String> = emptySet(),
    val allowedUrlPatterns: Set<String> = emptySet(),
    val allowedResourceTypes: Set<CefRequest.ResourceType> = emptySet(),
    val customAllowFilter: ((request: CefRequest) -> Boolean)? = null,
    val allowMainFrame: Boolean = true,
    val mode: AssetFilterMode = AssetFilterMode.BLOCKLIST
) {
    val isEnabled: Boolean
        get() = mode == AssetFilterMode.ALLOWLIST ||
                blockImages || blockMedia || blockFonts || blockStylesheets ||
                customBlockedExtensions.isNotEmpty() ||
                customBlockedUrlPatterns.isNotEmpty() ||
                customBlockedResourceTypes.isNotEmpty() ||
                customFilter != null ||
                allowedExtensions.isNotEmpty() ||
                allowedUrlPatterns.isNotEmpty() ||
                allowedResourceTypes.isNotEmpty() ||
                customAllowFilter != null

    private fun extractCleanPath(url: String): String =
        try {
            val uri = URI(url)
            uri.path?.lowercase() ?: url.substringBefore('?').substringBefore('#').lowercase()
        } catch (_: Throwable) {
            url.substringBefore('?').substringBefore('#').lowercase()
        }

    private fun matchesAllowRules(
        request: CefRequest?,
        url: String,
        cleanPath: String,
        resourceType: CefRequest.ResourceType?
    ): Boolean {
        if (allowMainFrame && resourceType == CefRequest.ResourceType.RT_MAIN_FRAME) return true
        if (request != null && customAllowFilter?.invoke(request) == true) return true
        if (resourceType != null && allowedResourceTypes.contains(resourceType)) return true
        if (allowedUrlPatterns.any { url.contains(it, ignoreCase = true) }) return true
        if (allowedExtensions.any { ext ->
            val normalized = if (ext.startsWith(".")) ext.lowercase() else ".$ext".lowercase()
            cleanPath.endsWith(normalized)
        }) return true
        return false
    }

    private fun matchesBlockRules(
        request: CefRequest?,
        url: String,
        cleanPath: String,
        resourceType: CefRequest.ResourceType?
    ): Boolean {
        if (request != null && customFilter?.invoke(request) == true) return true

        if (resourceType != null) {
            if (customBlockedResourceTypes.contains(resourceType)) return true
            when (resourceType) {
                CefRequest.ResourceType.RT_IMAGE,
                CefRequest.ResourceType.RT_FAVICON -> if (blockImages) return true
                CefRequest.ResourceType.RT_MEDIA -> if (blockMedia) return true
                CefRequest.ResourceType.RT_FONT_RESOURCE -> if (blockFonts) return true
                CefRequest.ResourceType.RT_STYLESHEET -> if (blockStylesheets) return true
                else -> { /* Fallback to URL extension check */ }
            }
        }

        if (customBlockedUrlPatterns.any { url.contains(it, ignoreCase = true) }) return true

        if (customBlockedExtensions.any { ext ->
            val normalized = if (ext.startsWith(".")) ext.lowercase() else ".$ext".lowercase()
            cleanPath.endsWith(normalized)
        }) return true

        if (blockImages && IMAGE_EXTENSIONS.any { cleanPath.endsWith(it) }) return true
        if (blockMedia && MEDIA_EXTENSIONS.any { cleanPath.endsWith(it) }) return true
        if (blockFonts && FONT_EXTENSIONS.any { cleanPath.endsWith(it) }) return true
        if (blockStylesheets && cleanPath.endsWith(".css")) return true

        return false
    }

    /**
     * Determines whether a given request should be blocked based on configured filter rules and mode.
     */
    fun shouldBlock(request: CefRequest): Boolean {
        if (!isEnabled) return false

        val url = request.url ?: return false
        val cleanPath = extractCleanPath(url)
        val resourceType = try { request.resourceType } catch (_: Throwable) { null }

        return when (mode) {
            AssetFilterMode.ALLOWLIST -> {
                !matchesAllowRules(request, url, cleanPath, resourceType)
            }
            AssetFilterMode.BLOCKLIST -> {
                if (matchesAllowRules(request, url, cleanPath, resourceType)) {
                    false
                } else {
                    matchesBlockRules(request, url, cleanPath, resourceType)
                }
            }
        }
    }

    /**
     * Determines whether a given URL should be blocked based on configured filter rules and mode.
     */
    fun shouldBlockUrl(url: String): Boolean {
        if (!isEnabled) return false
        val cleanPath = extractCleanPath(url)

        return when (mode) {
            AssetFilterMode.ALLOWLIST -> {
                !matchesAllowRules(null, url, cleanPath, null)
            }
            AssetFilterMode.BLOCKLIST -> {
                if (matchesAllowRules(null, url, cleanPath, null)) {
                    false
                } else {
                    matchesBlockRules(null, url, cleanPath, null)
                }
            }
        }
    }

    companion object {
        /**
         * Aggressive blocking preset for pure raw text and HTML extraction.
         * Blocks images, media, fonts, **and stylesheets (`.css`)**.
         *
         * **CAUTION**: Disabling CSS will break many Single Page Applications (SPAs),
         * interactive verification challenges (e.g. Cloudflare Turnstile, Google reCAPTCHA), and scripts that query
         * CSS-dependent element dimensions or computed styles.
         */
        @JvmField
        val AGGRESSIVE_HEADLESS = KromiumAssetFilter(
            blockImages = true,
            blockMedia = true,
            blockFonts = true,
            blockStylesheets = true
        )

        /**
         * @deprecated Renamed to [AGGRESSIVE_HEADLESS] to clearly reflect that stylesheets are blocked.
         */
        @JvmField
        val ALL_BLOCKED = AGGRESSIVE_HEADLESS

        /**
         * Recommended preset for automated content extraction and headless automation.
         * Blocks images, video, audio, and web fonts to save bandwidth and boost speed,
         * while **preserving stylesheets (`.css`)**.
         *
         * Preserving CSS is critical for modern SPAs, Cloudflare Turnstile, reCAPTCHA,
         * and UI visibility calculations (e.g. `offsetWidth`, `display: none` detection).
         */
        @JvmField
        val MEDIA_ONLY = KromiumAssetFilter(
            blockImages = true,
            blockMedia = true,
            blockFonts = true,
            blockStylesheets = false
        )

        /**
         * Creates a strict allowlist filter that blocks ALL network assets by default,
         * permitting ONLY those that match the specified allowed extensions, URL patterns,
         * resource types, or filter predicate.
         *
         * @param extensions File extensions permitted to load (e.g. `setOf("js", "css")` or `setOf(".png")`).
         * @param urlPatterns URL patterns or domain substrings permitted to load.
         * @param resourceTypes CEF resource types permitted to load.
         * @param allowMainFrame Whether to permit top-level document navigation (defaults to true).
         * @param filter Programmatic predicate returning true to allow a request.
         */
        @JvmStatic
        @JvmOverloads
        fun allowOnly(
            extensions: Set<String> = emptySet(),
            urlPatterns: Set<String> = emptySet(),
            resourceTypes: Set<CefRequest.ResourceType> = emptySet(),
            allowMainFrame: Boolean = true,
            filter: ((request: CefRequest) -> Boolean)? = null
        ): KromiumAssetFilter = KromiumAssetFilter(
            mode = AssetFilterMode.ALLOWLIST,
            allowedExtensions = extensions,
            allowedUrlPatterns = urlPatterns,
            allowedResourceTypes = resourceTypes,
            allowMainFrame = allowMainFrame,
            customAllowFilter = filter
        )

        @JvmField
        val IMAGE_EXTENSIONS: Set<String> = setOf(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".bmp", ".tiff", ".avif"
        )

        @JvmField
        val MEDIA_EXTENSIONS: Set<String> = setOf(
            ".mp4", ".webm", ".mkv", ".avi", ".mov", ".mp3", ".ogg", ".wav", ".flac", ".m4a", ".aac"
        )

        @JvmField
        val FONT_EXTENSIONS: Set<String> = setOf(
            ".woff", ".woff2", ".ttf", ".otf", ".eot"
        )

        @JvmField
        val STYLESHEET_EXTENSIONS: Set<String> = setOf(".css")

        @JvmField
        val SCRIPT_EXTENSIONS: Set<String> = setOf(".js", ".mjs")

        /**
         * Safely extracts the host component from a URL without throwing on non-standard URIs.
         */
        fun extractHost(url: String): String? {
            val trimmed = url.trim()
            try {
                val u = URI(trimmed)
                val h = u.host
                if (!h.isNullOrBlank()) return h.lowercase()
            } catch (_: Throwable) {}

            val afterScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
                trimmed.startsWith("ws://", ignoreCase = true) -> trimmed.substring(5)
                trimmed.startsWith("wss://", ignoreCase = true) -> trimmed.substring(6)
                else -> return null
            }
            val hostPort = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            val host = hostPort.substringBefore(':').trim().lowercase()
            return host.ifBlank { null }
        }

        /**
         * Helper to verify whether a URL's host matches the allowed host set (including subdomains).
         * Internal browser schemes ("about:", "data:", "chrome:", "blob:", "file:") are permitted.
         * Network requests ("http:", "https:", "ws:", "wss:") must match an allowed host entry.
         */
        fun isHostAllowed(url: String, allowedHosts: Set<String>): Boolean {
            if (allowedHosts.isEmpty()) return true
            val trimmed = url.trim()
            if (trimmed.startsWith("about:", ignoreCase = true) ||
                trimmed.startsWith("data:", ignoreCase = true) ||
                trimmed.startsWith("chrome:", ignoreCase = true) ||
                trimmed.startsWith("blob:", ignoreCase = true) ||
                trimmed.startsWith("file:", ignoreCase = true)) {
                return true
            }

            val lowerHost = extractHost(trimmed) ?: return false
            return allowedHosts.any { allowed ->
                val clean = allowed.trim().lowercase().removePrefix("*.")
                lowerHost == clean || lowerHost.endsWith(".$clean")
            }
        }
    }
}
