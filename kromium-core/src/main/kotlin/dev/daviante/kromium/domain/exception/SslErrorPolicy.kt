package dev.daviante.kromium.domain.exception

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
 * SSL error handling policy for [KromiumClient].
 *
 * Controls how certificate validation errors are handled. Defaults to [Strict]
 * which rejects all invalid certificates (recommended for production).
 */
sealed class SslErrorPolicy {
    /** Reject all certificate errors (default, recommended for production). */
    data object Strict : SslErrorPolicy()

    /** Allow certificate errors only for the specified domains. */
    data class AllowDomains(val domains: Set<String>) : SslErrorPolicy() {
        constructor(vararg domains: String) : this(domains.toSet())

        /** Checks whether the given URL's host is in the allowed domains list. */
        fun isAllowed(url: String?): Boolean {
            if (url == null) return false
            return try {
                val host = java.net.URI(url).host?.lowercase() ?: return false
                domains.any { domain ->
                    val d = domain.lowercase()
                    host == d || host.endsWith(".$d")
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * ⚠️ **DANGEROUS**: Allow ALL certificate errors for ALL domains.
     * Only use during development/testing. Never ship to production.
     */
    data object AllowAll : SslErrorPolicy()

    companion object {
        @JvmStatic val STRICT: SslErrorPolicy get() = Strict
        @JvmStatic val ALLOW_ALL: SslErrorPolicy get() = AllowAll

        @JvmStatic fun strict(): SslErrorPolicy = Strict
        @JvmStatic fun allowAll(): SslErrorPolicy = AllowAll
        @JvmStatic fun allowDomains(vararg domains: String): SslErrorPolicy = AllowDomains(*domains)
        @JvmStatic fun allowDomains(domains: Set<String>): SslErrorPolicy = AllowDomains(domains)
    }
}
