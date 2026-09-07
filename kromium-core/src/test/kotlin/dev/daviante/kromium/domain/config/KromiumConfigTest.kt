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
}
