package dev.daviante.kromium.presentation.handler

/**
 * Functional interface for Java consumers to monitor page loading state and navigation history.
 */
fun interface KromiumLoadingListener {
    /**
     * Invoked when the loading state or history navigation capability changes.
     *
     * @param isLoading True if a frame is currently loading content.
     * @param canGoBack True if the browser has backward history.
     * @param canGoForward True if the browser has forward history.
     */
    fun onLoadingChanged(isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean)
}
