package dev.daviante.kromium.presentation.scheme

import org.cef.network.CefRequest
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.HashMap

/**
 * Represents an incoming virtual HTTP request intercepted by a [KromiumAssetHandler].
 *
 * @property url The full URL string of the intercepted request.
 * @property method The HTTP method (e.g. "GET", "POST", "OPTIONS").
 * @property scheme The protocol scheme (e.g. "app", "https").
 * @property domain The host or authority domain component (e.g. "myapp", "localhost").
 * @property path The absolute request path without query strings (e.g. "/index.html", "/api/user").
 * @property queryString The raw URL query string (without the leading '?'), or null if absent.
 * @property queryParameters Parsed query parameters map.
 * @property headers Request headers map (case-insensitive keys).
 */
data class KromiumAssetRequest @JvmOverloads constructor(
    val url: String,
    val method: String = "GET",
    val scheme: String = "",
    val domain: String = "",
    val path: String = "/",
    val queryString: String? = null,
    val queryParameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap()
) {
    /**
     * Retrieves the value of a specific HTTP header in a case-insensitive manner.
     */
    fun getHeader(name: String): String? {
        val target = name.trim().lowercase()
        return headers.entries.firstOrNull { it.key.trim().lowercase() == target }?.value
    }

    /**
     * Retrieves a query parameter by key.
     */
    fun getQueryParam(key: String): String? = queryParameters[key]

    companion object {
        /**
         * Parses a native JCEF [CefRequest] into a [KromiumAssetRequest].
         */
        @JvmStatic
        fun fromCef(cefRequest: CefRequest): KromiumAssetRequest {
            val urlString = cefRequest.url ?: ""
            val method = cefRequest.method ?: "GET"

            val headerMap = HashMap<String, String>()
            try {
                cefRequest.getHeaderMap(headerMap)
            } catch (_: Throwable) {}

            var scheme = ""
            var domain = ""
            var path = "/"
            var queryString: String? = null
            val queryParams = HashMap<String, String>()

            try {
                val uri = URI.create(urlString)
                scheme = uri.scheme ?: ""
                domain = uri.host ?: uri.authority ?: ""
                val rawPath = uri.path
                path = if (!rawPath.isNullOrBlank()) rawPath else "/"
                queryString = uri.rawQuery

                if (!queryString.isNullOrBlank()) {
                    val pairs = queryString.split("&")
                    for (pair in pairs) {
                        val idx = pair.indexOf('=')
                        if (idx > 0) {
                            val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
                            val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                            queryParams[key] = value
                        } else if (pair.isNotEmpty()) {
                            val key = URLDecoder.decode(pair, StandardCharsets.UTF_8)
                            queryParams[key] = ""
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fallback basic URI parsing for non-standard scheme URLs
                val colonIdx = urlString.indexOf("://")
                if (colonIdx > 0) {
                    scheme = urlString.substring(0, colonIdx)
                    val rest = urlString.substring(colonIdx + 3)
                    val slashIdx = rest.indexOf('/')
                    val questionIdx = rest.indexOf('?')
                    if (slashIdx > 0) {
                        domain = rest.substring(0, slashIdx)
                        val endPath = if (questionIdx > slashIdx) questionIdx else rest.length
                        path = rest.substring(slashIdx, endPath)
                        if (questionIdx > 0 && questionIdx < rest.length - 1) {
                            queryString = rest.substring(questionIdx + 1)
                        }
                    } else if (questionIdx > 0) {
                        domain = rest.substring(0, questionIdx)
                        path = "/"
                        queryString = rest.substring(questionIdx + 1)
                    } else {
                        domain = rest
                        path = "/"
                    }
                }
            }

            return KromiumAssetRequest(
                url = urlString,
                method = method,
                scheme = scheme,
                domain = domain,
                path = path,
                queryString = queryString,
                queryParameters = Collections.unmodifiableMap(queryParams),
                headers = Collections.unmodifiableMap(headerMap)
            )
        }
    }
}
