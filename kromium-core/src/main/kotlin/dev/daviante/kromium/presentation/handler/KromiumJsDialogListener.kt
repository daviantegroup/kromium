package dev.daviante.kromium.presentation.handler

/**
 * Listener for handling JavaScript dialogs.
 */
fun interface KromiumJsDialogListener {
    /**
     * Called when a webpage triggers an alert, confirm, or prompt dialog.
     * Return `true` if your application handled the dialog, or `false` to let Chromium use default handling.
     */
    fun onDialog(dialog: KromiumJsDialog): Boolean
}
