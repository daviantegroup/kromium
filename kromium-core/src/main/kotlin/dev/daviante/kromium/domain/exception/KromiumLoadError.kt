package dev.daviante.kromium.domain.exception

data class KromiumLoadError(
    val errorCode: Int,
    val errorText: String,
    val failedUrl: String
)
