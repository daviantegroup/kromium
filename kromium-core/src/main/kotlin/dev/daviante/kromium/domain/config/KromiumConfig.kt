package dev.daviante.kromium.domain.config

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.util.FileUtils
import dev.daviante.kromium.data.engine.EngineRegistry
import dev.daviante.kromium.domain.exception.KromiumException
import dev.daviante.kromium.domain.model.KromiumCustomScheme
import dev.daviante.kromium.domain.model.KromiumSchemeRegistration
import dev.daviante.kromium.presentation.scheme.KromiumAssetHandler
import org.cef.CefSettings
import java.io.File

private const val TAG = "KromiumConfig"

/**
 * Configuration for the Kromium engine.
 *
 * Apply configuration in the [Kromium.initialize] lambda:
 * ```kotlin
 * Kromium.initialize {
 *     installDir = File("/path/to/jcef")
 *     userAgent = "MyApp/1.0"
 *     remoteDebuggingPort = 9222
 *     sandboxEnabled = true
 * }
 * ```
 */
class KromiumConfig {
    /** Directory where the JCEF engine binaries will be installed. */
    var installDir: File = EngineRegistry.defaultInstallDir()
        set(value) {
            field = FileUtils.sanitizeDirectory(value)
                ?: throw IllegalArgumentException("Invalid or unsafe install directory: ${value.path}")
        }

    /** Path for the CEF cache (cookies, localStorage, etc.). Null uses an in-memory cache. */
    var cachePath: String? = null

    /** Custom User-Agent string for all browser instances. */
    var userAgent: String? = null

    /** Enable off-screen (windowless) rendering. Defaults to false for standard Compose Desktop SwingPanel integration. */
    var windowlessRendering: Boolean = false

    /** Port for Chrome DevTools remote debugging. 0 = disabled. */
    var remoteDebuggingPort: Int = 0

    /** Specific JetBrains Runtime release tag to download. Null = latest. */
    var releaseTag: String? = null

    /**
     * Optional custom download URL for the JCEF engine bundle.
     * Useful for enterprise, air-gapped, or internal mirror environments to avoid GitHub API rate limits.
     */
    var customBundleUrl: String? = null

    /** Optional checksum URL for verifying a custom bundle. */
    var customChecksumUrl: String? = null

    /** CEF log severity level. */
    var logSeverity: CefSettings.LogSeverity = CefSettings.LogSeverity.LOGSEVERITY_DEFAULT

    /**
     * Whether Kromium is allowed to automatically download the JCEF engine binaries if they are missing from [installDir].
     *
     * Set to `false` in enterprise, offline, or pre-bundled environments where automatic network downloads
     * are strictly forbidden. If `false` and the engine binaries are not found at [installDir], initialization
     * will immediately throw [dev.daviante.kromium.domain.exception.KromiumException.AutoDownloadDisabled]
     * without attempting any network connection.
     *
     * Can also be configured globally via the `-Dkromium.auto.download=false` JVM system property.
     *
     * Defaults to `true`.
     */
    var autoDownload: Boolean = System.getProperty("kromium.auto.download")?.toBooleanStrictOrNull() ?: true

    /**
     * Enable the Chromium sandbox for renderer and GPU sub-processes.
     *
     * **Strongly recommended** for applications that load untrusted web content.
     * Disabling this removes a critical security boundary.
     *
     * Note: On Windows, sandboxing requires the `cef_sandbox.lib` to be linked
     * into the browser subprocess executable.
     */
    var sandboxEnabled: Boolean = true

    /**
     * Network proxy configuration for this Kromium instance.
     * Defaults to [KromiumProxy.System].
     */
    var proxy: KromiumProxy = KromiumProxy.System

