package dev.daviante.kromium.compose

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


import androidx.compose.runtime.*

/**
 * High-level state holder representing an active browser session with full Compose reactivity.
 */
@Stable
class KromiumViewState(initialUrl: String) {

    var url: String by mutableStateOf(initialUrl)
        internal set

    var title: String by mutableStateOf("")
        internal set

    var isLoading: Boolean by mutableStateOf(false)
        internal set

    var canGoBack: Boolean by mutableStateOf(false)
        internal set

    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    var userAgent: String? by mutableStateOf(null)

    var requestInterceptor: KromiumRequestInterceptor? by mutableStateOf(null)

    var onDownload: ((KromiumDownloadItem) -> Unit)? by mutableStateOf(null)

    var onJsDialog: ((KromiumJsDialog) -> Boolean)? by mutableStateOf(null)
    
    var onConsoleMessage: ((KromiumConsoleMessage) -> Unit)? by mutableStateOf(null)

    var onAuthRequired: ((KromiumAuthRequest) -> KromiumAuthResponse)? by mutableStateOf(null)

    var onPopup: ((url: String) -> Boolean)? by mutableStateOf(null)

    var onPermissionRequest: ((url: String) -> Boolean)? by mutableStateOf(null)

    var enableContextMenus: Boolean by mutableStateOf(true)

    var onLoadError: ((KromiumLoadError) -> Unit)? by mutableStateOf(null)

    var shouldOverrideUrlLoading: ((url: String) -> Boolean)? by mutableStateOf(null)

    var sslErrorPolicy: SslErrorPolicy by mutableStateOf(SslErrorPolicy.Strict)

    var browser: KromiumBrowser? by mutableStateOf(null)
        internal set

    private var pendingUrl: String? = null

    fun loadUrl(newUrl: String) {
        url = newUrl
        val b = browser
        if (b != null) {
            b.loadUrl(newUrl)
        } else {
            pendingUrl = newUrl
        }
    }

    internal fun consumePendingUrl(): String? {
        val p = pendingUrl
        pendingUrl = null
        return p
    }

    fun loadHtml(html: String, baseUrl: String = "about:blank") {
        browser?.loadHtml(html, baseUrl)
    }

    fun reload(ignoreCache: Boolean = false) {
        if (ignoreCache) {
            browser?.reloadIgnoreCache()
        } else {
            browser?.reload()
        }
    }

    fun stopLoading() {
        browser?.stopLoad()
    }

    fun goBack() {
        if (canGoBack) {
            browser?.goBack()
        }
    }

    fun goForward() {
        if (canGoForward) {
            browser?.goForward()
        }
    }

    suspend fun evaluateJavaScript(script: String): String? {
        return browser?.evaluateJavaScript(script)
    }

    suspend fun getHtml(): String {
        return browser?.getHtml() ?: ""
    }

    suspend fun getText(): String {
        return browser?.getText() ?: ""
    }

    fun setZoom(level: Double) {
        browser?.setZoom(level)
    }

    fun getZoom(): Double = browser?.getZoom() ?: 0.0

    fun openDevTools() {
        browser?.openDevTools()
    }

    fun closeDevTools() {
        browser?.closeDevTools()
    }

    fun simulateClick(x: Int, y: Int) {
        browser?.simulateClick(x, y)
    }
}

@Composable
fun rememberKromiumState(
    key: Any? = null,
    initialUrl: String = "about:blank"
): KromiumViewState {
    return remember(key, initialUrl) { KromiumViewState(initialUrl) }
}
