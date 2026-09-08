package dev.daviante.kromium.presentation.chrome

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.awt.Component
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KromiumWindowChromeTest {

    @Test
    fun `KromiumChromeConfig defaults to disabled and builder overrides properties`() {
        val defaultConfig = KromiumChromeConfig()
        assertFalse(defaultConfig.enabled)
        assertEquals(76, defaultConfig.macTrafficLightsWidth)
        assertEquals(38, defaultConfig.macTrafficLightsHeight)
        assertTrue(defaultConfig.transparentTitleBar)
        assertTrue(defaultConfig.hideWindowTitle)

        val disabledConstant = KromiumChromeConfig.DISABLED
        assertFalse(disabledConstant.enabled)

        val customConfig = KromiumChromeConfig.builder()
            .enabled(true)
            .macTrafficLightsWidth(84)
            .macTrafficLightsHeight(42)
            .transparentTitleBar(false)
            .hideWindowTitle(false)
            .build()

        assertTrue(customConfig.enabled)
        assertEquals(84, customConfig.macTrafficLightsWidth)
        assertEquals(42, customConfig.macTrafficLightsHeight)
        assertFalse(customConfig.transparentTitleBar)
        assertFalse(customConfig.hideWindowTitle)
    }

    @Test
    fun `KromiumWindowChrome returns zero insets when disabled`() {
        assertEquals(0, KromiumWindowChrome.getMacTrafficLightsWidth(enabled = false))
        assertEquals(0, KromiumWindowChrome.getMacTrafficLightsWidth(enabled = false, customWidth = 100))
        assertEquals(0, KromiumWindowChrome.getMacTrafficLightsHeight(enabled = false))
        assertEquals(0, KromiumWindowChrome.getMacTrafficLightsHeight(enabled = false, customHeight = 50))
    }

    @Test
    fun `KromiumWindowChrome returns custom insets when enabled on macOS`() {
        if (KromiumWindowChrome.isMac()) {
            assertEquals(76, KromiumWindowChrome.getMacTrafficLightsWidth(enabled = true))
            assertEquals(90, KromiumWindowChrome.getMacTrafficLightsWidth(enabled = true, customWidth = 90))
            assertEquals(38, KromiumWindowChrome.getMacTrafficLightsHeight(enabled = true))
            assertEquals(45, KromiumWindowChrome.getMacTrafficLightsHeight(enabled = true, customHeight = 45))
        } else {
            assertEquals(0, KromiumWindowChrome.getMacTrafficLightsWidth(enabled = true))
            assertEquals(0, KromiumWindowChrome.getMacTrafficLightsHeight(enabled = true))
        }
    }

    @Test
    fun `apply does nothing when config is disabled`() {
        val mockRootPane = mockk<JRootPane>(relaxed = true)
        val mockFrame = mockk<javax.swing.JFrame>(relaxed = true)
        every { mockFrame.rootPane } returns mockRootPane

        KromiumWindowChrome.apply(mockFrame, KromiumChromeConfig(enabled = false))

        verify(exactly = 0) { mockRootPane.putClientProperty(any(), any()) }
    }

    @Test
    fun `configureFlatLafTitleBar applies properties only when enabled`() {
        val mockRootPane = mockk<JRootPane>(relaxed = true)
        val mockFrame = mockk<javax.swing.JFrame>(relaxed = true)
        every { mockFrame.rootPane } returns mockRootPane

        // Disabled -> no properties set
        KromiumWindowChrome.configureFlatLafTitleBar(mockFrame, enabled = false)
        verify(exactly = 0) { mockRootPane.putClientProperty(any(), any()) }

        // Enabled -> properties set
        KromiumWindowChrome.configureFlatLafTitleBar(
            window = mockFrame,
            enabled = true,
            showTitle = false,
            showIcon = false
        )
        verify { mockRootPane.putClientProperty("JRootPane.titleBarShowTitle", false) }
        verify { mockRootPane.putClientProperty("JRootPane.titleBarShowIcon", false) }
    }

    @Test
    fun `installWindowDragger attaches mouse and mouse motion listeners`() {
        val mockComponent = mockk<Component>(relaxed = true)
        val mockWindow = mockk<java.awt.Window>(relaxed = true)

        KromiumWindowChrome.installWindowDragger(mockComponent, mockWindow)

        verify(exactly = 1) { mockComponent.addMouseListener(any<MouseListener>()) }
        verify(exactly = 1) { mockComponent.addMouseMotionListener(any<MouseMotionListener>()) }
    }
}
