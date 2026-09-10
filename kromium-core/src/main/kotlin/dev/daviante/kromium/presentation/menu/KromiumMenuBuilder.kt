package dev.daviante.kromium.presentation.menu

import org.cef.callback.CefMenuModel
import java.util.concurrent.atomic.AtomicInteger

/**
 * Declarative builder for configuring browser right-click context menus.
 *
 * Supports Kotlin DSL blocks and pure Java fluent builder chaining, complete with built-in shortcuts
 * for common browser actions (Inspect Element, Copy Link, Web Search, Save Image).
 */
class KromiumMenuBuilder internal constructor(
    private val model: CefMenuModel,
    private val nextCommandId: () -> Int,
    private val actionMap: MutableMap<Int, (KromiumContextMenuContext) -> Unit>,
    val context: KromiumContextMenuContext
) {
    constructor(
        model: CefMenuModel,
        context: KromiumContextMenuContext,
        actionMap: MutableMap<Int, (KromiumContextMenuContext) -> Unit>
    ) : this(
        model = model,
        nextCommandId = createIdGenerator(),
        actionMap = actionMap,
        context = context
    )

    /**
     * Clears all existing default Chromium menu items from the context menu.
     */
    fun clear(): KromiumMenuBuilder = apply {
        model.clear()
    }

    /**
     * Clears all existing default Chromium menu items (alias for Java).
     */
    fun clearDefaults(): KromiumMenuBuilder = clear()

    /**
     * Removes a specific menu item by its integer command ID (e.g. [CefMenuModel.MenuId.MENU_ID_VIEW_SOURCE]).
     */
    fun removeDefault(menuId: Int): KromiumMenuBuilder = apply {
        model.remove(menuId)
    }

    /**
     * Number of items currently present in the menu.
     */
    val count: Int
        get() = model.count

    /**
     * Adds a standard clickable context menu item with an action callback.
     */
    fun addItem(
        label: String,
        enabled: Boolean = true,
        action: (KromiumContextMenuContext) -> Unit
    ): KromiumMenuBuilder = apply {
        val cmdId = nextCommandId()
        actionMap[cmdId] = action
        model.addItem(cmdId, label)
        model.setEnabled(cmdId, enabled)
    }

    /**
     * Kotlin DSL alias for [addItem].
     */
    fun item(
        label: String,
        enabled: Boolean = true,
        action: (KromiumContextMenuContext) -> Unit
    ): KromiumMenuBuilder = addItem(label, enabled, action)

    /**
     * Pure Java consumer overload for [addItem].
     */
    fun addItem(
        label: String,
        action: java.util.function.Consumer<KromiumContextMenuContext>
    ): KromiumMenuBuilder = addItem(label, true) { ctx -> action.accept(ctx) }

    /**
     * Adds a checkable (checkbox) menu item.
     */
    fun addCheckItem(
        label: String,
        checked: Boolean,
        enabled: Boolean = true,
        onToggle: (Boolean) -> Unit
    ): KromiumMenuBuilder = apply {
        val cmdId = nextCommandId()
        actionMap[cmdId] = {
            onToggle(!checked)
        }
        model.addCheckItem(cmdId, label)
        model.setChecked(cmdId, checked)
        model.setEnabled(cmdId, enabled)
    }

    /**
     * Kotlin DSL alias for [addCheckItem].
     */
    fun checkItem(
        label: String,
        checked: Boolean,
        enabled: Boolean = true,
        onToggle: (Boolean) -> Unit
    ): KromiumMenuBuilder = addCheckItem(label, checked, enabled, onToggle)

    /**
     * Adds a radio menu item belonging to a mutual-exclusion group.
     */
    fun addRadioItem(
        label: String,
        checked: Boolean,
        groupId: Int,
        enabled: Boolean = true,
        onSelect: () -> Unit
    ): KromiumMenuBuilder = apply {
        val cmdId = nextCommandId()
        actionMap[cmdId] = { onSelect() }
        model.addRadioItem(cmdId, label, groupId)
        model.setChecked(cmdId, checked)
        model.setEnabled(cmdId, enabled)
    }

    /**
     * Kotlin DSL alias for [addRadioItem].
     */
    fun radioItem(
        label: String,
        checked: Boolean,
        groupId: Int,
        enabled: Boolean = true,
        onSelect: () -> Unit
    ): KromiumMenuBuilder = addRadioItem(label, checked, groupId, enabled, onSelect)

    /**
     * Adds a visual separator line between menu items.
     */
    fun addSeparator(): KromiumMenuBuilder = apply {
        model.addSeparator()
    }

    /**
     * Kotlin DSL alias for [addSeparator].
     */
    fun separator(): KromiumMenuBuilder = addSeparator()

    /**
     * Adds a nested submenu with its own child builder.
     */
    fun addSubMenu(label: String, block: (KromiumMenuBuilder) -> Unit): KromiumMenuBuilder = apply {
        val cmdId = nextCommandId()
        val subModel = model.addSubMenu(cmdId, label)
        val subBuilder = KromiumMenuBuilder(
            model = subModel,
            nextCommandId = nextCommandId,
            actionMap = actionMap,
            context = context
        )
        block(subBuilder)
    }

    /**
     * Kotlin DSL alias for [addSubMenu].
     */
    fun subMenu(label: String, block: KromiumMenuBuilder.() -> Unit): KromiumMenuBuilder =
        addSubMenu(label) { builder -> builder.block() }

    // ==========================================
    // Turnkey Built-In Browser Action Shortcuts
    // ==========================================

    /**
     * Turnkey action: Opens Chromium DevTools targeting the clicked element coordinates.
     */
    @JvmOverloads
    fun inspectElement(label: String = "Inspect Element"): KromiumMenuBuilder =
        addItem(label) { ctx -> ctx.inspectElement() }

    /**
     * Turnkey action: Copies the target link URL to clipboard if the right-click occurred on a hyperlink.
     */
    @JvmOverloads
    fun copyLink(label: String = "Copy Link Address"): KromiumMenuBuilder = apply {
        if (context.params.isLink()) {
            val url = context.params.linkUrl ?: ""
            addItem(label) { ctx -> ctx.copyToClipboard(url) }
        }
    }

    /**
     * Turnkey action: Triggers browser download for image/media under cursor if present.
     */
    @JvmOverloads
    fun saveImageAs(label: String = "Save Image As..."): KromiumMenuBuilder = apply {
        if (context.params.hasMedia()) {
            val src = context.params.sourceUrl ?: ""
            if (src.isNotBlank()) {
                addItem(label) { ctx -> ctx.startDownload(src) }
            }
        }
    }

    /**
     * Turnkey action: Copies media/image source URL to clipboard if present.
     */
    @JvmOverloads
    fun copyImageUrl(label: String = "Copy Image Address"): KromiumMenuBuilder = apply {
        if (context.params.hasMedia()) {
            val src = context.params.sourceUrl ?: ""
            if (src.isNotBlank()) {
                addItem(label) { ctx -> ctx.copyToClipboard(src) }
            }
        }
    }

    /**
     * Turnkey action: Searches the web for the currently selected text.
     */
    @JvmOverloads
    fun searchWeb(
        labelFormat: String = "Search Google for \"%s\"",
        engineUrl: String = "https://www.google.com/search?q=%s",
        openInSystemBrowser: Boolean = true
    ): KromiumMenuBuilder = apply {
        if (context.params.hasSelection()) {
            val sel = context.params.selectionText ?: ""
            val truncated = if (sel.length > 24) sel.take(21) + "..." else sel
            val label = labelFormat.replace("%s", truncated)
            addItem(label) { ctx -> ctx.searchWeb(sel, engineUrl, openInSystemBrowser) }
        }
    }

    /**
     * Adds the native Chromium Back command.
     */
    @JvmOverloads
    fun back(label: String = "Back"): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_BACK, label)
    }

    /**
     * Adds the native Chromium Forward command.
     */
    @JvmOverloads
    fun forward(label: String = "Forward"): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_FORWARD, label)
    }

    /**
     * Adds the native Chromium Reload command.
     */
    @JvmOverloads
    fun reload(label: String = "Reload"): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_RELOAD, label)
    }

    /**
     * Adds the native Chromium Print command.
     */
    @JvmOverloads
    fun print(label: String = "Print..."): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_PRINT, label)
    }

    /**
     * Adds the native Chromium View Page Source command.
     */
    @JvmOverloads
    fun viewSource(label: String = "View Page Source"): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_VIEW_SOURCE, label)
    }

    /**
     * Adds the native Chromium Copy command.
     */
    @JvmOverloads
    fun copy(label: String = "Copy"): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_COPY, label)
    }

    /**
     * Adds the native Chromium Cut command.
     */
    @JvmOverloads
    fun cut(label: String = "Cut"): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_CUT, label)
    }

    /**
     * Adds the native Chromium Paste command.
     */
    @JvmOverloads
    fun paste(label: String = "Paste"): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_PASTE, label)
    }

    /**
     * Adds the native Chromium Select All command.
     */
    @JvmOverloads
    fun selectAll(label: String = "Select All"): KromiumMenuBuilder = apply {
        model.addItem(CefMenuModel.MenuId.MENU_ID_SELECT_ALL, label)
    }

    companion object {
        /** Chromium's reserved starting integer ID for user-defined menu commands. */
        const val USER_COMMAND_FIRST = CefMenuModel.MenuId.MENU_ID_USER_FIRST

        private fun createIdGenerator(): () -> Int {
            val counter = AtomicInteger(USER_COMMAND_FIRST)
            return { counter.getAndIncrement() }
        }
    }
}