    /**
     * Suppresses Chromium background telemetry, crash reporting, update checks,
     * and Windows registry modifications (e.g. UsageStats, default browser checks,
     * component updates, autorun hooks, and native toast notifications).
     *
     * Enabled by default for clean embedded desktop operation.
     */
    var blockRegistryAndTelemetry: Boolean = true
        set(value) {
            field = value
            if (value) {
                applyRegistrySuppressionFlags()
            } else {
                commandLineArgs.removeAll { arg ->
                    REGISTRY_SUPPRESSION_FLAGS.any { it.equals(arg, ignoreCase = true) } ||
                        (arg.startsWith("--disable-features=") && arg.contains("WinNativeNotification"))
                }
            }
        }

    /**
     * Enables desktop environment emulation for headless / OSR mode.
     * Normalizes navigator properties (plugins, languages, chrome runtime, WebGL context)
     * to match a standard interactive desktop browser session.
     * Defaults to false.
     */
    var emulateDesktopEnvironment: Boolean = false

    /**
     * Default SSL error handling policy for clients created by the engine.
     * Defaults to [SslErrorPolicy.Strict] which rejects all certificate validation errors.
     */
    var sslErrorPolicy: dev.daviante.kromium.domain.exception.SslErrorPolicy =
        dev.daviante.kromium.domain.exception.SslErrorPolicy.Strict

    /**
     * Controls WebRTC candidate gathering and IP address exposure.
     *
     * Options:
     * - [WEBRTC_POLICY_DEFAULT]: Standard WebRTC behavior.
     * - [WEBRTC_POLICY_DEFAULT_PUBLIC_INTERFACE_ONLY]: Prevents binding to private LAN IP addresses (Default when [blockRegistryAndTelemetry] is true).
     * - [WEBRTC_POLICY_DISABLE_NON_PROXIED_UDP]: Prevents any non-proxied UDP traffic (Default when an explicit proxy is configured).
     *
     * Set to null to use automatic selection based on proxy and telemetry configuration.
     */
    var webrtcIpHandlingPolicy: String? = null

    /**
     * Enables the standard Do Not Track (DNT) header and Global Privacy Control signals.
     * Automatically injects `--enable-do-not-track` into Chromium startup arguments.
     * Defaults to true.
     */
    var doNotTrack: Boolean = true

    /**
     * Command-line arguments passed to the CEF process.
     *
     * Default includes rendering optimization flags, security hardening, and
     * privacy/registry suppression flags:
     * - `--disable-extensions` — Prevents loading untrusted browser extensions
     * - `--disable-plugins` — Disables PPAPI/NPAPI plugin loading
     * - Anti-telemetry and registry suppression flags when [blockRegistryAndTelemetry] is true
     */
    val commandLineArgs: MutableList<String> = mutableListOf(
        "--disable-gpu-compositing",
        "--enable-begin-frame-scheduling",
        "--disable-extensions",
        "--disable-plugins", "--disable-site-isolation-trials")

    init {
        if (blockRegistryAndTelemetry) {
            applyRegistrySuppressionFlags()
        }
        if (doNotTrack && commandLineArgs.none { it.equals("--enable-do-not-track", ignoreCase = true) }) {
            commandLineArgs.add("--enable-do-not-track")
        }
    }

    private fun applyRegistrySuppressionFlags() {
        for (flag in REGISTRY_SUPPRESSION_FLAGS) {
            if (commandLineArgs.none { it.equals(flag, ignoreCase = true) }) {
                commandLineArgs.add(flag)
            }
        }
        val existingFeatures = commandLineArgs.firstOrNull { it.startsWith("--disable-features=") }
        if (existingFeatures == null) {
            commandLineArgs.add("--disable-features=$REGISTRY_SUPPRESSION_FEATURES")
        } else if (!existingFeatures.contains("WinNativeNotification")) {
            commandLineArgs.remove(existingFeatures)
            commandLineArgs.add("$existingFeatures,$REGISTRY_SUPPRESSION_FEATURES")
        }
    }

    /** Appends additional command-line arguments. */
    fun addArgs(vararg args: String) {
        commandLineArgs.addAll(args)
    }

    /**
     * Custom protocol schemes to register with Chromium's security manager during bootstrap.
     */
    val customSchemes: MutableList<KromiumCustomScheme> = mutableListOf()

