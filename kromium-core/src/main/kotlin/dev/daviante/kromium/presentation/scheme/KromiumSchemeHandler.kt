package dev.daviante.kromium.presentation.scheme

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Factory utilities for creating turnkey [KromiumAssetHandler] instances to serve web assets
 * from the JVM classpath or local directories via custom protocols.
 */
object KromiumSchemeHandler {

    /**
     * Creates an asset handler that streams resources bundled inside the application JAR or classpath.
     *
     * Example:
     * ```kotlin
     * val handler = KromiumSchemeHandler.fromClasspath(
     *     resourcePath = "web",
     *     spaFallback = "index.html"
     * )
     * ```
     *
     * @param resourcePath Base path within the classpath (e.g. "web" or "assets/frontend").
     * @param classLoader The [ClassLoader] used to locate resources. Defaults to this class's loader.
     * @param spaFallback Optional fallback file (e.g. "index.html") served on extensionless GET routes for SPAs.
     * @param defaultHeaders Optional default headers appended to every response (e.g. CORS).
     */
    @JvmStatic
    @JvmOverloads
    fun fromClasspath(
        resourcePath: String = "",
        classLoader: ClassLoader = KromiumSchemeHandler::class.java.classLoader,
        spaFallback: String? = null,
        defaultHeaders: Map<String, String> = emptyMap()
    ): KromiumAssetHandler {
        val normalizedBase = resourcePath.trim().trim('/', '\\')

        return KromiumAssetHandler { request ->
            val cleanPath = sanitizePath(request.path)
            if (cleanPath == null) {
                return@KromiumAssetHandler KromiumAssetResponse.forbidden("Invalid or unsafe path")
            }

            val targetRelPath = if (cleanPath.isEmpty() || cleanPath == "/") "index.html" else cleanPath.trimStart('/')
            val fullResourcePath = if (normalizedBase.isEmpty()) targetRelPath else "$normalizedBase/$targetRelPath"

            var stream = classLoader.getResourceAsStream(fullResourcePath)
            var resolvedPath = fullResourcePath
            var resolvedMime = MimeTypes.lookup(targetRelPath)

            // Secure SPA Fallback routing
            if (stream == null && spaFallback != null && shouldApplySpaFallback(request)) {
                val fallbackPath = if (normalizedBase.isEmpty()) {
                    spaFallback.trimStart('/')
                } else {
                    "$normalizedBase/${spaFallback.trimStart('/')}"
                }
                val fallbackStream = classLoader.getResourceAsStream(fallbackPath)
                if (fallbackStream != null) {
                    stream = fallbackStream
                    resolvedPath = fallbackPath
                    resolvedMime = MimeTypes.lookup(spaFallback)
                }
            }

            if (stream == null) {
                return@KromiumAssetHandler KromiumAssetResponse.notFound("Resource not found: ${request.path}")
                    .withHeaders(defaultHeaders)
            }

            // Close the probe stream to avoid leaking unclosed handles in custom classloaders
            try { stream.close() } catch (_: Throwable) {}

            KromiumAssetResponse.stream(
                mimeType = resolvedMime,
                contentLength = null,
                streamProvider = {
                    classLoader.getResourceAsStream(resolvedPath)
                        ?: throw java.io.FileNotFoundException("Classpath resource not found: $resolvedPath")
                }
            ).withHeaders(defaultHeaders)
        }
    }

