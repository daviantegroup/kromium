package dev.daviante.kromium.presentation.scheme

import java.net.URLConnection
import java.util.HashMap
import java.util.Locale

/**
 * Fast, zero-dependency MIME type resolver for modern web and application assets.
 */
object MimeTypes {
    private const val DEFAULT_MIME_TYPE = "application/octet-stream"

    private val MIME_MAP: Map<String, String> = HashMap<String, String>().apply {
        // Web Core
        put("html", "text/html; charset=utf-8")
        put("htm", "text/html; charset=utf-8")
        put("css", "text/css; charset=utf-8")
        put("js", "application/javascript; charset=utf-8")
        put("mjs", "application/javascript; charset=utf-8")
        put("wasm", "application/wasm")
        put("json", "application/json; charset=utf-8")
        put("map", "application/json; charset=utf-8")

        // Text & Data
        put("txt", "text/plain; charset=utf-8")
        put("csv", "text/csv; charset=utf-8")
        put("xml", "application/xml; charset=utf-8")
        put("md", "text/markdown; charset=utf-8")
        put("yaml", "application/yaml; charset=utf-8")
        put("yml", "application/yaml; charset=utf-8")

        // Images
        put("svg", "image/svg+xml")
        put("png", "image/png")
        put("jpg", "image/jpeg")
        put("jpeg", "image/jpeg")
        put("gif", "image/gif")
        put("webp", "image/webp")
        put("avif", "image/avif")
        put("ico", "image/x-icon")
        put("bmp", "image/bmp")
        put("tiff", "image/tiff")
        put("tif", "image/tiff")

        // Fonts
        put("woff", "font/woff")
        put("woff2", "font/woff2")
        put("ttf", "font/ttf")
        put("otf", "font/otf")
        put("eot", "application/vnd.ms-fontobject")

        // Audio
        put("mp3", "audio/mpeg")
        put("wav", "audio/wav")
        put("ogg", "audio/ogg")
        put("flac", "audio/flac")
        put("aac", "audio/aac")
        put("m4a", "audio/mp4")

        // Video
        put("mp4", "video/mp4")
        put("webm", "video/webm")
        put("ogv", "video/ogg")
        put("mov", "video/quicktime")

        // Documents & Archives
        put("pdf", "application/pdf")
        put("zip", "application/zip")
        put("gz", "application/gzip")
        put("tar", "application/x-tar")
    }

    /**
     * Resolves the MIME type for a given filename or file path.
     *
     * @param path Filename or relative/absolute path.
     * @return Resolved MIME content type string.
     */
    @JvmStatic
    fun lookup(path: String): String {
        val extension = getExtension(path)
        if (extension.isNotEmpty()) {
            val mime = MIME_MAP[extension.lowercase(Locale.ROOT)]
            if (mime != null) return mime
        }

        return URLConnection.guessContentTypeFromName(path) ?: DEFAULT_MIME_TYPE
    }

    /**
     * Checks if a filename or path has a known static file extension.
     */
    @JvmStatic
    fun hasExtension(path: String): Boolean = getExtension(path).isNotEmpty()

    /**
     * Extracts the file extension (without the dot) from a path string.
     */
    @JvmStatic
    fun getExtension(path: String): String {
        val cleanPath = path.substringBefore('?').substringBefore('#')
        val lastSlash = cleanPath.lastIndexOf('/')
        val filename = if (lastSlash >= 0) cleanPath.substring(lastSlash + 1) else cleanPath
        val lastDot = filename.lastIndexOf('.')
        return if (lastDot > 0 && lastDot < filename.length - 1) {
            filename.substring(lastDot + 1)
        } else {
            ""
        }
    }
}
