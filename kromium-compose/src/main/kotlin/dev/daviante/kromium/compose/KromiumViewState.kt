package dev.daviante.kromium.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.daviante.kromium.domain.exception.KromiumException
import dev.daviante.kromium.domain.exception.KromiumLoadError
import dev.daviante.kromium.domain.exception.SslErrorPolicy
import dev.daviante.kromium.domain.model.KromiumPdfSettings
import dev.daviante.kromium.presentation.browser.KromiumBrowser
import dev.daviante.kromium.presentation.browser.KromiumClient
import dev.daviante.kromium.presentation.handler.KromiumAuthRequest
import dev.daviante.kromium.presentation.handler.KromiumAuthResponse
import dev.daviante.kromium.presentation.handler.KromiumConsoleMessage
import dev.daviante.kromium.presentation.handler.KromiumDownloadItem
import dev.daviante.kromium.presentation.handler.KromiumJsDialog
import dev.daviante.kromium.presentation.network.KromiumAssetFilter
import dev.daviante.kromium.presentation.network.KromiumCookieManager
import dev.daviante.kromium.presentation.network.KromiumRequestInterceptor

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

    var downloadDirectory: java.io.File? by mutableStateOf(null)

    var onBeforeDownload: ((item: KromiumDownloadItem, suggestedFileName: String) -> String?)? by mutableStateOf(null)

    var onJsDialog: ((KromiumJsDialog) -> Boolean)? by mutableStateOf(null)
    
    var onConsoleMessage: ((KromiumConsoleMessage) -> Unit)? by mutableStateOf(null)

    var onAuthRequired: ((KromiumAuthRequest) -> KromiumAuthResponse)? by mutableStateOf(null)

    var onPopup: ((url: String) -> Boolean)? by mutableStateOf(null)

    var onPermissionRequest: ((url: String) -> Boolean)? by mutableStateOf(null)

    var enableContextMenus: Boolean by mutableStateOf(true)

    var onLoadError: ((KromiumLoadError) -> Unit)? by mutableStateOf(null)

    var shouldOverrideUrlLoading: ((url: String) -> Boolean)? by mutableStateOf(null)

    var sslErrorPolicy: SslErrorPolicy by mutableStateOf(SslErrorPolicy.Strict)

    var assetFilter: KromiumAssetFilter? by mutableStateOf(null)

    var hostLock: Set<String>? by mutableStateOf(null)

    var hostLockSubresources: Boolean by mutableStateOf(false)

    var browser: KromiumBrowser? by mutableStateOf(null)
        internal set

    var pendingUrl: String? by mutableStateOf(null)
        private set

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

    /**
     * Triggers the operating system's native interactive print dialog for the current page.
     */
    fun print() {
        browser?.print()
    }

    /**
     * Asynchronously prints the current web page to a vector PDF file using Kotlin coroutines.
     *
     * @param targetFile The output PDF destination [java.io.File].
     * @param settings The PDF layout and rendering configurations.
     * @return The generated PDF [java.io.File].
     * @throws KromiumException.NotInitialized if the browser is not active yet.
     * @throws KromiumException.PdfPrintFailed if Chromium fails to render or write the PDF.
     */
    suspend fun printToPdf(
        targetFile: java.io.File,
        settings: KromiumPdfSettings = KromiumPdfSettings.Default
    ): java.io.File {
        val b = browser ?: throw KromiumException.NotInitialized
        return b.printToPdf(targetFile, settings)
    }

    /**
     * Asynchronously prints the current web page to a vector PDF file path using Kotlin coroutines.
     */
    suspend fun printToPdf(
        targetPath: String,
        settings: KromiumPdfSettings = KromiumPdfSettings.Default
    ): java.io.File = printToPdf(java.io.File(targetPath), settings)

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

    /**
     * Programmatically initiates a file download from the given URL.
     */
    fun startDownload(url: String) {
        browser?.startDownload(url)
    }

    /**
     * Programmatically cancels an in-progress download identified by its download ID.
     */
    fun cancelDownload(downloadId: Int): Boolean {
        return browser?.cancelDownload(downloadId) ?: KromiumClient.cancelDownloadGlobally(downloadId)
    }

    /**
     * Programmatically pauses an in-progress download identified by its download ID.
     */
    fun pauseDownload(downloadId: Int): Boolean {
        return browser?.pauseDownload(downloadId) ?: KromiumClient.pauseDownloadGlobally(downloadId)
    }

    /**
     * Programmatically resumes a paused download identified by its download ID.
     */
    fun resumeDownload(downloadId: Int): Boolean {
        return browser?.resumeDownload(downloadId) ?: KromiumClient.resumeDownloadGlobally(downloadId)
    }

    /**
     * Checks whether an in-progress download is currently paused.
     */
    fun isDownloadPaused(downloadId: Int): Boolean {
        return browser?.isDownloadPaused(downloadId) ?: KromiumClient.isDownloadPausedGlobally(downloadId)
    }

    /**
     * Configures asset blocking to avoid downloading images, media, fonts, or stylesheets.
     */
    fun blockMediaAssets(
        images: Boolean = true,
        media: Boolean = true,
        fonts: Boolean = true,
        stylesheets: Boolean = false
    ) {
        assetFilter = KromiumAssetFilter(
            blockImages = images,
            blockMedia = media,
            blockFonts = fonts,
            blockStylesheets = stylesheets
        )
        browser?.blockMediaAssets(images, media, fonts, stylesheets)
    }

    /**
     * Restricts navigation to the specified allowed hostnames.
     */
    fun setHostLock(vararg allowedHosts: String, lockSubresources: Boolean = false) {
        val set = allowedHosts.toSet()
        hostLock = set
        this.hostLockSubresources = lockSubresources
        browser?.setHostLock(set, lockSubresources)
    }

    /**
     * Removes active host lock constraints.
     */
    fun clearHostLock() {
        hostLock = null
        hostLockSubresources = false
        browser?.clearHostLock()
    }

    /**
     * Retrieves all cookies for the current page as a key-value map.
     */
    suspend fun getCookies(): Map<String, String> {
        return browser?.getCookies() ?: KromiumCookieManager.getCookies(url)
    }

    /**
     * Retrieves a specific cookie value by name for the current page.
     */
    suspend fun getCookie(name: String): String? {
        return browser?.getCookie(name) ?: KromiumCookieManager.getCookie(url, name)
    }

    /**
     * Sets a cookie for the current page.
     */
    fun setCookie(
        name: String,
        value: String,
        domain: String? = null,
        path: String = "/",
        isSecure: Boolean = false,
        isHttpOnly: Boolean = false,
        expires: java.util.Date? = null
    ): Boolean {
        return browser?.setCookie(name, value, domain, path, isSecure, isHttpOnly, expires)
            ?: KromiumCookieManager.setCookie(url, name, value, domain, path, isSecure, isHttpOnly, expires)
    }

    /**
     * Deletes all cookies from the underlying cookie store.
     */
    fun clearCookies(): Boolean = KromiumCookieManager.clearCookies()
}

@Composable
fun rememberKromiumState(
    key: Any? = null,
    initialUrl: String = "about:blank"
): KromiumViewState {
    return remember(key, initialUrl) { KromiumViewState(initialUrl) }
}
