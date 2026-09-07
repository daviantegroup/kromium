package dev.daviante.kromium.presentation.handler

import dev.daviante.kromium.domain.model.*
import dev.daviante.kromium.domain.config.*
import dev.daviante.kromium.domain.exception.*
import dev.daviante.kromium.data.engine.*
import dev.daviante.kromium.data.model.*
import dev.daviante.kromium.presentation.browser.*
import dev.daviante.kromium.presentation.handler.*
import dev.daviante.kromium.presentation.js.*
import dev.daviante.kromium.presentation.network.*
import dev.daviante.kromium.core.logging.*
import dev.daviante.kromium.core.util.*


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
