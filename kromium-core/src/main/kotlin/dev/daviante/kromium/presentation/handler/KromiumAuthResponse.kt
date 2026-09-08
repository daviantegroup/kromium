package dev.daviante.kromium.presentation.handler

/**
 * The response to an authentication request.
 */
sealed class KromiumAuthResponse {
    /** Cancel the authentication request. */
    data object Cancel : KromiumAuthResponse()

    /** Provide credentials. */
    data class Proceed(val username: String, val password: String) : KromiumAuthResponse()

    companion object {
        @JvmStatic val CANCEL: KromiumAuthResponse get() = Cancel
        @JvmStatic fun cancel(): KromiumAuthResponse = Cancel
        @JvmStatic fun proceed(username: String, password: String): KromiumAuthResponse = Proceed(username, password)
    }
}
