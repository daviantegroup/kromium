package dev.daviante.kromium.presentation.network

/**
 * Represents an outgoing web resource request.
 */
data class KromiumWebResourceRequest(
    val url: String,
    val method: String,
    val headers: MutableMap<String, String>,
    val isNavigation: Boolean,
    val isDownload: Boolean,
    val requestInitiator: String? = null
)
