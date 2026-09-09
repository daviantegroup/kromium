package dev.daviante.kromium.presentation.automation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class KromiumEmulationTest {

    @Test
    fun `emulation script contains all standard desktop environment normalization patches`() {
        val script = KromiumEmulation.SCRIPT

        // 1. Webdriver property normalization
        assertTrue(script.contains("Navigator.prototype.webdriver"))
        assertTrue(script.contains("navigator.webdriver"))
        assertTrue(script.contains("return undefined;"))

        // 2. Standard window.chrome runtime
        assertTrue(script.contains("chrome"))
        assertTrue(script.contains("chrome.runtime"))
        assertTrue(script.contains("chrome.loadTimes"))
        assertTrue(script.contains("chrome.csi"))

        // 3. Realistic language emulation
        assertTrue(script.contains("navigator.languages"))
        assertTrue(script.contains("['en-US', 'en']"))

        // 4. Desktop plugins array with PluginArray tagging
        assertTrue(script.contains("navigator.plugins"))
        assertTrue(script.contains("PDF Viewer"))
        assertTrue(script.contains("Chrome PDF Viewer"))
        assertTrue(script.contains("PluginArray"))

        // 5. Notification permissions query
        assertTrue(script.contains("navigator.permissions.query"))
        assertTrue(script.contains("Notification.permission"))

        // 6. WebGL vendor and renderer standardization
        assertTrue(script.contains("UNMASKED_VENDOR_WEBGL") || script.contains("37445"))
        assertTrue(script.contains("UNMASKED_RENDERER_WEBGL") || script.contains("37446"))
        assertTrue(script.contains("Intel Inc."))
        assertTrue(script.contains("Intel(R) Iris(TM) Plus Graphics 640"))

        // 7. Native toString masking
        assertTrue(script.contains("[native code]"))
        assertTrue(script.contains("makeNative"))

        // 8. Dynamic iframe protection
        assertTrue(script.contains("HTMLIFrameElement.prototype"))
        assertTrue(script.contains("contentWindow"))

        // 9. Zero global variable pollution on window
        assertFalse(script.contains("window.__kromium_stealth_injected"))
    }
}
