package dev.daviante.kromium.presentation.handler

/**
 * Encapsulates a JavaScript alert, confirm, or prompt dialog.
 */
class KromiumJsDialog(
    val message: String,
    val defaultPromptText: String,
    val type: KromiumJsDialogType,
    private val onConfirm: (promptResult: String) -> Unit,
    private val onCancel: () -> Unit
) {
    fun confirm(promptResult: String = defaultPromptText) = onConfirm(promptResult)
    fun cancel() = onCancel()
}
