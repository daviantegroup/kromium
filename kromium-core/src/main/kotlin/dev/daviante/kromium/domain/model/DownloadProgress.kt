package dev.daviante.kromium.domain.model

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
 * Detailed download progress state.
 */
data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long?,
    val fraction: Float // 0.0f .. 1.0f
) {
    val percentage: Int get() = (fraction * 100f).toInt().coerceIn(0, 100)

    companion object {
        val Initial = DownloadProgress(0L, null, 0.0f)
    }
}
