package dev.daviante.kromium.presentation.handler



/**
 * Callback for listening to and customizing file downloads.
 */
fun interface KromiumDownloadListener {
    fun onDownloadUpdated(item: KromiumDownloadItem)
}
