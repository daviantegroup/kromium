package dev.daviante.kromium.presentation.keyboard

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefKeyboardHandler
import org.cef.misc.EventFlags
import java.awt.Component
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KromiumShortcutHandlerTest {

    private val dummyComponent = object : Component() {}

    private fun createAwtKeyEvent(
        keyCode: Int,
        modifiers: Int = 0,
        id: Int = KeyEvent.KEY_PRESSED,
        keyChar: Char = KeyEvent.CHAR_UNDEFINED
    ): KeyEvent {
        return KeyEvent(dummyComponent, id, System.currentTimeMillis(), modifiers, keyCode, keyChar)
    }

    private fun createCefKeyEvent(
        keyCode: Int,
        modifiers: Int = 0,
        type: CefKeyboardHandler.CefKeyEvent.EventType = CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN
    ): CefKeyboardHandler.CefKeyEvent {
        return CefKeyboardHandler.CefKeyEvent(type, modifiers, keyCode, 0, false, 'c', 'c', false)
    }

    @Test
    fun testMacAwtCommandShortcuts() {
        val mockBrowser = mockk<CefBrowser>(relaxed = true)
        val mockFrame = mockk<CefFrame>(relaxed = true)
        every { mockBrowser.focusedFrame } returns mockFrame

        // 1. Cmd + C -> copy()
        val copyEvent = createAwtKeyEvent(KeyEvent.VK_C, modifiers = KeyEvent.META_DOWN_MASK)
        val copyHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, copyEvent, isMacOverride = true)
        assertTrue(copyHandled)
        assertTrue(copyEvent.isConsumed)
        verify(exactly = 1) { mockFrame.copy() }

        // 2. Cmd + V -> paste()
        val pasteEvent = createAwtKeyEvent(KeyEvent.VK_V, modifiers = KeyEvent.META_DOWN_MASK)
        val pasteHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, pasteEvent, isMacOverride = true)
        assertTrue(pasteHandled)
        assertTrue(pasteEvent.isConsumed)
        verify(exactly = 1) { mockFrame.paste() }

        // 3. Cmd + X -> cut()
        val cutEvent = createAwtKeyEvent(KeyEvent.VK_X, modifiers = KeyEvent.META_DOWN_MASK)
        val cutHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, cutEvent, isMacOverride = true)
        assertTrue(cutHandled)
        assertTrue(cutEvent.isConsumed)
        verify(exactly = 1) { mockFrame.cut() }

        // 4. Cmd + A -> selectAll()
        val selectAllEvent = createAwtKeyEvent(KeyEvent.VK_A, modifiers = KeyEvent.META_DOWN_MASK)
        val selectAllHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, selectAllEvent, isMacOverride = true)
        assertTrue(selectAllHandled)
        assertTrue(selectAllEvent.isConsumed)
        verify(exactly = 1) { mockFrame.selectAll() }

        // 5. Cmd + Z -> undo()
        val undoEvent = createAwtKeyEvent(KeyEvent.VK_Z, modifiers = KeyEvent.META_DOWN_MASK)
        val undoHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, undoEvent, isMacOverride = true)
        assertTrue(undoHandled)
        assertTrue(undoEvent.isConsumed)
        verify(exactly = 1) { mockFrame.undo() }

        // 6. Cmd + Shift + Z -> redo()
        val redoEvent = createAwtKeyEvent(KeyEvent.VK_Z, modifiers = KeyEvent.META_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK)
        val redoHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, redoEvent, isMacOverride = true)
        assertTrue(redoHandled)
        assertTrue(redoEvent.isConsumed)
        verify(exactly = 1) { mockFrame.redo() }

        // 7. Cmd + R -> reload()
        val reloadEvent = createAwtKeyEvent(KeyEvent.VK_R, modifiers = KeyEvent.META_DOWN_MASK)
        val reloadHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, reloadEvent, isMacOverride = true)
        assertTrue(reloadHandled)
        assertTrue(reloadEvent.isConsumed)
        verify(exactly = 1) { mockBrowser.reload() }

        // 8. Cmd + Shift + R -> reloadIgnoreCache()
        val hardReloadEvent = createAwtKeyEvent(KeyEvent.VK_R, modifiers = KeyEvent.META_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK)
        val hardReloadHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, hardReloadEvent, isMacOverride = true)
        assertTrue(hardReloadHandled)
        assertTrue(hardReloadEvent.isConsumed)
        verify(exactly = 1) { mockBrowser.reloadIgnoreCache() }

        // 9. Cmd + [ -> goBack()
        every { mockBrowser.canGoBack() } returns true
        val backEvent = createAwtKeyEvent(KeyEvent.VK_OPEN_BRACKET, modifiers = KeyEvent.META_DOWN_MASK)
        val backHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, backEvent, isMacOverride = true)
        assertTrue(backHandled)
        assertTrue(backEvent.isConsumed)
        verify(exactly = 1) { mockBrowser.goBack() }

        // 10. Cmd + ] -> goForward()
        every { mockBrowser.canGoForward() } returns true
        val forwardEvent = createAwtKeyEvent(KeyEvent.VK_CLOSE_BRACKET, modifiers = KeyEvent.META_DOWN_MASK)
        val forwardHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, forwardEvent, isMacOverride = true)
        assertTrue(forwardHandled)
        assertTrue(forwardEvent.isConsumed)
        verify(exactly = 1) { mockBrowser.goForward() }
    }

    @Test
    fun testWindowsLinuxAwtShortcuts() {
        val mockBrowser = mockk<CefBrowser>(relaxed = true)
        val mockFrame = mockk<CefFrame>(relaxed = true)
        every { mockBrowser.focusedFrame } returns mockFrame

        // 1. Ctrl + C -> copy()
        val copyEvent = createAwtKeyEvent(KeyEvent.VK_C, modifiers = KeyEvent.CTRL_DOWN_MASK)
        val copyHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, copyEvent, isMacOverride = false)
        assertTrue(copyHandled)
        assertTrue(copyEvent.isConsumed)
        verify(exactly = 1) { mockFrame.copy() }

        // 2. Ctrl + V -> paste()
        val pasteEvent = createAwtKeyEvent(KeyEvent.VK_V, modifiers = KeyEvent.CTRL_DOWN_MASK)
        val pasteHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, pasteEvent, isMacOverride = false)
        assertTrue(pasteHandled)
        assertTrue(pasteEvent.isConsumed)
        verify(exactly = 1) { mockFrame.paste() }

        // 3. F5 -> reload()
        val f5Event = createAwtKeyEvent(KeyEvent.VK_F5)
        val f5Handled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, f5Event, isMacOverride = false)
        assertTrue(f5Handled)
        assertTrue(f5Event.isConsumed)
        verify(exactly = 1) { mockBrowser.reload() }

        // 4. Shift + F5 -> reloadIgnoreCache()
        val shiftF5Event = createAwtKeyEvent(KeyEvent.VK_F5, modifiers = KeyEvent.SHIFT_DOWN_MASK)
        val shiftF5Handled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, shiftF5Event, isMacOverride = false)
        assertTrue(shiftF5Handled)
        assertTrue(shiftF5Event.isConsumed)
        verify(exactly = 1) { mockBrowser.reloadIgnoreCache() }

        // 5. Alt + Left -> goBack()
        every { mockBrowser.canGoBack() } returns true
        val altLeftEvent = createAwtKeyEvent(KeyEvent.VK_LEFT, modifiers = KeyEvent.ALT_DOWN_MASK)
        val altLeftHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, altLeftEvent, isMacOverride = false)
        assertTrue(altLeftHandled)
        assertTrue(altLeftEvent.isConsumed)
        verify(exactly = 1) { mockBrowser.goBack() }

        // 6. Regular key without Ctrl/Alt should NOT be intercepted
        val regularKey = createAwtKeyEvent(KeyEvent.VK_A)
        val regularHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, regularKey, isMacOverride = false)
        assertFalse(regularHandled)
        assertFalse(regularKey.isConsumed)
    }

    @Test
    fun testZoomShortcuts() {
        val mockBrowser = mockk<CefBrowser>(relaxed = true)
        every { mockBrowser.zoomLevel } returns 0.0

        // Zoom In
        val zoomInEvent = createAwtKeyEvent(KeyEvent.VK_EQUALS, modifiers = KeyEvent.META_DOWN_MASK)
        val zoomInHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, zoomInEvent, isMacOverride = true)
        assertTrue(zoomInHandled)
        verify(exactly = 1) { mockBrowser.zoomLevel = 0.25 }

        // Zoom Reset
        val zoomResetEvent = createAwtKeyEvent(KeyEvent.VK_0, modifiers = KeyEvent.META_DOWN_MASK)
        val zoomResetHandled = KromiumShortcutHandler.handleAwtKeyEvent(mockBrowser, zoomResetEvent, isMacOverride = true)
        assertTrue(zoomResetHandled)
        verify(exactly = 1) { mockBrowser.zoomLevel = 0.0 }
    }

    @Test
    fun testCefKeyEventDispatch() {
        val mockBrowser = mockk<CefBrowser>(relaxed = true)
        val mockFrame = mockk<CefFrame>(relaxed = true)
        every { mockBrowser.focusedFrame } returns mockFrame

        val cefCopy = createCefKeyEvent(
            KeyEvent.VK_C,
            modifiers = EventFlags.EVENTFLAG_COMMAND_DOWN
        )
        val handled = KromiumShortcutHandler.handleCefKeyEvent(mockBrowser, cefCopy, isMacOverride = true)
        assertTrue(handled)
        verify(exactly = 1) { mockFrame.copy() }

        val cefRegular = createCefKeyEvent(KeyEvent.VK_C, modifiers = 0)
        val regularHandled = KromiumShortcutHandler.handleCefKeyEvent(mockBrowser, cefRegular, isMacOverride = true)
        assertFalse(regularHandled)
    }
}
