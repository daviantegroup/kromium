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
 * Listener for handling JavaScript dialogs.
 */
fun interface KromiumJsDialogListener {
    /**
     * Called when a webpage triggers an alert, confirm, or prompt dialog.
     * Return `true` if your application handled the dialog, or `false` to let Chromium use default handling.
     */
    fun onDialog(dialog: KromiumJsDialog): Boolean
}
