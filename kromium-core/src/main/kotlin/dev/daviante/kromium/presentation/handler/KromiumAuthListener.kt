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
 * Listener for authentication requests.
 */
fun interface KromiumAuthListener {
    /**
     * Called when the browser requires authentication.
     * Return [KromiumAuthResponse.Proceed] with credentials, or [KromiumAuthResponse.Cancel].
     */
    fun onAuthRequired(request: KromiumAuthRequest): KromiumAuthResponse
}
