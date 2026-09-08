package dev.daviante.kromium.presentation.scheme

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Represents a virtual HTTP response returned by a [KromiumAssetHandler] to fulfill a custom scheme request.
 *
 * @property statusCode HTTP response status code (e.g. 200, 404, 500).
 * @property statusText HTTP status line description (e.g. "OK", "Not Found").
 * @property mimeType The MIME Content-Type of the payload (e.g. "text/html", "application/javascript").
 * @property contentLength Total content length in bytes if known, or null for chunked streaming.
 * @property headers HTTP response headers.
 * @property openStream Lambda returning a freshly opened [InputStream] to stream response data to Chromium.
 */
class KromiumAssetResponse @JvmOverloads constructor(
    val statusCode: Int = 200,
    val statusText: String = "OK",
    val mimeType: String = "application/octet-stream",
    val contentLength: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val openStream: () -> InputStream
) {
    /**
     * Returns a new [KromiumAssetResponse] copy with the specified header added or replaced.
     */
    fun withHeader(name: String, value: String): KromiumAssetResponse {
        val updated = LinkedHashMap(headers)
        updated[name] = value
        return KromiumAssetResponse(
            statusCode = statusCode,
            statusText = statusText,
            mimeType = mimeType,
            contentLength = contentLength,
            headers = Collections.unmodifiableMap(updated),
            openStream = openStream
        )
    }

    /**
     * Returns a new [KromiumAssetResponse] with multiple headers appended.
     */
    fun withHeaders(newHeaders: Map<String, String>): KromiumAssetResponse {
        val updated = LinkedHashMap(headers)
        updated.putAll(newHeaders)
        return KromiumAssetResponse(
            statusCode = statusCode,
            statusText = statusText,
            mimeType = mimeType,
            contentLength = contentLength,
            headers = Collections.unmodifiableMap(updated),
            openStream = openStream
        )
    }

    /**
     * Appends permissive CORS headers (`Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, etc.).
     */
    @JvmOverloads
    fun withCors(allowedOrigins: String = "*"): KromiumAssetResponse =
        withHeaders(
            mapOf(
                "Access-Control-Allow-Origin" to allowedOrigins,
                "Access-Control-Allow-Methods" to "GET, POST, HEAD, OPTIONS",
                "Access-Control-Allow-Headers" to "*",
                "Access-Control-Max-Age" to "86400"
            )
        )

    /**
     * Sets standard `Cache-Control` header with a `max-age` value in seconds.
     */
    fun withCacheControl(maxAgeSeconds: Int): KromiumAssetResponse =
        withHeader("Cache-Control", "public, max-age=$maxAgeSeconds")

    /**
     * Sets `Cache-Control: no-cache, no-store, must-revalidate` to prevent caching.
     */
    fun withNoCache(): KromiumAssetResponse =
        withHeader("Cache-Control", "no-cache, no-store, must-revalidate")

    companion object {
        /**
         * Creates a 200 OK response from an in-memory byte array.
         */
        @JvmStatic
        fun ok(bytes: ByteArray, mimeType: String): KromiumAssetResponse =
            KromiumAssetResponse(
                statusCode = 200,
                statusText = "OK",
                mimeType = mimeType,
                contentLength = bytes.size.toLong(),
                openStream = { ByteArrayInputStream(bytes) }
            )

        /**
         * Creates a streaming response from an [InputStream].
         *
         * Note: [streamProvider] is invoked once when CEF starts reading the response.
         */
        @JvmStatic
        @JvmOverloads
        fun stream(
            mimeType: String,
            contentLength: Long? = null,
            streamProvider: () -> InputStream
        ): KromiumAssetResponse =
            KromiumAssetResponse(
                statusCode = 200,
                statusText = "OK",
                mimeType = mimeType,
                contentLength = contentLength,
                openStream = streamProvider
            )

        /**
         * Creates a 200 OK response from a string with UTF-8 encoding.
         */
        @JvmStatic
        @JvmOverloads
        fun text(text: String, mimeType: String = "text/plain; charset=utf-8"): KromiumAssetResponse {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            return ok(bytes, mimeType)
        }

        /**
         * Creates a 200 OK JSON response (`application/json`).
         */
        @JvmStatic
        fun json(json: String): KromiumAssetResponse =
            text(json, "application/json; charset=utf-8")

        /**
         * Creates a 200 OK HTML response (`text/html`).
         */
        @JvmStatic
        fun html(html: String): KromiumAssetResponse =
            text(html, "text/html; charset=utf-8")

        /**
         * Creates a 404 Not Found response.
         */
        @JvmStatic
        @JvmOverloads
        fun notFound(message: String = "404 Not Found"): KromiumAssetResponse {
            val bytes = message.toByteArray(StandardCharsets.UTF_8)
            return KromiumAssetResponse(
                statusCode = 404,
                statusText = "Not Found",
                mimeType = "text/plain; charset=utf-8",
                contentLength = bytes.size.toLong(),
                openStream = { ByteArrayInputStream(bytes) }
            )
        }

        /**
         * Creates a 403 Forbidden response.
         */
        @JvmStatic
        @JvmOverloads
        fun forbidden(message: String = "403 Forbidden"): KromiumAssetResponse {
            val bytes = message.toByteArray(StandardCharsets.UTF_8)
            return KromiumAssetResponse(
                statusCode = 403,
                statusText = "Forbidden",
                mimeType = "text/plain; charset=utf-8",
                contentLength = bytes.size.toLong(),
                openStream = { ByteArrayInputStream(bytes) }
            )
        }

        /**
         * Creates a generic error response with a custom status code.
         */
        @JvmStatic
        fun error(statusCode: Int, message: String): KromiumAssetResponse {
            val bytes = message.toByteArray(StandardCharsets.UTF_8)
            return KromiumAssetResponse(
                statusCode = statusCode,
                statusText = message,
                mimeType = "text/plain; charset=utf-8",
                contentLength = bytes.size.toLong(),
                openStream = { ByteArrayInputStream(bytes) }
            )
        }
    }
}
