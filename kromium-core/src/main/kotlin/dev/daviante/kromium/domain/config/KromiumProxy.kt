package dev.daviante.kromium.domain.config

import dev.daviante.kromium.domain.exception.KromiumException

/**
 * Defines the proxy strategy for the Kromium browser engine and client sessions.
 *
 * Designed to satisfy both Small Business and Enterprise network architectures:
 * - Operating System Default Proxy
 * - Direct (Bypass all proxies)
 * - WPAD (Web Proxy Auto-Discovery Protocol via DHCP/DNS)
 * - PAC (Proxy Auto-Configuration script URLs)
 * - HTTP & Secure HTTPS Proxy Tunnels (with automatic authentication & bypass rules)
 * - SOCKS5 / SOCKS4 (with remote DNS resolution & authentication)
 * - Multi-Protocol Proxy Routing (different proxies for HTTP vs HTTPS vs SOCKS)
 * - Dynamic runtime switching without browser engine restart
 */
sealed class KromiumProxy {
    /** Uses the operating system's default proxy configuration. */
    data object System : KromiumProxy() {
        override fun toCommandLineArgs(): List<String> = emptyList()
        override fun toPreferenceMap(): Map<String, Any> = mapOf("mode" to "system")
    }

    /** Bypasses all proxies and connects directly to destination hosts. */
    data object Direct : KromiumProxy() {
        override fun toCommandLineArgs(): List<String> = listOf("--no-proxy-server")
        override fun toPreferenceMap(): Map<String, Any> = mapOf("mode" to "direct")
    }

    /**
     * Automatically discovers proxy settings using Web Proxy Auto-Discovery (WPAD)
     * via DHCP and DNS queries. Standard in enterprise Active Directory environments.
     */
    data object AutoDetect : KromiumProxy() {
        override fun toCommandLineArgs(): List<String> = listOf("--proxy-auto-detect")
        override fun toPreferenceMap(): Map<String, Any> = mapOf("mode" to "auto_detect")
    }

    /**
     * Connects using a Proxy Auto-Configuration (PAC) script URL (e.g. `http://pac.corp.internal/wpad.dat`).
     *
     * @param pacUrl The HTTP, HTTPS, or file URL pointing to the proxy PAC script.
     */
    data class Pac(
        val pacUrl: String
    ) : KromiumProxy() {
        override fun toCommandLineArgs(): List<String> = listOf("--proxy-pac-url=$pacUrl")
        override fun toPreferenceMap(): Map<String, Any> = mapOf(
            "mode" to "pac_script",
            "pac_url" to pacUrl
        )

        override fun validate() {
            if (pacUrl.isBlank()) {
                throw KromiumException.InvalidConfig("PAC proxy URL must not be blank")
            }
        }
    }

    /**
     * Connects via an HTTP or HTTPS (secure tunnel) forward proxy.
     *
     * @param host Proxy server hostname or IP address.
     * @param port Proxy server port (1..65535).
     * @param username Optional username for authenticated proxies (HTTP 407).
     * @param password Optional password for authenticated proxies.
     * @param isSecure When true, establishes a TLS connection to the proxy itself (`https://`),
     *                 essential for modern Zero-Trust enterprise egress proxies.
     * @param bypassList List of hosts/patterns that bypass the proxy (e.g. `listOf("<local>", "127.0.0.1", "*.internal.corp")`).
     */
    data class Http @JvmOverloads constructor(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
        val isSecure: Boolean = false,
        val bypassList: List<String> = emptyList()
    ) : KromiumProxy() {
        val scheme: String get() = if (isSecure) "https" else "http"
        val serverSpec: String get() = "$scheme://$host:$port"

        override fun toCommandLineArgs(): List<String> {
            val args = mutableListOf("--proxy-server=$serverSpec")
            if (bypassList.isNotEmpty()) {
                args.add("--proxy-bypass-list=${bypassList.joinToString(";")}")
            }
            return args
        }

        override fun toPreferenceMap(): Map<String, Any> {
            val map = mutableMapOf<String, Any>(
                "mode" to "fixed_servers",
                "server" to serverSpec
            )
            if (bypassList.isNotEmpty()) {
                map["bypass_list"] = bypassList.joinToString(";")
            }
            return map
        }

        override fun getCredentials(targetHost: String?, targetPort: Int?): Pair<String, String>? {
            if (username != null && password != null) {
                if (targetHost.isNullOrBlank() || targetHost.equals(host, ignoreCase = true)) {
                    if (targetPort == null || targetPort == port || targetPort == 0) {
                        return username to password
                    }
                }
            }
            return null
        }

        override fun validate() {
            if (host.isBlank()) throw KromiumException.InvalidConfig("Proxy host must not be blank")
            if (port !in 1..65535) throw KromiumException.InvalidConfig("Proxy port must be 1..65535, got: $port")
        }
    }

