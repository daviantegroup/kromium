package dev.daviante.kromium.presentation.menu

/**
 * Functional interface for intercepting and customizing right-click context menus.
 *
 * Compatible with Kotlin lambdas `{ builder, context -> ... }` and Java lambdas `(builder, context) -> ...`.
 */
fun interface KromiumContextMenuHandler {

    /**
     * Called when a context menu is about to be displayed.
     *
     * @param builder Declarative builder for adding, removing, or customizing menu items.
     * @param context Click coordinates, target URLs, selected text, and helper actions.
     */
    fun onBuildContextMenu(builder: KromiumMenuBuilder, context: KromiumContextMenuContext)

    companion object {
        /**
         * Suppresses context menus entirely by clearing all items.
         */
        @JvmStatic
        fun disabled(): KromiumContextMenuHandler =
            KromiumContextMenuHandler { builder, _ ->
                builder.clear()
            }

        /**
         * Preserves default native Chromium context menu items without modifications.
         */
        @JvmStatic
        fun defaultMenu(): KromiumContextMenuHandler =
            KromiumContextMenuHandler { _, _ -> }

        /**
         * Replaces default menus with a minimal developer menu containing only "Inspect Element".
         */
        @JvmStatic
        fun devToolsOnly(): KromiumContextMenuHandler =
            KromiumContextMenuHandler { builder, _ ->
                builder.clear().inspectElement()
            }

        /**
         * Turnkey menu tailored for clean desktop applications:
         * - If text is selected or editable: provides Cut, Copy, Paste, and Web Search.
         * - If clicked on link: provides Copy Link.
         * - Optionally includes Inspect Element.
         */
        @JvmStatic
        @JvmOverloads
        fun minimalEditing(includeInspectElement: Boolean = false): KromiumContextMenuHandler =
            KromiumContextMenuHandler { builder, ctx ->
                builder.clear()

                if (ctx.params.isLink()) {
                    builder.copyLink()
                    builder.separator()
                }

                if (ctx.params.hasSelection()) {
                    builder.copy()
                    builder.searchWeb()
                    builder.separator()
                }

                if (ctx.params.isEditable) {
                    builder.cut()
                    builder.paste()
                    builder.selectAll()
                    builder.separator()
                }

                if (includeInspectElement) {
                    builder.inspectElement()
                }
            }
    }
}
