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
 * Metadata representing a file download event from Chromium.
 */
data class KromiumDownloadItem(
    val id: Int,
    val url: String,
    val suggestedFileName: String,
    val totalBytes: Long,
    val receivedBytes: Long,
    val percentComplete: Int,
    val speed: Long,
    val isInProgress: Boolean,
    val isComplete: Boolean,
    val isCanceled: Boolean,
    val isPaused: Boolean = false,
    val fullPath: String = ""
)
