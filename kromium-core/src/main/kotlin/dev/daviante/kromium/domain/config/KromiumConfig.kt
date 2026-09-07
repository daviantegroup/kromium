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
            val rawPath = value.path
            if (rawPath.contains("..")) {
                throw IllegalArgumentException("Path traversal sequence detected in: $rawPath")
            }
            val canonical = value.canonicalFile
            val canonicalPath = canonical.canonicalPath
            if (canonicalPath.contains("..")) {
                throw IllegalArgumentException("Path traversal sequence detected in: $canonicalPath")
            }
            val roots = File.listRoots() ?: emptyArray()
            val root = roots.firstOrNull { r ->
                val rPath = r.canonicalPath
                canonicalPath.startsWith(rPath) && canonicalPath.length > rPath.length
            } ?: throw IllegalArgumentException("Install directory outside filesystem root: $canonicalPath")
            if (!canonicalPath.startsWith(root.canonicalPath)) {
                throw IllegalArgumentException("Root validation failed for: $canonicalPath")
            }
            val sanitized = FileUtils.sanitizeDirectory(canonical)
                ?: throw IllegalArgumentException("Invalid or unsafe install directory: ${value.path}")
            field = sanitized
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
        "--disable-plugins"
    )

    init {
        if (blockRegistryAndTelemetry) {
            applyRegistrySuppressionFlags()
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

        return settings
    }

    companion object {
        /**
         * Core Chromium flags that completely suppress background telemetry,
         * crash reporting, component updates, and Windows registry write hooks.
         */
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
}