    /**
     * Creates an asset handler that serves files from a local directory on disk.
     *
     * Strictly verifies canonical paths to prevent directory traversal attacks outside the root.
     *
     * @param directory The local root directory containing the web assets.
     * @param spaFallback Optional fallback file name (e.g. "index.html") served on extensionless GET routes.
     * @param defaultHeaders Optional default headers appended to every response.
     */
    @JvmStatic
    @JvmOverloads
    fun fromDirectory(
        directory: File,
        spaFallback: String? = null,
        defaultHeaders: Map<String, String> = emptyMap()
    ): KromiumAssetHandler {
        require(directory.exists()) { "Directory does not exist: ${directory.absolutePath}" }
        require(directory.isDirectory) { "Path is not a directory: ${directory.absolutePath}" }
        val canonicalRoot = directory.canonicalFile
        val rootPrefix = if (canonicalRoot.path.endsWith(File.separator)) canonicalRoot.path else canonicalRoot.path + File.separator

        return KromiumAssetHandler { request ->
            val cleanPath = sanitizePath(request.path)
            if (cleanPath == null) {
                return@KromiumAssetHandler KromiumAssetResponse.forbidden("Invalid or unsafe path")
            }

            val relPath = if (cleanPath.isEmpty() || cleanPath == "/") "index.html" else cleanPath.trimStart('/')
            var targetFile = File(canonicalRoot, relPath).canonicalFile

            // Directory traversal shield: ensure targetFile is strictly inside canonicalRoot
            if (!targetFile.path.startsWith(rootPrefix) && targetFile.path != canonicalRoot.path) {
                return@KromiumAssetHandler KromiumAssetResponse.forbidden("Directory traversal access denied")
            }

            if (targetFile.isDirectory) {
                targetFile = File(targetFile, "index.html").canonicalFile
            }

            var resolvedFile: File? = if (targetFile.exists() && targetFile.isFile) targetFile else null

            // Secure SPA Fallback routing
            if (resolvedFile == null && spaFallback != null && shouldApplySpaFallback(request)) {
                val fallbackFile = File(canonicalRoot, spaFallback.trimStart('/')).canonicalFile
                if (fallbackFile.exists() && fallbackFile.isFile && (fallbackFile.path.startsWith(rootPrefix) || fallbackFile.path == canonicalRoot.path)) {
                    resolvedFile = fallbackFile
                }
            }

            if (resolvedFile == null) {
                return@KromiumAssetHandler KromiumAssetResponse.notFound("File not found: ${request.path}")
                    .withHeaders(defaultHeaders)
            }

            val fileToServe = resolvedFile
            KromiumAssetResponse.stream(
                mimeType = MimeTypes.lookup(fileToServe.name),
                contentLength = fileToServe.length(),
                streamProvider = { FileInputStream(fileToServe) }
            ).withHeaders(defaultHeaders)
        }
    }

    /**
     * Creates a custom [KromiumAssetHandler] from a functional lambda.
     */
    @JvmStatic
    fun create(handler: KromiumAssetHandler): KromiumAssetHandler = handler

    /**
     * Sanitizes a requested URL path against directory traversal attempts (`..`, `%2e%2e`, `\0`).
     * Returns null if path is malicious.
     */
    @JvmStatic
    fun sanitizePath(rawPath: String): String? {
        if (rawPath.contains('\u0000')) return null

        val decoded = try {
            URLDecoder.decode(rawPath, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            return null
        }

        if (decoded.contains('\u0000')) return null

        val normalized = decoded.replace('\\', '/')
        val segments = normalized.split('/')
        for (segment in segments) {
            if (segment == "..") {
                return null
            }
        }

        return normalized
    }

    /**
     * Determines whether SPA fallback routing should apply.
     *
     * Strictly applies only to GET navigation requests where:
     * 1. The path does not have a static file extension (e.g. `/dashboard`, `/users/42`), OR
     * 2. The client explicitly requested `Accept: text/html`.
     *
     * Never applies to missing static assets (`.js`, `.css`, `.wasm`, `.png`, `.json`) to prevent MIME confusion.
     */
    private fun shouldApplySpaFallback(request: KromiumAssetRequest): Boolean {
        if (!request.method.equals("GET", ignoreCase = true)) {
            return false
        }

        // If it looks like a missing asset file (e.g. bundle.js, style.css), do NOT fall back to HTML
        if (MimeTypes.hasExtension(request.path)) {
            val accept = request.getHeader("Accept")
            return accept != null && accept.contains("text/html")
        }

        return true
    }
}
