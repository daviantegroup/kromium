package dev.daviante.kromium.presentation.keyboard

import dev.daviante.kromium.core.util.PlatformDetector
import dev.daviante.kromium.domain.model.OperatingSystem
import dev.daviante.kromium.presentation.browser.KromiumBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefKeyboardHandler
import org.cef.misc.EventFlags
import java.awt.event.KeyEvent

/**
 * Centralized cross-platform browser shortcut engine for Kromium.
 * Normalizes and executes standard browser actions (clipboard, history, zoom, reload)
 * across OSR mode, Windowed JCEF mode, and Compose Desktop on both macOS (Command)
 * and Windows/Linux (Control).
 */
object KromiumShortcutHandler {

    val isMac: Boolean
        get() = try {
            PlatformDetector.current().os == OperatingSystem.MacOS
        } catch (_: Throwable) {
            System.getProperty("os.name", "").lowercase().contains("mac")
        }

    /**
     * Handles an AWT [KeyEvent] dispatched from Swing/AWT or Compose Desktop.
     * Returns true if the key event was intercepted and handled as a browser shortcut.
     */
    @JvmStatic
    @JvmOverloads
    fun handleKeyEvent(
        browser: KromiumBrowser,
        event: KeyEvent,
        isMacOverride: Boolean? = null
    ): Boolean {
        return handleAwtKeyEvent(browser.rawBrowser, event, isMacOverride)
    }

    /**
     * Handles an AWT [KeyEvent] on a raw [CefBrowser] instance.
     */
    @JvmStatic
    @JvmOverloads
    fun handleAwtKeyEvent(
        browser: CefBrowser,
        event: KeyEvent,
        isMacOverride: Boolean? = null
    ): Boolean {
        val mac = isMacOverride ?: isMac

        // Modifiers check
        val isShortcutModifier = if (mac) {
            event.isMetaDown
        } else {
            event.isControlDown && !event.isAltDown
        }

        // On key typed or released with shortcut modifier active, consume to prevent unwanted characters
        if (isShortcutModifier && (event.id == KeyEvent.KEY_TYPED || event.id == KeyEvent.KEY_RELEASED)) {
            event.consume()
            return true
        }

        if (event.id != KeyEvent.KEY_PRESSED) {
            return false
        }

        val frame = browser.focusedFrame ?: browser.mainFrame
        val keyCode = event.keyCode

        // Standalone shortcuts (without primary modifier) on Windows/Linux
        if (!mac && !isShortcutModifier) {
            // F5 / Shift+F5
            if (keyCode == KeyEvent.VK_F5) {
                if (event.isShiftDown || event.isControlDown) {
                    browser.reloadIgnoreCache()
                } else {
                    browser.reload()
                }
                event.consume()
                return true
            }
            // Alt+Left / Alt+Right for history navigation
            if (event.isAltDown && !event.isControlDown) {
                if (keyCode == KeyEvent.VK_LEFT && browser.canGoBack()) {
                    browser.goBack()
                    event.consume()
                    return true
                }
                if (keyCode == KeyEvent.VK_RIGHT && browser.canGoForward()) {
                    browser.goForward()
                    event.consume()
                    return true
                }
            }
            return false
        }

        if (!isShortcutModifier) {
            return false
        }

        val isShift = event.isShiftDown

        when (keyCode) {
            KeyEvent.VK_C -> {
                frame?.copy()
                event.consume()
                return true
            }
            KeyEvent.VK_INSERT -> {
                if (!mac && event.isControlDown) {
                    frame?.copy()
                    event.consume()
                    return true
                }
            }
            KeyEvent.VK_V -> {
                frame?.paste()
                event.consume()
                return true
            }
            KeyEvent.VK_X -> {
                frame?.cut()
                event.consume()
                return true
            }
            KeyEvent.VK_A -> {
                frame?.selectAll()
                event.consume()
                return true
            }
            KeyEvent.VK_Z -> {
                if (isShift) {
                    frame?.redo()
                } else {
                    frame?.undo()
                }
                event.consume()
                return true
            }
            KeyEvent.VK_Y -> {
                frame?.redo()
                event.consume()
                return true
            }
            KeyEvent.VK_R -> {
                if (isShift) {
                    browser.reloadIgnoreCache()
                } else {
                    browser.reload()
                }
                event.consume()
                return true
            }
            KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD, 187 -> {
                browser.zoomLevel = browser.zoomLevel + 0.25
                event.consume()
                return true
            }
            KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT, 189 -> {
                browser.zoomLevel = browser.zoomLevel - 0.25
                event.consume()
                return true
            }
            KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> {
                browser.zoomLevel = 0.0
                event.consume()
                return true
            }
            KeyEvent.VK_LEFT, KeyEvent.VK_OPEN_BRACKET, 219 -> {
                if (mac && browser.canGoBack()) {
                    browser.goBack()
                    event.consume()
                    return true
                }
            }
            KeyEvent.VK_RIGHT, KeyEvent.VK_CLOSE_BRACKET, 221 -> {
                if (mac && browser.canGoForward()) {
                    browser.goForward()
                    event.consume()
                    return true
                }
            }
        }
        return false
    }

