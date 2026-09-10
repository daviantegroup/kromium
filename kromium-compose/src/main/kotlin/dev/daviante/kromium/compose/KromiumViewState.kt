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
import dev.daviante.kromium.presentation.browser.NavigationStage
import dev.daviante.kromium.presentation.handler.KromiumAuthRequest
import dev.daviante.kromium.presentation.handler.KromiumAuthResponse
import dev.daviante.kromium.presentation.handler.KromiumConsoleMessage
import dev.daviante.kromium.presentation.handler.KromiumDownloadItem
import dev.daviante.kromium.presentation.handler.KromiumJsDialog
import dev.daviante.kromium.presentation.handler.KromiumPermissionHandler
import dev.daviante.kromium.presentation.menu.KromiumContextMenuContext
import dev.daviante.kromium.presentation.menu.KromiumContextMenuHandler
import dev.daviante.kromium.presentation.menu.KromiumMenuBuilder
import dev.daviante.kromium.presentation.network.KromiumAssetFilter
import dev.daviante.kromium.presentation.network.KromiumCookieManager
import dev.daviante.kromium.presentation.network.KromiumRequestInterceptor
import org.cef.network.CefRequest

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

    var permissionHandler: KromiumPermissionHandler? by mutableStateOf(null)

    var rememberPermissions: Boolean by mutableStateOf(true)

    /**
     * Clears all remembered permission decisions from the active browser session cache.
     */
    fun clearPermissionCache() {
        browser?.client?.clearPermissionCache()
    }

    var enableContextMenus: Boolean by mutableStateOf(true)

    var contextMenuHandler: KromiumContextMenuHandler? by mutableStateOf(null)

    /**
     * Configures the right-click context menu using a declarative Kotlin DSL block.
     */
    fun setContextMenu(block: KromiumMenuBuilder.(KromiumContextMenuContext) -> Unit) {
        contextMenuHandler = KromiumContextMenuHandler { builder, context ->
            builder.block(context)
        }
    }

    var onLoadError: ((KromiumLoadError) -> Unit)? by mutableStateOf(null)

    var shouldOverrideUrlLoading: ((url: String) -> Boolean)? by mutableStateOf(null)

    var sslErrorPolicy: SslErrorPolicy by mutableStateOf(SslErrorPolicy.Strict)

    var assetFilter: KromiumAssetFilter? by mutableStateOf(null)

    var hostLock: Set<String>? by mutableStateOf(null)

    var hostLockSubresources: Boolean by mutableStateOf(false)

    var hostLockSubframes: Boolean by mutableStateOf(false)

    /**
     * Whether to assert Do Not Track (DNT) and Global Privacy Control (Sec-GPC) headers on outbound requests.
     * Defaults to true.
     */
    var doNotTrack: Boolean by mutableStateOf(true)

    /**
     * Whether this Compose browser view renders in Off-Screen Rendering (OSR) mode.
     * Defaults to false (native windowed mode via SwingPanel), which provides maximum hardware vsync performance.
     * Set to true if overlapping Compose elements or transparency effects are required.
     */
    var isOffScreenRendered: Boolean by mutableStateOf(false)

    /**
     * Whether the background of the browser should be transparent when [isOffScreenRendered] is true.
     * Defaults to false.
     */
    var isTransparent: Boolean by mutableStateOf(false)

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

    /**
     * Executes [code] directly in the browser without waiting for a return value.
     * Unlike [evaluateJavaScript], this does not route through the message router,
     * making it ideal for side-effect scripts and instrumentation where no response is required.
     *
     * @param code The JavaScript source code to execute.
     * @param scriptUrl The URL reported as the script origin in DevTools (defaults to the current page URL).
     * @param startLine The line number offset reported in DevTools stack traces (defaults to 0).
     */
    fun executeJavaScript(
        code: String,
        scriptUrl: String = url,
        startLine: Int = 0
    ) {
        browser?.executeJavaScript(code, scriptUrl, startLine)
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
     * Configures fine-grained asset filtering, including custom file extensions,
     * URL patterns, resource types, programmatic filter predicates, and optional allow bypass rules.
     */
    fun blockAssets(
        images: Boolean = false,
        media: Boolean = false,
        fonts: Boolean = false,
        stylesheets: Boolean = false,
        customExtensions: Set<String> = emptySet(),
        customUrlPatterns: Set<String> = emptySet(),
        customResourceTypes: Set<CefRequest.ResourceType> = emptySet(),
        allowedExtensions: Set<String> = emptySet(),
        allowedUrlPatterns: Set<String> = emptySet(),
        allowedResourceTypes: Set<CefRequest.ResourceType> = emptySet(),
        customAllowFilter: ((request: CefRequest) -> Boolean)? = null,
        customFilter: ((request: CefRequest) -> Boolean)? = null
    ) {
        val filter = KromiumAssetFilter(
            blockImages = images,
            blockMedia = media,
            blockFonts = fonts,
            blockStylesheets = stylesheets,
            customBlockedExtensions = customExtensions,
            customBlockedUrlPatterns = customUrlPatterns,
            customBlockedResourceTypes = customResourceTypes,
            customFilter = customFilter,
            allowedExtensions = allowedExtensions,
            allowedUrlPatterns = allowedUrlPatterns,
            allowedResourceTypes = allowedResourceTypes,
            customAllowFilter = customAllowFilter,
            mode = dev.daviante.kromium.presentation.network.AssetFilterMode.BLOCKLIST
        )
        assetFilter = filter
        browser?.client?.assetFilter = filter
    }

    /**
     * Configures strict allowlist asset filtering so that only network requests matching the specified
     * extensions, URL patterns, resource types, or filter predicate are permitted.
     * All other network assets are blocked.
     *
     * @param extensions File extensions permitted to load (e.g. `setOf("js", "css")` or `setOf(".png")`).
     * @param urlPatterns URL patterns or domain substrings permitted to load.
     * @param resourceTypes CEF resource types permitted to load.
     * @param allowMainFrame Whether to permit top-level document navigation (defaults to true).
     * @param filter Programmatic predicate returning true to allow a request.
     */
    fun allowOnlyAssets(
        extensions: Set<String> = emptySet(),
        urlPatterns: Set<String> = emptySet(),
        resourceTypes: Set<CefRequest.ResourceType> = emptySet(),
        allowMainFrame: Boolean = true,
        filter: ((request: CefRequest) -> Boolean)? = null
    ) {
        val filterObj = KromiumAssetFilter.allowOnly(
            extensions = extensions,
            urlPatterns = urlPatterns,
            resourceTypes = resourceTypes,
            allowMainFrame = allowMainFrame,
            filter = filter
        )
        assetFilter = filterObj
        browser?.client?.assetFilter = filterObj
    }

    /**
     * Restricts navigation to the specified allowed hostnames.
     */
    fun setHostLock(vararg allowedHosts: String, lockSubresources: Boolean = false, lockSubframes: Boolean = false) {
        val set = allowedHosts.toSet()
        hostLock = set
        this.hostLockSubresources = lockSubresources
        this.hostLockSubframes = lockSubframes
        browser?.setHostLock(set, lockSubresources, lockSubframes)
    }

    /**
     * Removes active host lock constraints.
     */
    fun clearHostLock() {
        hostLock = null
        hostLockSubresources = false
        hostLockSubframes = false
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

    /**
     * Retrieves all cookies across all domains stored in the global cookie store.
     */
    suspend fun getAllCookies(): List<org.cef.network.CefCookie> =
        browser?.getAllCookies() ?: KromiumCookieManager.getAllCookies()

    /**
     * Clears HTML5 `localStorage` and `sessionStorage` for the active page origin.
     */
    suspend fun clearWebStorage(): Boolean =
        browser?.clearWebStorage() ?: false

    /**
     * Purges browsing data including cookies and HTML5 web storage.
     */
    suspend fun clearBrowsingData(clearCookies: Boolean = true, clearStorage: Boolean = true): Boolean =
        browser?.clearBrowsingData(clearCookies, clearStorage) ?: if (clearCookies) clearCookies() else true

    // ==========================================
    // Web Automation & Content Extraction Forwarders
    // ==========================================

    /**
     * Waits until an element matching [selector] appears in the DOM.
     */
    suspend fun waitForSelector(selector: String, timeoutMs: Long = 10_000L): Boolean =
        browser?.waitForSelector(selector, timeoutMs) ?: false

    /**
     * Waits for an element matching [selector] and clicks it.
     */
    suspend fun click(selector: String, timeoutMs: Long = 10_000L): Boolean =
        browser?.click(selector, timeoutMs) ?: false

    /**
     * Waits for an input element matching [selector] and sets its value.
     */
    suspend fun fill(selector: String, value: String, timeoutMs: Long = 10_000L): Boolean =
        browser?.fill(selector, value, timeoutMs) ?: false

    /**
     * Waits for an input element matching [selector] and types [text] simulating human keystrokes.
     */
    suspend fun type(selector: String, text: String, delayMs: Long = 20L, timeoutMs: Long = 10_000L): Boolean =
        browser?.type(selector, text, delayMs, timeoutMs) ?: false

    /**
     * Selects an option in a `<select>` element matching [selector] by option value or label.
     */
    suspend fun selectOption(selector: String, value: String, timeoutMs: Long = 10_000L): Boolean =
        browser?.selectOption(selector, value, timeoutMs) ?: false

    /**
     * Retrieves the text content of the element matching [selector].
     */
    suspend fun getTextContent(selector: String, timeoutMs: Long = 10_000L): String? =
        browser?.getTextContent(selector, timeoutMs)

    /**
     * Retrieves the value of [attributeName] for the element matching [selector].
     */
    suspend fun getAttribute(selector: String, attributeName: String, timeoutMs: Long = 10_000L): String? =
        browser?.getAttribute(selector, attributeName, timeoutMs)

    /**
     * Checks whether an element matching [selector] is currently visible in the DOM.
     */
    suspend fun isVisible(selector: String): Boolean =
        browser?.isVisible(selector) ?: false

    /**
     * Checks whether a checkbox or radio button matching [selector] is checked.
     */
    suspend fun isChecked(selector: String): Boolean =
        browser?.isChecked(selector) ?: false

    /**
     * Counts the number of elements in the DOM matching [selector].
     */
    suspend fun count(selector: String): Int =
        browser?.count(selector) ?: 0

    /**
     * Waits until in-flight network requests cease for at least [idleTimeMs].
     */
    suspend fun waitForNetworkIdle(idleTimeMs: Long = 500L, maxTimeoutMs: Long = 15_000L): Boolean =
        browser?.waitForNetworkIdle(idleTimeMs, maxTimeoutMs) ?: false

    /**
     * Waits until the browser navigates to a URL matching [urlPattern].
     */
    suspend fun waitForUrl(urlPattern: String, isRegex: Boolean = false, timeoutMs: Long = 15_000L): Boolean =
        browser?.waitForUrl(urlPattern, isRegex, timeoutMs) ?: false

    /**
     * Waits for the page navigation lifecycle to reach the specified [stage].
     */
    suspend fun waitForNavigation(stage: NavigationStage = NavigationStage.LOADED, timeoutMs: Long = 10_000L): Boolean =
        browser?.waitForNavigation(stage, timeoutMs) ?: false

    /**
     * Navigates to [url] and suspends until navigation reaches the requested [waitUntil] stage.
     */
    suspend fun loadUrl(url: String, waitUntil: NavigationStage, timeoutMs: Long = 10_000L): Boolean {
        this.url = url
        return browser?.loadUrl(url, waitUntil, timeoutMs) ?: false
    }
}

@Composable
fun rememberKromiumState(
    key: Any? = null,
    initialUrl: String = "about:blank"
): KromiumViewState {
    return remember(key, initialUrl) { KromiumViewState(initialUrl) }
}
