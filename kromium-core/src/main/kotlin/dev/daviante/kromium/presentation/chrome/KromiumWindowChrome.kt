package dev.daviante.kromium.presentation.chrome

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.util.PlatformDetector
import dev.daviante.kromium.domain.model.OperatingSystem
import java.awt.Component
import java.awt.Frame
import java.awt.Point
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * Lightweight, unopinionated window chrome helper for host applications integrating custom
 * tab strips with native OS titlebars and macOS traffic lights.
 *
 * All features are flag-enabled and default to disabled, ensuring zero intrusion or modification
 * to standard window decorations unless explicitly enabled by the developer.
 */
object KromiumWindowChrome {

    private const val TAG = "KromiumWindowChrome"

    /** Standard reference width in points/pixels for native macOS traffic lights (Close, Minimize, Zoom). */
    const val DEFAULT_MAC_TRAFFIC_LIGHTS_WIDTH: Int = 76

    /** Standard reference height in points/pixels for native macOS traffic lights. */
    const val DEFAULT_MAC_TRAFFIC_LIGHTS_HEIGHT: Int = 38

    /**
     * Checks if the current operating environment is macOS.
     */
    @JvmStatic
    fun isMac(): Boolean = try {
        PlatformDetector.current().os == OperatingSystem.MacOS
    } catch (_: Throwable) {
        val osName = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: ""
        osName.contains("mac") || osName.contains("darwin")
    }

    /**
     * Resolves the left inset allocated for macOS traffic lights.
     *
     * Returns [customWidth] if [enabled] is `true` and the platform is macOS; returns `0` otherwise.
     *
     * @param enabled Whether custom window chrome is enabled. Defaults to true.
     * @param customWidth The desired width to allocate if enabled. Defaults to [DEFAULT_MAC_TRAFFIC_LIGHTS_WIDTH].
     */
    @JvmStatic
    @JvmOverloads
    fun getMacTrafficLightsWidth(
        enabled: Boolean = true,
        customWidth: Int = DEFAULT_MAC_TRAFFIC_LIGHTS_WIDTH
    ): Int {
        return if (enabled && isMac()) customWidth else 0
    }

    /**
     * Resolves the height allocated for macOS traffic lights.
     *
     * Returns [customHeight] if [enabled] is `true` and the platform is macOS; returns `0` otherwise.
     *
     * @param enabled Whether custom window chrome is enabled. Defaults to true.
     * @param customHeight The desired height to allocate if enabled. Defaults to [DEFAULT_MAC_TRAFFIC_LIGHTS_HEIGHT].
     */
    @JvmStatic
    @JvmOverloads
    fun getMacTrafficLightsHeight(
        enabled: Boolean = true,
        customHeight: Int = DEFAULT_MAC_TRAFFIC_LIGHTS_HEIGHT
    ): Int {
        return if (enabled && isMac()) customHeight else 0
    }

    /**
     * Applies the specified [KromiumChromeConfig] to the given AWT/Swing [Window].
     *
     * If [KromiumChromeConfig.enabled] is `false`, this method is a complete no-op and leaves
     * the window's existing styling 100% untouched.
     */
    @JvmStatic
    fun apply(window: Window, config: KromiumChromeConfig) {
        if (!config.enabled) return
        if (isMac()) {
            applyMacFullWindowContent(
                window = window,
                enabled = true,
                transparentTitleBar = config.transparentTitleBar,
                hideWindowTitle = config.hideWindowTitle
            )
        }
        configureFlatLafTitleBar(
            window = window,
            enabled = true,
            showTitle = !config.hideWindowTitle,
            showIcon = !config.hideWindowTitle
        )
    }