    /**
     * Handles a CEF [CefKeyboardHandler.CefKeyEvent] intercepted during [CefKeyboardHandler.onPreKeyEvent].
     */
    @JvmStatic
    @JvmOverloads
    fun handleCefKeyEvent(
        browser: CefBrowser,
        event: CefKeyboardHandler.CefKeyEvent,
        isMacOverride: Boolean? = null
    ): Boolean {
        val mac = isMacOverride ?: isMac

        val isCmd = (event.modifiers and EventFlags.EVENTFLAG_COMMAND_DOWN) != 0
        val isCtrl = (event.modifiers and EventFlags.EVENTFLAG_CONTROL_DOWN) != 0
        val isAlt = (event.modifiers and EventFlags.EVENTFLAG_ALT_DOWN) != 0
        val isShift = (event.modifiers and EventFlags.EVENTFLAG_SHIFT_DOWN) != 0

        val isShortcutModifier = if (mac) isCmd else ((isCtrl && !isAlt) || isCmd)

        val isDown = event.type == CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN ||
            event.type == CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_KEYDOWN

        if (!isDown) {
            return isShortcutModifier
        }

        val frame = browser.focusedFrame ?: browser.mainFrame
        val keyCode = event.windows_key_code

        // Standalone shortcuts on Windows/Linux without Ctrl
        if (!mac && !isShortcutModifier) {
            if (keyCode == KeyEvent.VK_F5) {
                if (isShift || isCtrl) {
                    browser.reloadIgnoreCache()
                } else {
                    browser.reload()
                }
                return true
            }
            if (isAlt && !isCtrl) {
                if (keyCode == KeyEvent.VK_LEFT && browser.canGoBack()) {
                    browser.goBack()
                    return true
                }
                if (keyCode == KeyEvent.VK_RIGHT && browser.canGoForward()) {
                    browser.goForward()
                    return true
                }
            }
            return false
        }

        if (!isShortcutModifier) return false

        when (keyCode) {
            KeyEvent.VK_C -> {
                frame?.copy()
                return true
            }
            KeyEvent.VK_INSERT -> {
                if (!mac && isCtrl) {
                    frame?.copy()
                    return true
                }
            }
            KeyEvent.VK_V -> {
                frame?.paste()
                return true
            }
            KeyEvent.VK_X -> {
                frame?.cut()
                return true
            }
            KeyEvent.VK_A -> {
                frame?.selectAll()
                return true
            }
            KeyEvent.VK_Z -> {
                if (isShift) {
                    frame?.redo()
                } else {
                    frame?.undo()
                }
                return true
            }
            KeyEvent.VK_Y -> {
                frame?.redo()
                return true
            }
            KeyEvent.VK_R -> {
                if (isShift) {
                    browser.reloadIgnoreCache()
                } else {
                    browser.reload()
                }
                return true
            }
            KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD, 187 -> {
                browser.zoomLevel = browser.zoomLevel + 0.25
                return true
            }
            KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT, 189 -> {
                browser.zoomLevel = browser.zoomLevel - 0.25
                return true
            }
            KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> {
                browser.zoomLevel = 0.0
                return true
            }
            KeyEvent.VK_LEFT, KeyEvent.VK_OPEN_BRACKET, 219 -> {
                if (mac && browser.canGoBack()) {
                    browser.goBack()
                    return true
                }
            }
            KeyEvent.VK_RIGHT, KeyEvent.VK_CLOSE_BRACKET, 221 -> {
                if (mac && browser.canGoForward()) {
                    browser.goForward()
                    return true
                }
            }
        }
        return false
    }
}
