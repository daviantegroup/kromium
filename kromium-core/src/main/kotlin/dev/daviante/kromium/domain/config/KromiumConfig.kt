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
            val sanitized = FileUtils.sanitizeDirectory(value)
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
     * Command-line arguments passed to the CEF process.
     *
     * Default includes rendering optimization flags and essential security hardening:
     * - `--disable-extensions` — Prevents loading untrusted browser extensions
     * - `--disable-plugins` — Disables PPAPI/NPAPI plugin loading
     */
    val commandLineArgs: MutableList<String> = mutableListOf(
        "--disable-gpu-compositing",
        "--enable-begin-frame-scheduling",
        "--disable-extensions",
        "--disable-plugins"
    )

    /** Appends additional command-line arguments. */
    fun addArgs(vararg args: String) {
        commandLineArgs.addAll(args)
    }

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
                File(installDir.parentFile, "cache").apply { mkdirs() }.canonicalPath
            } catch (_: Exception) { null }
        }

        effectiveCachePath?.let { path ->
            settings.cache_path = path
            if (commandLineArgs.none { it.startsWith("--root-cache-path=") }) {
                commandLineArgs.add("--root-cache-path=$path")
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

        // Apply Proxy configuration
        when (val p = proxy) {
            is KromiumProxy.System -> { /* Default behavior in CEF */ }
            is KromiumProxy.Direct -> {
                commandLineArgs.add("--no-proxy-server")
            }
            is KromiumProxy.Http -> {
                commandLineArgs.add("--proxy-server=http://${p.host}:${p.port}")
            }
            is KromiumProxy.Socks5 -> {
                commandLineArgs.add("--proxy-server=socks5://${p.host}:${p.port}")
            }
        }

        return settings
    }
}