    /**
     * Configures native macOS Apple AWT client properties so custom components (such as browser tab strips)
     * can merge into the titlebar area behind the native OS traffic lights.
     *
     * @param window Target [Window] (typically a [javax.swing.JFrame] or [javax.swing.JDialog]).
     * @param enabled Whether to enable or restore standard window content layout. Defaults to true.
     * @param transparentTitleBar If true, makes the macOS titlebar background transparent.
     * @param hideWindowTitle If true, hides the default OS title text.
     */
    @JvmStatic
    @JvmOverloads
    fun applyMacFullWindowContent(
        window: Window,
        enabled: Boolean = true,
        transparentTitleBar: Boolean = true,
        hideWindowTitle: Boolean = true
    ) {
        if (!isMac()) return
        val rootPane = (window as? RootPaneContainer)?.rootPane ?: return

        try {
            if (enabled) {
                rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                rootPane.putClientProperty("apple.awt.transparentTitleBar", transparentTitleBar)
                rootPane.putClientProperty("apple.awt.windowTitleVisible", !hideWindowTitle)
                KromiumLogger.i(TAG, "Applied macOS full window content styling")
            } else {
                rootPane.putClientProperty("apple.awt.fullWindowContent", false)
                rootPane.putClientProperty("apple.awt.transparentTitleBar", false)
                rootPane.putClientProperty("apple.awt.windowTitleVisible", true)
                KromiumLogger.i(TAG, "Restored standard macOS window content styling")
            }
        } catch (t: Throwable) {
            KromiumLogger.w(TAG, "Failed to apply macOS window client properties", t)
        }
    }

    /**
     * Safely applies FlatLaf titlebar client properties to a Swing window's root pane without introducing
     * a hard compile-time dependency on FlatLaf.
     *
     * @param window Target [Window] instance.
     * @param enabled Whether to configure FlatLaf titlebar properties. Defaults to true.
     * @param showTitle Whether FlatLaf should render the window title text in the titlebar.
     * @param showIcon Whether FlatLaf should render the window icon in the titlebar.
     */
    @JvmStatic
    @JvmOverloads
    fun configureFlatLafTitleBar(
        window: Window,
        enabled: Boolean = true,
        showTitle: Boolean = false,
        showIcon: Boolean = false
    ) {
        if (!enabled) return
        val rootPane = (window as? RootPaneContainer)?.rootPane ?: return

        try {
            rootPane.putClientProperty("JRootPane.titleBarShowTitle", showTitle)
            rootPane.putClientProperty("JRootPane.titleBarShowIcon", showIcon)
            KromiumLogger.d(TAG, "Configured FlatLaf titlebar client properties (title=$showTitle, icon=$showIcon)")
        } catch (t: Throwable) {
            KromiumLogger.w(TAG, "Failed to configure FlatLaf client properties", t)
        }
    }

    /**
     * Attaches mouse drag and double-click listeners to a Swing [Component] (such as an empty tab bar area),
     * enabling users to drag the window and double-click to toggle between maximized and restored states.
     *
     * @param component The UI component to attach mouse gesture listeners to.
     * @param window The parent [Window] to move or maximize.
     */
    @JvmStatic
    fun installWindowDragger(component: Component, window: Window) {
        val listener = object : MouseAdapter() {
            private var initialClickPoint: Point? = null

            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    initialClickPoint = e.point
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                initialClickPoint = null
            }

            override fun mouseDragged(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) return
                val initial = initialClickPoint ?: return

                if (window is Frame && (window.extendedState and Frame.MAXIMIZED_BOTH) != 0) {
                    return // Do not drag while in maximized state
                }

                try {
                    val currentScreen = e.locationOnScreen
                    window.setLocation(currentScreen.x - initial.x, currentScreen.y - initial.y)
                } catch (t: Throwable) {
                    KromiumLogger.w(TAG, "Error moving window during drag", t)
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                    if (window is Frame) {
                        val isMaximized = (window.extendedState and Frame.MAXIMIZED_BOTH) != 0
                        window.extendedState = if (isMaximized) Frame.NORMAL else Frame.MAXIMIZED_BOTH
                        KromiumLogger.d(TAG, "Toggled window maximize state to: ${!isMaximized}")
                    }
                }
            }
        }

        component.addMouseListener(listener)
        component.addMouseMotionListener(listener)
    }
}
