package dev.daviante.kromium.presentation.handler

import dev.daviante.kromium.domain.model.*
import dev.daviante.kromium.domain.config.*
import dev.daviante.kromium.domain.exception.*
import dev.daviante.kromium.data.engine.*
import dev.daviante.kromium.data.model.*
import dev.daviante.kromium.presentation.browser.*
import dev.daviante.kromium.presentation.handler.*
import dev.daviante.kromium.presentation.js.*
import dev.daviante.kromium.presentation.network.*
import dev.daviante.kromium.core.logging.*
import dev.daviante.kromium.core.util.*


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
