package dev.daviante.kromium.domain.model

sealed class KromiumState {
    data object Idle : KromiumState()
    data object Locating : KromiumState()
    data class Downloading(val progress: DownloadProgress) : KromiumState()
    data object Extracting : KromiumState()
    data object Initializing : KromiumState()
    data object Ready : KromiumState()
    data class Error(val cause: Throwable) : KromiumState()
    data object Disposed : KromiumState()
}
