package dev.daviante.kromium.presentation.menu

import dev.daviante.kromium.presentation.browser.KromiumBrowser
import dev.daviante.kromium.presentation.browser.KromiumClient
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandler
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KromiumContextMenuTest {

    @Test
    fun `KromiumContextMenuParams maps all JCEF params and evaluates predicates`() {
        val mockParams = mockk<CefContextMenuParams>()
        every { mockParams.xCoord } returns 150
        every { mockParams.yCoord } returns 300
        every { mockParams.linkUrl } returns "https://daviante.dev/docs"
        every { mockParams.unfilteredLinkUrl } returns "https://daviante.dev/docs?utm=1"
        every { mockParams.sourceUrl } returns "https://daviante.dev/logo.png"
        every { mockParams.hasImageContents() } returns true
        every { mockParams.pageUrl } returns "https://daviante.dev"
        every { mockParams.frameUrl } returns "https://daviante.dev/subframe"
        every { mockParams.selectionText } returns "Kromium Chromium Engine"
        every { mockParams.misspelledWord } returns "Kromium"
        every { mockParams.isEditable } returns true
        every { mockParams.isSpellCheckEnabled } returns true

        val params = KromiumContextMenuParams.from(mockParams)

        assertEquals(150, params.x)
        assertEquals(300, params.y)
        assertEquals("https://daviante.dev/docs", params.linkUrl)
        assertEquals("https://daviante.dev/docs?utm=1", params.unfilteredLinkUrl)
        assertEquals("https://daviante.dev/logo.png", params.sourceUrl)
        assertTrue(params.hasImage)
        assertEquals("https://daviante.dev", params.pageUrl)
        assertEquals("https://daviante.dev/subframe", params.frameUrl)
        assertEquals("Kromium Chromium Engine", params.selectionText)
        assertEquals("Kromium", params.misspelledWord)
        assertTrue(params.isEditable)
        assertTrue(params.isSpellCheckEnabled)
        assertTrue(params.hasSelection())
        assertTrue(params.isLink())
        assertTrue(params.hasMedia())

        // Test null params fallback
        val emptyParams = KromiumContextMenuParams.from(null)
        assertEquals(0, emptyParams.x)
        assertEquals(0, emptyParams.y)
        assertNull(emptyParams.linkUrl)
        assertFalse(emptyParams.hasSelection())
        assertFalse(emptyParams.isLink())
        assertFalse(emptyParams.hasMedia())
    }

    @Test
    fun `KromiumMenuBuilder registers items, checks, radios, separators, and submenus`() {
        val mockModel = mockk<CefMenuModel>(relaxed = true)
        val mockSubModel = mockk<CefMenuModel>(relaxed = true)
        every { mockModel.addSubMenu(any(), any()) } returns mockSubModel
        every { mockModel.count } returns 5

        val actionMap = mutableMapOf<Int, (KromiumContextMenuContext) -> Unit>()
        val mockBrowser = mockk<CefBrowser>(relaxed = true)
        val params = KromiumContextMenuParams.from(null)
        val context = KromiumContextMenuContext(null, mockBrowser, params, null)

        val builder = KromiumMenuBuilder(mockModel, context, actionMap)

        builder.clear()
        verify(exactly = 1) { mockModel.clear() }

        // Standard item
        var itemClicked = false
        builder.item("Action 1") { itemClicked = true }
        verify { mockModel.addItem(KromiumMenuBuilder.USER_COMMAND_FIRST, "Action 1") }
        verify { mockModel.setEnabled(KromiumMenuBuilder.USER_COMMAND_FIRST, true) }

        // Checkbox item
        var toggleValue = false
        builder.checkItem("Check 1", checked = false) { toggleValue = it }
        val checkCmdId = KromiumMenuBuilder.USER_COMMAND_FIRST + 1
        verify { mockModel.addCheckItem(checkCmdId, "Check 1") }
        verify { mockModel.setChecked(checkCmdId, false) }

        // Radio item
        var radioSelected = false
        builder.radioItem("Radio 1", checked = true, groupId = 42) { radioSelected = true }
        val radioCmdId = KromiumMenuBuilder.USER_COMMAND_FIRST + 2
        verify { mockModel.addRadioItem(radioCmdId, "Radio 1", 42) }
        verify { mockModel.setChecked(radioCmdId, true) }

        // Separator
        builder.separator()
        verify { mockModel.addSeparator() }

        // Submenu
        var subItemClicked = false
        builder.subMenu("More Tools") {
            item("Sub Action") { subItemClicked = true }
        }
        val subMenuCmdId = KromiumMenuBuilder.USER_COMMAND_FIRST + 3
        val subItemCmdId = KromiumMenuBuilder.USER_COMMAND_FIRST + 4
        verify { mockModel.addSubMenu(subMenuCmdId, "More Tools") }
        verify { mockSubModel.addItem(subItemCmdId, "Sub Action") }

        assertEquals(5, builder.count)

        // Execute callbacks from actionMap
        actionMap[KromiumMenuBuilder.USER_COMMAND_FIRST]?.invoke(context)
        assertTrue(itemClicked)

        actionMap[checkCmdId]?.invoke(context)
        assertTrue(toggleValue) // was false, toggled to true

        actionMap[radioCmdId]?.invoke(context)
        assertTrue(radioSelected)

        actionMap[subItemCmdId]?.invoke(context)
        assertTrue(subItemClicked)
    }

    @Test
    fun `KromiumMenuBuilder built-in shortcuts attach appropriate actions`() {
        val mockModel = mockk<CefMenuModel>(relaxed = true)
        val actionMap = mutableMapOf<Int, (KromiumContextMenuContext) -> Unit>()
        val mockBrowser = mockk<CefBrowser>(relaxed = true)

        val params = KromiumContextMenuParams(
            x = 200,
            y = 400,
            linkUrl = "https://daviante.dev/docs",
            unfilteredLinkUrl = null,
            sourceUrl = "https://daviante.dev/hero.jpg",
            hasImage = true,
            pageUrl = "https://daviante.dev",
            frameUrl = null,
            selectionText = "Selected text snippet",
            misspelledWord = null,
            isEditable = true,
            isSpellCheckEnabled = false
        )
        val context = KromiumContextMenuContext(null, mockBrowser, params, null)
        val builder = KromiumMenuBuilder(mockModel, context, actionMap)

        builder.inspectElement()
        builder.copyLink()
        builder.saveImageAs()
        builder.copyImageUrl()
        builder.searchWeb()

        // Inspect element action test
        val inspectCmdId = KromiumMenuBuilder.USER_COMMAND_FIRST
        actionMap[inspectCmdId]?.invoke(context)
        verify { mockBrowser.openDevTools(Point(200, 400)) }

        // Save Image As action test
        val saveImageCmdId = KromiumMenuBuilder.USER_COMMAND_FIRST + 2
        actionMap[saveImageCmdId]?.invoke(context)
        verify { mockBrowser.startDownload("https://daviante.dev/hero.jpg") }

        // Native command shortcuts
        builder.copy()
        builder.cut()
        builder.paste()
        builder.selectAll()
        builder.back()
        builder.forward()
        builder.reload()
        builder.print()
        builder.viewSource()

        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_COPY, "Copy") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_CUT, "Cut") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_PASTE, "Paste") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_SELECT_ALL, "Select All") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_BACK, "Back") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_FORWARD, "Forward") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_RELOAD, "Reload") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_PRINT, "Print...") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_VIEW_SOURCE, "View Page Source") }
    }

    @Test
    fun `KromiumContextMenuHandler presets configure expected menu state`() {
        val mockModel = mockk<CefMenuModel>(relaxed = true)
        val actionMap = mutableMapOf<Int, (KromiumContextMenuContext) -> Unit>()
        val context = KromiumContextMenuContext(null, null, KromiumContextMenuParams.from(null), null)

        // Disabled preset: clears model
        val disabledHandler = KromiumContextMenuHandler.disabled()
        val builder1 = KromiumMenuBuilder(mockModel, context, actionMap)
        disabledHandler.onBuildContextMenu(builder1, context)
        verify(exactly = 1) { mockModel.clear() }

        // DevTools only preset: clears model and adds inspectElement
        clearMocks(mockModel)
        val devToolsHandler = KromiumContextMenuHandler.devToolsOnly()
        val builder2 = KromiumMenuBuilder(mockModel, context, actionMap)
        devToolsHandler.onBuildContextMenu(builder2, context)
        verify(exactly = 1) { mockModel.clear() }
        verify { mockModel.addItem(any(), "Inspect Element") }

        // Minimal editing preset
        clearMocks(mockModel)
        val editableParams = KromiumContextMenuParams(
            x = 0, y = 0, linkUrl = null, unfilteredLinkUrl = null, sourceUrl = null,
            hasImage = false, pageUrl = null, frameUrl = null, selectionText = "test",
            misspelledWord = null, isEditable = true, isSpellCheckEnabled = false
        )
        val editableCtx = KromiumContextMenuContext(null, null, editableParams, null)
        val minimalHandler = KromiumContextMenuHandler.minimalEditing(includeInspectElement = true)
        val builder3 = KromiumMenuBuilder(mockModel, editableCtx, actionMap)
        minimalHandler.onBuildContextMenu(builder3, editableCtx)
        verify(exactly = 1) { mockModel.clear() }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_COPY, "Copy") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_CUT, "Cut") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_PASTE, "Paste") }
        verify { mockModel.addItem(CefMenuModel.MenuId.MENU_ID_SELECT_ALL, "Select All") }
        verify { mockModel.addItem(any(), "Inspect Element") }
    }

    @Test
    fun `KromiumClient integrates contextMenuHandler with custom action execution and lifecycle`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val contextHandlerSlot = slot<CefContextMenuHandler>()

        val client = KromiumClient(mockRawClient)
        verify { mockRawClient.addContextMenuHandler(capture(contextHandlerSlot)) }

        val handler = contextHandlerSlot.captured
        val mockModel = mockk<CefMenuModel>(relaxed = true)
        val mockBrowser = mockk<CefBrowser>(relaxed = true)

        var customActionInvoked = false
        client.setContextMenu {
            clear()
            item("Export to Excel") {
                customActionInvoked = true
            }
        }

        // Trigger onBeforeContextMenu
        handler.onBeforeContextMenu(mockBrowser, null, null, mockModel)
        verify { mockModel.clear() }
        verify { mockModel.addItem(KromiumMenuBuilder.USER_COMMAND_FIRST, "Export to Excel") }

        // Trigger onContextMenuCommand for custom action ID
        val handled = handler.onContextMenuCommand(
            mockBrowser, null, null, KromiumMenuBuilder.USER_COMMAND_FIRST, 0
        )
        assertTrue(handled)
        assertTrue(customActionInvoked)

        // Native command (not in custom actions map) should return false to let Chromium handle it
        val nativeHandled = handler.onContextMenuCommand(
            mockBrowser, null, null, CefMenuModel.MenuId.MENU_ID_BACK, 0
        )
        assertFalse(nativeHandled)

        // Dismiss context menu
        handler.onContextMenuDismissed(mockBrowser, null)

        // Subsequent click on previously registered custom action should now return false (cleaned up)
        customActionInvoked = false
        val afterDismissHandled = handler.onContextMenuCommand(
            mockBrowser, null, null, KromiumMenuBuilder.USER_COMMAND_FIRST, 0
        )
        assertFalse(afterDismissHandled)
        assertFalse(customActionInvoked)
    }

    @Test
    fun `KromiumClient respects enableContextMenus false`() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val contextHandlerSlot = slot<CefContextMenuHandler>()

        val client = KromiumClient(mockRawClient)
        client.enableContextMenus = false
        verify { mockRawClient.addContextMenuHandler(capture(contextHandlerSlot)) }

        val handler = contextHandlerSlot.captured
        val mockModel = mockk<CefMenuModel>(relaxed = true)

        handler.onBeforeContextMenu(null, null, null, mockModel)
        verify(exactly = 1) { mockModel.clear() }
    }
}
