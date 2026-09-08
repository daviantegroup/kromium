package dev.daviante.kromium.domain.model



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
