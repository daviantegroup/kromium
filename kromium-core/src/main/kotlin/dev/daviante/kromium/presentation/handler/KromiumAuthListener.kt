package dev.daviante.kromium.presentation.handler

/**
 * Listener for authentication requests.
 */
fun interface KromiumAuthListener {
    /**
     * Called when the browser requires authentication.
     * Return [KromiumAuthResponse.Proceed] with credentials, or [KromiumAuthResponse.Cancel].
     */
    fun onAuthRequired(request: KromiumAuthRequest): KromiumAuthResponse
}
