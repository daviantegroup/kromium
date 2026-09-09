package dev.daviante.kromium.domain.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.cef.CefSettings

class KromiumConfigTest {

    @Test
    fun testDefaultSettings() {
        val config = KromiumConfig()
        
        // Check our domain defaults (windowless is false by default for SwingPanel windowed rendering)
        assertFalse(config.windowlessRendering, "Windowless rendering should be disabled by default for SwingPanel")
        assertTrue(config.commandLineArgs.isNotEmpty(), "There should be default command line args for GPU/offscreen")
        
        // Ensure specific default args are present
        assertTrue(config.commandLineArgs.contains("--disable-gpu-compositing"))
        assertTrue(config.commandLineArgs.contains("--enable-begin-frame-scheduling"))
    }

    @Test
    fun testCustomArgsAddition() {
        val config = KromiumConfig()
        config.addArgs("--test-arg=1", "--test-arg=2")
        
        assertTrue(config.commandLineArgs.contains("--test-arg=1"))
        assertTrue(config.commandLineArgs.contains("--test-arg=2"))
    }

    @Test
    fun testToCefSettingsConversion() {
        val config = KromiumConfig()
        config.cachePath = "/tmp/kromium-cache"
        config.logSeverity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
        config.remoteDebuggingPort = 9222
        config.userAgent = "TestAgent/1.0"

        val settings = config.toCefSettings()

        // Validate mapped settings
        assertFalse(settings.windowless_rendering_enabled)
        assertEquals("/tmp/kromium-cache", settings.cache_path)
        assertEquals("TestAgent/1.0", settings.user_agent)
        assertEquals(9222, settings.remote_debugging_port)
        assertTrue(settings.log_severity == CefSettings.LogSeverity.LOGSEVERITY_WARNING)
    }

    @Test
    fun testWindowlessRenderingExplicit() {
        val config = KromiumConfig().apply { windowlessRendering = true }
        val settings = config.toCefSettings()
        assertTrue(settings.windowless_rendering_enabled)
    }

    @Test
    fun testCachePathTraversal() {
        val config = KromiumConfig()
        config.cachePath = "foo/../../bar"
        kotlin.test.assertFailsWith<dev.daviante.kromium.domain.exception.KromiumException.InvalidConfig> {
            config.validate()
        }
    }

    @Test
    fun testRegistrySuppressionFlagsEnabledByDefault() {
        val config = KromiumConfig()
        assertTrue(config.blockRegistryAndTelemetry)

        // Verify key suppression flags
        assertTrue(config.commandLineArgs.contains("--no-default-browser-check"))
        assertTrue(config.commandLineArgs.contains("--no-first-run"))
        assertTrue(config.commandLineArgs.contains("--disable-breakpad"))
        assertTrue(config.commandLineArgs.contains("--disable-crash-reporter"))
        assertTrue(config.commandLineArgs.contains("--disable-metrics"))
        assertTrue(config.commandLineArgs.contains("--disable-component-update"))
        assertTrue(config.commandLineArgs.contains("--disable-sync"))
        assertTrue(config.commandLineArgs.contains("--no-service-autorun"))
        assertTrue(config.commandLineArgs.any { it.startsWith("--disable-features=") && it.contains("WinNativeNotification") })
    }

    @Test
    fun testRegistrySuppressionFlagsCanBeToggled() {
        val config = KromiumConfig()
        assertTrue(config.commandLineArgs.contains("--no-default-browser-check"))

        // Disable registry suppression
        config.blockRegistryAndTelemetry = false
        assertFalse(config.commandLineArgs.contains("--no-default-browser-check"))
        assertFalse(config.commandLineArgs.contains("--disable-breakpad"))

        // Re-enable
        config.blockRegistryAndTelemetry = true
        assertTrue(config.commandLineArgs.contains("--no-default-browser-check"))
        assertTrue(config.commandLineArgs.contains("--disable-breakpad"))
    }

    @Test
    fun testUserDataDirConfiguredWithCache() {
        val config = KromiumConfig()
        config.cachePath = "C:/tmp/kromium-cache"
        config.toCefSettings()

        assertTrue(config.commandLineArgs.any { it == "--root-cache-path=C:/tmp/kromium-cache" })
        assertTrue(config.commandLineArgs.any { it == "--user-data-dir=C:/tmp/kromium-cache" })
    }

    @Test
    fun testAutoDownloadDefaultsAndToggling() {
        val config = KromiumConfig()
        assertTrue(config.autoDownload, "autoDownload should be enabled by default")

        config.autoDownload = false
        assertFalse(config.autoDownload, "autoDownload should be configurable to false")

        val builtConfig = KromiumConfig.builder()
            .autoDownload(false)
            .build()
        assertFalse(builtConfig.autoDownload, "Builder should properly configure autoDownload")
    }

    @Test
    fun testEmulateDesktopEnvironmentDefaultsAndToggling() {
        val config = KromiumConfig()
        assertFalse(config.emulateDesktopEnvironment, "emulateDesktopEnvironment should be disabled by default")

        config.emulateDesktopEnvironment = true
        assertTrue(config.emulateDesktopEnvironment, "emulateDesktopEnvironment should be configurable to true")

        val built = KromiumConfig.builder()
            .emulateDesktopEnvironment(true)
            .build()
        assertTrue(built.emulateDesktopEnvironment, "Builder should properly configure emulateDesktopEnvironment")
    }

    @Test
    fun testSslErrorPolicyConfiguration() {
        val config = KromiumConfig()
        assertEquals(dev.daviante.kromium.domain.exception.SslErrorPolicy.Strict, config.sslErrorPolicy)

        val policy = dev.daviante.kromium.domain.exception.SslErrorPolicy.allowDomains("*.internal.corp", "localhost")
        config.sslErrorPolicy = policy
        assertEquals(policy, config.sslErrorPolicy)

        val built = KromiumConfig.builder()
            .sslErrorPolicy(policy)
            .build()
        assertEquals(policy, built.sslErrorPolicy)
    }
}