    /**
     * Connects via a SOCKS5 (or SOCKS4) proxy.
     *
     * @param host Proxy server hostname or IP address.
     * @param port Proxy server port (1..65535).
     * @param username Optional username for authenticated SOCKS5 proxies.
     * @param password Optional password for authenticated SOCKS5 proxies.
     * @param remoteDns When true (default), DNS queries are resolved by the proxy (prevents DNS leaks).
     * @param bypassList List of hosts/patterns that bypass the proxy.
     */
    data class Socks5 @JvmOverloads constructor(
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
        val remoteDns: Boolean = true,
        val bypassList: List<String> = emptyList()
    ) : KromiumProxy() {
        val scheme: String get() = if (remoteDns) "socks5" else "socks4"
        val serverSpec: String get() = "$scheme://$host:$port"

        override fun toCommandLineArgs(): List<String> {
            val args = mutableListOf("--proxy-server=$serverSpec")
            if (bypassList.isNotEmpty()) {
                args.add("--proxy-bypass-list=${bypassList.joinToString(";")}")
            }
            return args
        }

        override fun toPreferenceMap(): Map<String, Any> {
            val map = mutableMapOf<String, Any>(
                "mode" to "fixed_servers",
                "server" to serverSpec
            )
            if (bypassList.isNotEmpty()) {
                map["bypass_list"] = bypassList.joinToString(";")
            }
            return map
        }

        override fun getCredentials(targetHost: String?, targetPort: Int?): Pair<String, String>? {
            if (username != null && password != null) {
                if (targetHost.isNullOrBlank() || targetHost.equals(host, ignoreCase = true)) {
                    if (targetPort == null || targetPort == port || targetPort == 0) {
                        return username to password
                    }
                }
            }
            return null
        }

        override fun validate() {
            if (host.isBlank()) throw KromiumException.InvalidConfig("Proxy host must not be blank")
            if (port !in 1..65535) throw KromiumException.InvalidConfig("Proxy port must be 1..65535, got: $port")
        }
    }

    /**
     * Connects using separate proxies for different protocols.
     * Standard in complex enterprise environments routing HTTP vs HTTPS traffic differently.
     *
     * Example:
     * ```kotlin
     * KromiumProxy.MultiProtocol(
     *     http = "http://proxy.corp:8080",
     *     https = "https://secure-proxy.corp:8443",
     *     socks = "socks5://socks.corp:1080",
     *     bypassList = listOf("<local>", "127.0.0.1", "*.corp")
     * )
     * ```
     */
    data class MultiProtocol @JvmOverloads constructor(
        val http: String? = null,
        val https: String? = null,
        val ftp: String? = null,
        val socks: String? = null,
        val bypassList: List<String> = emptyList()
    ) : KromiumProxy() {
        val serverSpec: String get() {
            val rules = mutableListOf<String>()
            http?.takeIf { it.isNotBlank() }?.let { rules.add("http=$it") }
            https?.takeIf { it.isNotBlank() }?.let { rules.add("https=$it") }
            ftp?.takeIf { it.isNotBlank() }?.let { rules.add("ftp=$it") }
            socks?.takeIf { it.isNotBlank() }?.let { rules.add("socks=$it") }
            return rules.joinToString(";")
        }

        override fun toCommandLineArgs(): List<String> {
            val spec = serverSpec
            val args = mutableListOf<String>()
            if (spec.isNotBlank()) {
                args.add("--proxy-server=$spec")
            }
            if (bypassList.isNotEmpty()) {
                args.add("--proxy-bypass-list=${bypassList.joinToString(";")}")
            }
            return args
        }

        override fun toPreferenceMap(): Map<String, Any> {
            val spec = serverSpec
            val map = mutableMapOf<String, Any>(
                "mode" to "fixed_servers",
                "server" to spec
            )
            if (bypassList.isNotEmpty()) {
                map["bypass_list"] = bypassList.joinToString(";")
            }
            return map
        }

        override fun validate() {
            if (http.isNullOrBlank() && https.isNullOrBlank() && ftp.isNullOrBlank() && socks.isNullOrBlank()) {
                throw KromiumException.InvalidConfig("MultiProtocol proxy must specify at least one protocol proxy rule")
            }
        }
    }

    /** Converts this proxy strategy to Chromium command-line switches. */
    abstract fun toCommandLineArgs(): List<String>

    /** Converts this proxy strategy to a Chromium preference dictionary for dynamic runtime switching. */
    abstract fun toPreferenceMap(): Map<String, Any>

    /**
     * Checks if this proxy configuration holds static credentials matching the target host and port.
     */
    open fun getCredentials(targetHost: String?, targetPort: Int?): Pair<String, String>? = null

    /**
     * Validates proxy configuration parameters.
     */
    open fun validate() {}

    companion object {
        /** Uses the operating system's default proxy configuration. */
        @JvmStatic val SYSTEM: KromiumProxy get() = System

        /** Bypasses all proxies and connects directly to destination hosts. */
        @JvmStatic val DIRECT: KromiumProxy get() = Direct

        /** Automatically discovers proxy settings via WPAD. */
        @JvmStatic val AUTO_DETECT: KromiumProxy get() = AutoDetect

        @JvmStatic fun system(): KromiumProxy = System
        @JvmStatic fun direct(): KromiumProxy = Direct
        @JvmStatic fun autoDetect(): KromiumProxy = AutoDetect
        @JvmStatic fun pac(pacUrl: String): KromiumProxy = Pac(pacUrl)

        @JvmStatic
        @JvmOverloads
        fun http(
            host: String,
            port: Int,
            username: String? = null,
            password: String? = null,
            isSecure: Boolean = false,
            bypassList: List<String> = emptyList()
        ): KromiumProxy = Http(host, port, username, password, isSecure, bypassList)

        @JvmStatic
        @JvmOverloads
        fun socks5(
            host: String,
            port: Int,
            username: String? = null,
            password: String? = null,
            remoteDns: Boolean = true,
            bypassList: List<String> = emptyList()
        ): KromiumProxy = Socks5(host, port, username, password, remoteDns, bypassList)

        @JvmStatic
        @JvmOverloads
        fun multiProtocol(
            http: String? = null,
            https: String? = null,
            ftp: String? = null,
            socks: String? = null,
            bypassList: List<String> = emptyList()
        ): KromiumProxy = MultiProtocol(http, https, ftp, socks, bypassList)
    }
}
