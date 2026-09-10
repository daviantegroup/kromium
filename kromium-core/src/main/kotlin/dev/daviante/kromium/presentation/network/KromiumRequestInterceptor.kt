package dev.daviante.kromium.presentation.network

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
