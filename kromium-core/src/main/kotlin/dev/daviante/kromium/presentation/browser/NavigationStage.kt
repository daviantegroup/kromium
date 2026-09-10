package dev.daviante.kromium.presentation.browser

/**
 * Defines the navigation lifecycle stage to await during page navigation.
 */
enum class NavigationStage {
    /**
     * Main frame navigation has started ([org.cef.handler.CefLoadHandler.onLoadStart] fired for main frame).
     */
    STARTED,

    /**
     * Main frame document and static assets have finished loading ([org.cef.handler.CefLoadHandler.onLoadEnd] fired for main frame).
     */
    LOADED,

    /**
     * Main frame has finished loading and active in-flight network requests have settled to zero
     * for at least 500ms (or within the remaining timeout window).
     */
    NETWORK_IDLE
}
