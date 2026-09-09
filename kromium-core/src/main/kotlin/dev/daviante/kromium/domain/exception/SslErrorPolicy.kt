package dev.daviante.kromium.domain.exception

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

        /**
         * Checks whether the given URL or host is in the allowed domains list.
         * Supports exact hostnames, port stripping, and wildcard notation (e.g. `*.corp.internal` or `corp.internal`).
         */
        fun isAllowed(urlOrHost: String?): Boolean {
            if (urlOrHost.isNullOrBlank()) return false
            val host = extractHost(urlOrHost)?.lowercase() ?: return false
            return domains.any { domainPattern ->
                val cleanedPattern = extractHost(domainPattern)?.lowercase()?.removePrefix("*.")
                    ?: domainPattern.trim().lowercase().removePrefix("*.")
                if (cleanedPattern.isBlank()) false
                else host == cleanedPattern || host.endsWith(".$cleanedPattern")
            }
        }

        private fun extractHost(input: String): String? {
            val trimmed = input.trim()
            val withoutScheme = if (trimmed.contains("://")) trimmed.substringAfter("://") else trimmed
            val hostPort = withoutScheme.substringBefore("/").substringBefore("?").substringBefore("#")
            return if (hostPort.contains(":")) hostPort.substringBefore(":") else hostPort
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