    /**
     * Initial virtual scheme handler registrations bound upon engine initialization.
     */
    val schemeHandlers: MutableList<KromiumSchemeRegistration> = mutableListOf()

    /**
     * Registers a custom protocol scheme with Chromium.
     *
     * Example:
     * ```kotlin
     * registerCustomScheme("app")
     * ```
     */
    @JvmOverloads
    fun registerCustomScheme(
        schemeName: String,
        isStandard: Boolean = true,
        isLocal: Boolean = true,
        isDisplayIsolated: Boolean = false,
        isSecure: Boolean = true,
        isCorsEnabled: Boolean = true,
        isCspBypassing: Boolean = false,
        isFetchEnabled: Boolean = true
    ) {
        val scheme = KromiumCustomScheme(
            schemeName = schemeName,
            isStandard = isStandard,
            isLocal = isLocal,
            isDisplayIsolated = isDisplayIsolated,
            isSecure = isSecure,
            isCorsEnabled = isCorsEnabled,
            isCspBypassing = isCspBypassing,
            isFetchEnabled = isFetchEnabled
        )
        registerCustomScheme(scheme)
    }

    /**
     * Registers a custom protocol scheme using a [KromiumCustomScheme] definition.
     */
    fun registerCustomScheme(scheme: KromiumCustomScheme) {
        if (customSchemes.none { it.schemeName.equals(scheme.schemeName, ignoreCase = true) }) {
            customSchemes.add(scheme)
        }
    }

    /**
     * Registers a custom protocol scheme and associates it directly with a [KromiumAssetHandler].
     *
     * Example:
     * ```kotlin
     * registerCustomScheme("app", "myapp", KromiumSchemeHandler.fromClasspath("web"))
     * ```
     */
    @JvmOverloads
    fun registerCustomScheme(
        schemeName: String,
        domainName: String? = null,
        handler: KromiumAssetHandler
    ) {
        registerCustomScheme(schemeName)
        registerSchemeHandler(schemeName, domainName, handler)
    }

    /**
     * Registers a virtual asset handler for an existing or custom scheme and domain.
     */
    @JvmOverloads
    fun registerSchemeHandler(
        schemeName: String,
        domainName: String? = null,
        handler: KromiumAssetHandler
    ) {
        schemeHandlers.add(KromiumSchemeRegistration(schemeName, domainName, handler))
    }

    /**
     * Whitelist of servers/proxies permitted for Integrated Windows Authentication
     * (NTLM / Kerberos Negotiate). Essential for enterprise single sign-on (SSO).
     *
     * Example: `listOf("*.corp.internal", "proxy.company.com")`
     * Maps to Chromium's `--auth-server-allowlist` flag.
     */
    var authServerAllowlist: List<String> = emptyList()

    /**
     * Whitelist of servers/proxies permitted for Kerberos credential delegation.
     * Maps to Chromium's `--auth-negotiate-delegate-allowlist` flag.
     */
    var authNegotiateDelegateAllowlist: List<String> = emptyList()

    /**
     * Validates this configuration and throws [KromiumException.InvalidConfig]
     * if any values are invalid. Called automatically by [Kromium.initialize].
     */
    fun validate() {
        if (remoteDebuggingPort < 0 || remoteDebuggingPort > 65535) {
            throw KromiumException.InvalidConfig(
                "remoteDebuggingPort must be 0-65535, got: $remoteDebuggingPort"
            )
        }

        cachePath?.let { path ->
            if (path.contains("..")) {
                throw KromiumException.InvalidConfig("cachePath contains path traversal sequence: $path")
            }
        }

        // Validate proxy parameters
        proxy.validate()

        // Warn about dangerous flags
        val dangerousFlags = commandLineArgs.filter { arg ->
            val lower = arg.lowercase()
            lower.contains("--no-sandbox") ||
                lower.contains("--disable-web-security") ||
                lower.contains("--allow-running-insecure-content")
        }

        for (flag in dangerousFlags) {
            KromiumLogger.w(TAG, "⚠️ Dangerous command-line flag detected: $flag — This significantly reduces security.")
        }

        if (!sandboxEnabled) {
            KromiumLogger.w(TAG, "⚠️ Sandbox is disabled. Do not load untrusted web content without sandboxing.")
        }
    }

