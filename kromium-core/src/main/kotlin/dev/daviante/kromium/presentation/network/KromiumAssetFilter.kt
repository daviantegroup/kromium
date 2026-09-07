package dev.daviante.kromium.presentation.network

import org.cef.network.CefRequest
import java.net.URI

/**
 * Filter configuration for blocking unnecessary network assets (images, media, fonts, stylesheets).
 * Significantly speeds up headless scraping, web automation, and reduces bandwidth usage.
 */
data class KromiumAssetFilter(
    val blockImages: Boolean = false,
    val blockMedia: Boolean = false,
    val blockFonts: Boolean = false,
    val blockStylesheets: Boolean = false
) {
    val isEnabled: Boolean
        get() = blockImages || blockMedia || blockFonts || blockStylesheets

    /**
     * Determines whether a given request should be blocked based on its CEF resource type or URL extension.
     */
    fun shouldBlock(request: CefRequest): Boolean {
        if (!isEnabled) return false

        // 1. Check CefRequest resourceType if available
        val resourceType = try { request.resourceType } catch (_: Throwable) { null }
        if (resourceType != null) {
            when (resourceType) {
                CefRequest.ResourceType.RT_IMAGE,
                CefRequest.ResourceType.RT_FAVICON -> if (blockImages) return true
                CefRequest.ResourceType.RT_MEDIA -> if (blockMedia) return true
                CefRequest.ResourceType.RT_FONT_RESOURCE -> if (blockFonts) return true
                CefRequest.ResourceType.RT_STYLESHEET -> if (blockStylesheets) return true
                else -> { /* Fallback to URL extension check */ }
            }
        }

        // 2. Secondary check: URL file extension
        val url = request.url ?: return false
        return shouldBlockUrl(url)
    }

    /**
     * Determines whether a given URL should be blocked based on known file extensions.
     */
    fun shouldBlockUrl(url: String): Boolean {
        if (!isEnabled) return false
        val cleanPath = try {
            val uri = URI(url)
            uri.path?.lowercase() ?: url.substringBefore('?').substringBefore('#').lowercase()
        } catch (_: Throwable) {
            url.substringBefore('?').substringBefore('#').lowercase()
        }

        if (blockImages && IMAGE_EXTENSIONS.any { cleanPath.endsWith(it) }) return true
        if (blockMedia && MEDIA_EXTENSIONS.any { cleanPath.endsWith(it) }) return true
        if (blockFonts && FONT_EXTENSIONS.any { cleanPath.endsWith(it) }) return true
        if (blockStylesheets && cleanPath.endsWith(".css")) return true

        return false
    }

    companion object {
        val ALL_BLOCKED = KromiumAssetFilter(
            blockImages = true,
            blockMedia = true,
            blockFonts = true,
            blockStylesheets = true
        )

        val MEDIA_ONLY = KromiumAssetFilter(
            blockImages = true,
            blockMedia = true,
            blockFonts = true,
            blockStylesheets = false
        )

        private val IMAGE_EXTENSIONS = listOf(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".bmp", ".tiff", ".avif"
        )
        private val MEDIA_EXTENSIONS = listOf(
            ".mp4", ".webm", ".mkv", ".avi", ".mov", ".mp3", ".ogg", ".wav", ".flac", ".m4a", ".aac"
        )
        private val FONT_EXTENSIONS = listOf(
            ".woff", ".woff2", ".ttf", ".otf", ".eot"
        )

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
