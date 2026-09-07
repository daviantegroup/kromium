package dev.daviante.kromium.domain.config

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
 * Defines the proxy strategy for the Kromium browser instance.
 */
sealed class KromiumProxy {
    /** Uses the system default proxy settings. */
    data object System : KromiumProxy()

    /** Bypasses any system proxies and connects directly. */
    data object Direct : KromiumProxy()

    /** Connects via an HTTP/HTTPS proxy. Username/Password requires the Authentication Handler to be implemented. */
    data class Http(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null
    ) : KromiumProxy()

    /** Connects via a SOCKS5 proxy. */
    data class Socks5(
        val host: String,
        val port: Int
    ) : KromiumProxy()
}