    /**
     * Converts this config to CEF native settings.
     */
    fun toCefSettings(): CefSettings {
        val settings = CefSettings()
        settings.windowless_rendering_enabled = windowlessRendering
        settings.log_severity = logSeverity
        val requestedCache = cachePath
        val effectiveCachePath = if (requestedCache != null) {
            if (requestedCache.isEmpty()) null else requestedCache
        } else {
            try {
                val base = installDir.canonicalFile
                if (base.path.contains("..")) {
                    null
                } else {
                    val parent = base.parentFile ?: base
                    val target = File(parent, "cache").canonicalFile
                    if (!target.canonicalPath.startsWith(parent.canonicalPath)) {
                        null
                    } else {
                        target.apply { mkdirs() }.canonicalPath
                    }
                }
            } catch (_: Exception) { null }
        }

        effectiveCachePath?.let { path ->
            settings.cache_path = path
            if (commandLineArgs.none { it.startsWith("--root-cache-path=") }) {
                commandLineArgs.add("--root-cache-path=$path")
            }
            if (commandLineArgs.none { it.startsWith("--user-data-dir=") }) {
                commandLineArgs.add("--user-data-dir=$path")
            }
        }

        userAgent?.let { settings.user_agent = it }
        if (remoteDebuggingPort > 0) {
            settings.remote_debugging_port = remoteDebuggingPort
        }
        if (!sandboxEnabled) {
            settings.no_sandbox = true
            if (commandLineArgs.none { it.equals("--no-sandbox", ignoreCase = true) }) {
                commandLineArgs.add("--no-sandbox")
            }
        }

        if (blockRegistryAndTelemetry) {
            applyRegistrySuppressionFlags()
        }

        // Apply Proxy configuration (cleans up any existing proxy flags before applying current strategy)
        commandLineArgs.removeAll { arg ->
            arg == "--no-proxy-server" ||
                arg == "--proxy-auto-detect" ||
                arg.startsWith("--proxy-server=") ||
                arg.startsWith("--proxy-pac-url=") ||
                arg.startsWith("--proxy-bypass-list=")
        }
        for (arg in proxy.toCommandLineArgs()) {
            commandLineArgs.add(arg)
        }

        // Apply Enterprise Integrated Windows Authentication (NTLM / Kerberos)
        if (authServerAllowlist.isNotEmpty()) {
            val arg = "--auth-server-allowlist=${authServerAllowlist.joinToString(",")}"
            commandLineArgs.removeAll { it.startsWith("--auth-server-allowlist=") }
            commandLineArgs.add(arg)
        }
        if (authNegotiateDelegateAllowlist.isNotEmpty()) {
            val arg = "--auth-negotiate-delegate-allowlist=${authNegotiateDelegateAllowlist.joinToString(",")}"
            commandLineArgs.removeAll { it.startsWith("--auth-negotiate-delegate-allowlist=") }
            commandLineArgs.add(arg)
        }

        // Apply WebRTC IP handling policy
        val effectiveWebRtcPolicy = webrtcIpHandlingPolicy ?: when {
            proxy !is KromiumProxy.Direct && proxy !is KromiumProxy.System -> WEBRTC_POLICY_DISABLE_NON_PROXIED_UDP
            blockRegistryAndTelemetry -> WEBRTC_POLICY_DEFAULT_PUBLIC_INTERFACE_ONLY
            else -> null
        }
        if (effectiveWebRtcPolicy != null) {
            commandLineArgs.removeAll { it.startsWith("--webrtc-ip-handling-policy=") }
            commandLineArgs.add("--webrtc-ip-handling-policy=$effectiveWebRtcPolicy")
        }

        // Apply Do Not Track (DNT)
        if (doNotTrack) {
            if (commandLineArgs.none { it.equals("--enable-do-not-track", ignoreCase = true) }) {
                commandLineArgs.add("--enable-do-not-track")
            }
        } else {
            commandLineArgs.removeAll { it.equals("--enable-do-not-track", ignoreCase = true) }
        }

        return settings
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()

        /** Standard WebRTC IP gathering across all interfaces. */
        const val WEBRTC_POLICY_DEFAULT: String = "default"

        /** WebRTC only binds to public interfaces, preventing internal LAN IP leaks. */
        const val WEBRTC_POLICY_DEFAULT_PUBLIC_INTERFACE_ONLY: String = "default_public_interface_only"

        /** WebRTC drops all non-proxied UDP traffic, preventing any proxy IP bypass. */
        const val WEBRTC_POLICY_DISABLE_NON_PROXIED_UDP: String = "disable_non_proxied_udp"

        /**
         * Core Chromium flags that completely suppress background telemetry,
         * crash reporting, component updates, and Windows registry write hooks.
         */
        @JvmField
        val REGISTRY_SUPPRESSION_FLAGS: List<String> = listOf(
            "--no-default-browser-check",
            "--no-first-run",
            "--disable-breakpad",
            "--disable-crash-reporter",
            "--disable-metrics",
            "--disable-metrics-reporting",
            "--disable-component-update",
            "--disable-background-networking",
            "--disable-domain-reliability",
            "--disable-sync",
            "--no-service-autorun",
            "--disable-background-mode"
        )

        /**
         * Chromium feature flags to disable Windows shell hooks (such as Action Center toast COM registrations).
         */
        const val REGISTRY_SUPPRESSION_FEATURES: String =
            "WinNativeNotification,CalculateNativeWinOcclusion,CertificateTransparencyComponentUpdater"
    }

