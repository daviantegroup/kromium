package dev.daviante.kromium.presentation.scheme

/**
 * Functional interface for handling intercepted virtual asset requests.
 *
 * Compatible with Kotlin lambdas `{ request -> ... }` and Java lambdas `request -> ...`.
 */
fun interface KromiumAssetHandler {
    /**
     * Handles an incoming [KromiumAssetRequest] and returns a [KromiumAssetResponse],
     * or null to produce a 404 response.
     *
     * @param request The intercepted virtual HTTP request.
     * @return A [KromiumAssetResponse] containing status, headers, and data stream, or null for 404.
     */
    fun handle(request: KromiumAssetRequest): KromiumAssetResponse?
}
