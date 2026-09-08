package dev.daviante.kromium.presentation.network



/**
 * Represents an outgoing web resource request.
 */
data class KromiumWebResourceRequest(
    val url: String,
    val method: String,
    val headers: MutableMap<String, String>,
    val isNavigation: Boolean,
    val isDownload: Boolean,
    val requestInitiator: String? = null
)

/**
 * Functional interface to intercept and modify HTTP/HTTPS requests or block unwanted domains.
 */
fun interface KromiumRequestInterceptor {
    /**
     * Intercepts an outgoing request.
     * Developers can mutate [request.headers] or return `true` to cancel/block the request.
     *
     * @return `true` to block the request (e.g. ad/tracker blocking), or `false` to let it proceed.
     */
    fun intercept(request: KromiumWebResourceRequest): Boolean
}