    /**
     * Fluent Builder for [KromiumConfig] enabling idiomatic configuration from Java.
     */
    class Builder {
        private val config = KromiumConfig()

        fun installDir(installDir: File) = apply { config.installDir = installDir }
        fun cachePath(cachePath: String?) = apply { config.cachePath = cachePath }
        fun userAgent(userAgent: String?) = apply { config.userAgent = userAgent }
        fun windowlessRendering(windowless: Boolean) = apply { config.windowlessRendering = windowless }
        fun remoteDebuggingPort(port: Int) = apply { config.remoteDebuggingPort = port }
        fun releaseTag(tag: String?) = apply { config.releaseTag = tag }
        fun customBundleUrl(url: String?) = apply { config.customBundleUrl = url }
        fun customChecksumUrl(url: String?) = apply { config.customChecksumUrl = url }
        fun logSeverity(severity: CefSettings.LogSeverity) = apply { config.logSeverity = severity }
        fun autoDownload(autoDownload: Boolean) = apply { config.autoDownload = autoDownload }
        fun sandboxEnabled(enabled: Boolean) = apply { config.sandboxEnabled = enabled }
        fun proxy(proxy: KromiumProxy) = apply { config.proxy = proxy }
        fun blockRegistryAndTelemetry(block: Boolean) = apply { config.blockRegistryAndTelemetry = block }
        fun addArgs(vararg args: String) = apply { config.addArgs(*args) }
        fun authServerAllowlist(allowlist: List<String>) = apply { config.authServerAllowlist = allowlist }
        fun authNegotiateDelegateAllowlist(allowlist: List<String>) = apply { config.authNegotiateDelegateAllowlist = allowlist }
        fun emulateDesktopEnvironment(enable: Boolean) = apply { config.emulateDesktopEnvironment = enable }
        fun sslErrorPolicy(policy: dev.daviante.kromium.domain.exception.SslErrorPolicy) = apply { config.sslErrorPolicy = policy }
        fun webrtcIpHandlingPolicy(policy: String?) = apply { config.webrtcIpHandlingPolicy = policy }
        fun doNotTrack(enabled: Boolean) = apply { config.doNotTrack = enabled }

        fun build(): KromiumConfig {
            config.validate()
            return config
        }
    }
}



