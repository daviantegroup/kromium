package dev.daviante.kromium.presentation.browser

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.util.FutureBridge
import dev.daviante.kromium.domain.exception.KromiumException
import dev.daviante.kromium.domain.model.KromiumPdfSettings
import dev.daviante.kromium.presentation.js.JsEvaluator
import dev.daviante.kromium.presentation.network.KromiumAssetFilter
import dev.daviante.kromium.presentation.network.KromiumCookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefPdfPrintCallback
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "KromiumBrowser"

/**
 * High-level wrapper over [CefBrowser] providing Kotlin coroutine JS evaluation,
 * DOM extraction, Zoom controls, DevTools inspection, and lifecycle management.
 */
class KromiumBrowser(
    val client: KromiumClient,
    private val browser: CefBrowser,
    private val hostPeer: java.awt.Window? = null
) {

    val rawBrowser: CefBrowser get() = browser

    val uiComponent: Component get() = browser.uiComponent

    val url: String? get() = browser.url

    fun loadUrl(url: String) {
        browser.loadURL(url)
    }

    fun reload() = browser.reload()
    
    fun reloadIgnoreCache() = browser.reloadIgnoreCache()
    
    fun goBack() = browser.goBack()
    
    fun goForward() = browser.goForward()
    
    fun canGoBack(): Boolean = browser.canGoBack()
    
    fun canGoForward(): Boolean = browser.canGoForward()
    
    fun stopLoad() = browser.stopLoad()
    
    /**
     * Programmatically initiates a file download from the specified URL using this browser session.
     */
    fun startDownload(url: String) {
        browser.startDownload(url)
    }

    /**
     * Programmatically cancels an in-progress download identified by its download ID.
     */
    fun cancelDownload(downloadId: Int): Boolean = client.cancelDownload(downloadId)

    /**
     * Programmatically pauses an in-progress download identified by its download ID.
     */
    fun pauseDownload(downloadId: Int): Boolean = client.pauseDownload(downloadId)

    /**
     * Programmatically resumes a paused download identified by its download ID.
     */
    fun resumeDownload(downloadId: Int): Boolean = client.resumeDownload(downloadId)

    /**
     * Checks whether an in-progress download is currently paused.
     */
    fun isDownloadPaused(downloadId: Int): Boolean = KromiumClient.isDownloadPausedGlobally(downloadId)

    @JvmOverloads
    fun find(
        searchText: String,
        forward: Boolean = true,
        matchCase: Boolean = false,
        findNext: Boolean = false
    ) {
        browser.find(searchText, forward, matchCase, findNext)
    }

    fun stopFinding(clearSelection: Boolean) {
        browser.stopFinding(clearSelection)
    }

    /**
     * Executes arbitrary JavaScript asynchronously and returns the stringified response.
     */
    suspend fun evaluateJavaScript(expression: String): String? {
        return JsEvaluator.evaluate(
            browser = browser,
            handler = client.jsHandler,
            expression = expression,
            routerQueryName = client.routerQueryName
        )
    }

    /**
     * Executes arbitrary JavaScript asynchronously returning a Java [CompletableFuture].
     * Provides 100% idiomatic non-blocking execution for Java callers.
     */
    @JvmOverloads
    fun evaluateJavaScriptAsync(
        expression: String,
        timeoutMs: Long? = null
    ): CompletableFuture<String?> =
        FutureBridge.toCompletableFuture {
            JsEvaluator.evaluate(
                browser = browser,
                handler = client.jsHandler,
                expression = expression,
                routerQueryName = client.routerQueryName,
                timeoutMs = timeoutMs
            )
        }

    /**
     * Convenience function to fetch the complete HTML of the current document (`document.documentElement.outerHTML`).
     */
    suspend fun getHtml(): String {
        return evaluateJavaScript("document.documentElement.outerHTML") ?: ""
    }

    /**
     * Asynchronously fetches the complete HTML of the current document returning a Java [CompletableFuture].
     */
    fun getHtmlAsync(): CompletableFuture<String> =
        FutureBridge.toCompletableFuture { getHtml() }

    /**
     * Convenience function to fetch the visible text content of the current document (`document.body.innerText`).
     */
    suspend fun getText(): String {
        return evaluateJavaScript("document.body ? document.body.innerText : ''") ?: ""
    }

    /**
     * Asynchronously fetches the visible text content of the current document returning a Java [CompletableFuture].
     */
    fun getTextAsync(): CompletableFuture<String> =
        FutureBridge.toCompletableFuture { getText() }

    /**
     * Attempts to resolve the URL of the page's favicon using DOM inspection.
     */
    suspend fun getFaviconUrl(): String? {
        val script = """
            (function() {
                var link = document.querySelector("link[rel*='icon']");
                return link ? link.href : '';
            })();
        """.trimIndent()
        val url = evaluateJavaScript(script)
        return if (url.isNullOrBlank()) null else url
    }

    /**
     * Asynchronously resolves the favicon URL returning a Java [CompletableFuture].
     */
    fun getFaviconUrlAsync(): CompletableFuture<String?> =
        FutureBridge.toCompletableFuture { getFaviconUrl() }

    /**
     * Loads raw HTML content with an optional base URL.
     * Uses a custom resource handler to serve the HTML while maintaining proper origin constraints,
     * avoiding the limitations and security restrictions of `data:` URIs.
     */
    @JvmOverloads
    fun loadHtml(html: String, baseUrl: String = "http://kromium.local/") {
        val id = java.util.UUID.randomUUID().toString()
        val separator = if (baseUrl.endsWith("/")) "" else "/"
        val url = "$baseUrl$separator$id"
        client.htmlPayloads[url] = html
        browser.loadURL(url)
    }

    /**
     * Sets the zoom level for this browser. (0.0 is 100%, 1.0 is ~120%, -1.0 is ~80%).
     */
    fun setZoom(level: Double) {
        browser.zoomLevel = level
    }

    /**
     * Gets the current zoom level.
     */
    fun getZoom(): Double = browser.zoomLevel

    /**
     * Property providing JavaBeans getter and setter for Java interop (getZoomLevel / setZoomLevel).
     */
    var zoomLevel: Double
        get() = browser.zoomLevel
        set(value) { browser.zoomLevel = value }

    /**
     * Opens the native Chromium DevTools window for debugging and DOM inspection.
     */
    fun openDevTools() {
        browser.openDevTools()
    }

    /**
     * Closes the DevTools if open.
     */
    fun closeDevTools() {
        browser.closeDevTools()
    }

    /**
     * Simulates a left-click at the specified coordinates on the rendering surface.
     */
    fun simulateClick(x: Int, y: Int) {
        val component = browser.uiComponent
        val press = MouseEvent(component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1)
        val release = MouseEvent(component, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1)
        val click = MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1)

        component.dispatchEvent(press)
        component.dispatchEvent(release)
        component.dispatchEvent(click)
    }

    /**
     * Triggers the operating system's native interactive print dialog for the current page.
     */
    fun print() {
        browser.print()
    }

    /**
     * Asynchronously prints the current web page to a vector PDF file using Kotlin coroutines.
     *
     * @param targetFile The output PDF destination [File].
     * @param settings The PDF layout and rendering configurations.
     * @return The generated PDF [File].
     * @throws KromiumException.PdfPrintFailed if Chromium fails to render or write the PDF.
     */
    @JvmOverloads
    suspend fun printToPdf(
        targetFile: File,
        settings: KromiumPdfSettings = KromiumPdfSettings.Default
    ): File = suspendCancellableCoroutine { continuation ->
        if (settings.createDirectories) {
            targetFile.parentFile?.mkdirs()
        }

        val canonicalPath = targetFile.canonicalPath
        val cefSettings = settings.toCefPdfPrintSettings()

        val callback = CefPdfPrintCallback { path, success ->
            if (success) {
                continuation.resume(File(path))
            } else {
                continuation.resumeWithException(
                    KromiumException.PdfPrintFailed(path)
                )
            }
        }

        browser.printToPDF(canonicalPath, cefSettings, callback)
    }

    /**
     * Asynchronously prints the current web page to a vector PDF file path using Kotlin coroutines.
     */
    @JvmOverloads
    suspend fun printToPdf(
        targetPath: String,
        settings: KromiumPdfSettings = KromiumPdfSettings.Default
    ): File = printToPdf(File(targetPath), settings)

    /**
     * Asynchronously prints the current web page to a vector PDF file returning a Java [CompletableFuture].
     *
     * @param targetFile The output PDF destination [File].
     * @param settings The PDF layout and rendering configurations.
     * @return A [CompletableFuture] resolving to the written PDF [File].
     */
    @JvmOverloads
    fun printToPdfAsync(
        targetFile: File,
        settings: KromiumPdfSettings = KromiumPdfSettings.Default
    ): CompletableFuture<File> {
        return FutureBridge.toCompletableFuture {
            printToPdf(targetFile, settings)
        }
    }

    /**
     * Asynchronously prints the current web page to a vector PDF file path returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun printToPdfAsync(
        targetPath: String,
        settings: KromiumPdfSettings = KromiumPdfSettings.Default
    ): CompletableFuture<File> = printToPdfAsync(File(targetPath), settings)

    /**
     * Fire-and-forget print to PDF for Java callers without async tracking.
     * For completion tracking and error handling, use [printToPdfAsync].
     */
    @JvmName("printToPdf")
    fun printToPdfFireAndForget(filePath: String) {
        val settings = org.cef.misc.CefPdfPrintSettings()
        browser.printToPDF(filePath, settings, null)
    }

    /**
     * Takes a screenshot of the currently visible rendering surface.
     * Returns a BufferedImage that can be saved using ImageIO.write().
     */
    fun takeScreenshot(): BufferedImage? {
        val component = browser.uiComponent ?: return null
        if (component.width <= 0 || component.height <= 0) return null

        // In windowed mode on screen, Robot captures actual hardware/GPU composited surface
        if (component.isShowing) {
            try {
                val loc = component.locationOnScreen
                if (loc.x >= 0 && loc.y >= 0) {
                    val rect = java.awt.Rectangle(loc.x, loc.y, component.width, component.height)
                    return java.awt.Robot().createScreenCapture(rect)
                }
            } catch (_: Throwable) {
                // Fallback to component painting if Robot capture is not permitted or headless
            }
        }

        val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        val graphics: Graphics2D = image.createGraphics()
        component.paint(graphics)
        graphics.dispose()
        return image
    }

    /**
     * Configures asset blocking on this browser's client to omit loading images, media, fonts, or stylesheets.
     * Dramatically reduces bandwidth and CPU overhead for headless tasks and web automation.
     */
    @JvmOverloads
    fun blockMediaAssets(
        images: Boolean = true,
        media: Boolean = true,
        fonts: Boolean = true,
        stylesheets: Boolean = false
    ) {
        client.assetFilter = KromiumAssetFilter(
            blockImages = images,
            blockMedia = media,
            blockFonts = fonts,
            blockStylesheets = stylesheets
        )
    }

    /**
     * Restricts navigation exclusively to the specified allowed hostnames/domains.
     * Attempts to navigate to non-whitelisted domains will be automatically blocked.
     *
     * @param allowedHosts Whitelist of permitted domains (e.g., "example.com", "api.example.com")
     * @param lockSubresources If true, also prevents loading subresources (scripts, fetch) from outside allowed hosts.
     */
    fun setHostLock(vararg allowedHosts: String, lockSubresources: Boolean = false) {
        client.hostLock = allowedHosts.toSet()
        client.hostLockSubresources = lockSubresources
    }

    /**
     * Restricts navigation exclusively to the specified allowed hostnames/domains.
     */
    @JvmOverloads
    fun setHostLock(allowedHosts: Set<String>?, lockSubresources: Boolean = false) {
        client.hostLock = allowedHosts
        client.hostLockSubresources = lockSubresources
    }

    /**
     * Removes any active host lock restrictions.
     */
    fun clearHostLock() {
        client.hostLock = null
        client.hostLockSubresources = false
    }

    /**
     * Retrieves all cookies for the current page as a key-value map.
     */
    suspend fun getCookies(): Map<String, String> {
        val currentUrl = url ?: return emptyMap()
        return KromiumCookieManager.getCookies(currentUrl)
    }

    /**
     * Asynchronously retrieves all cookies for the current page returning a Java [CompletableFuture].
     */
    fun getCookiesAsync(): CompletableFuture<Map<String, String>> =
        FutureBridge.toCompletableFuture { getCookies() }

    /**
     * Retrieves a specific cookie by [name] for the current page.
     */
    suspend fun getCookie(name: String): String? {
        val currentUrl = url ?: return null
        return KromiumCookieManager.getCookie(currentUrl, name)
    }

    /**
     * Asynchronously retrieves a specific cookie by [name] for the current page returning a Java [CompletableFuture].
     */
    fun getCookieAsync(name: String): CompletableFuture<String?> =
        FutureBridge.toCompletableFuture { getCookie(name) }

    /**
     * Sets a cookie for the current page.
     */
    @JvmOverloads
    fun setCookie(
        name: String,
        value: String,
        domain: String? = null,
        path: String = "/",
        isSecure: Boolean = false,
        isHttpOnly: Boolean = false,
        expires: java.util.Date? = null
    ): Boolean {
        val currentUrl = url ?: return false
        return KromiumCookieManager.setCookie(currentUrl, name, value, domain, path, isSecure, isHttpOnly, expires)
    }

    /**
     * Deletes all cookies from the underlying cookie store.
     */
    fun clearCookies(): Boolean = KromiumCookieManager.clearCookies()

    private val attachedHandlers = java.util.concurrent.CopyOnWriteArrayList<Any>()

    /**
     * Registers a callback invoked when a page has completed loading in the main frame.
     */
    @JvmSynthetic
    fun onPageFinished(callback: (url: String) -> Unit) {
        val handler = object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true && (cefBrowser == null || cefBrowser.identifier == browser.identifier)) {
                    callback(cefBrowser?.url ?: url ?: "")
                }
            }
        }
        client.addLoadHandler(handler)
        attachedHandlers.add(handler)
    }

    /**
     * Registers a Java [Consumer] callback invoked when a page has completed loading in the main frame.
     */
    fun onPageFinished(callback: Consumer<String>) {
        onPageFinished { url -> callback.accept(url) }
    }

    /**
     * Registers a callback invoked when the browser URL / address changes.
     */
    @JvmSynthetic
    fun onAddressChanged(callback: (newUrl: String) -> Unit) {
        val handler = object : org.cef.handler.CefDisplayHandlerAdapter() {
            override fun onAddressChange(cefBrowser: CefBrowser?, frame: CefFrame?, newUrl: String?) {
                if (cefBrowser == null || cefBrowser.identifier == browser.identifier) {
                    newUrl?.let(callback)
                }
            }
        }
        client.addDisplayHandler(handler)
        attachedHandlers.add(handler)
    }

    /**
     * Registers a Java [Consumer] callback invoked when the browser URL / address changes.
     */
    fun onAddressChanged(callback: Consumer<String>) {
        onAddressChanged { newUrl -> callback.accept(newUrl) }
    }

    /**
     * Registers a callback invoked when the page title changes.
     */
    @JvmSynthetic
    fun onTitleChanged(callback: (title: String) -> Unit) {
        val handler = object : org.cef.handler.CefDisplayHandlerAdapter() {
            override fun onTitleChange(cefBrowser: CefBrowser?, title: String?) {
                if (cefBrowser == null || cefBrowser.identifier == browser.identifier) {
                    title?.let(callback)
                }
            }
        }
        client.addDisplayHandler(handler)
        attachedHandlers.add(handler)
    }

    /**
     * Registers a Java [Consumer] callback invoked when the page title changes.
     */
    fun onTitleChanged(callback: Consumer<String>) {
        onTitleChanged { title -> callback.accept(title) }
    }

    /**
     * Registers a callback invoked when the browser loading state or navigation history changes.
     */
    @JvmSynthetic
    fun onLoadingChanged(callback: (isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) -> Unit) {
        val handler = object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                cefBrowser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (cefBrowser == null || cefBrowser.identifier == browser.identifier) {
                    callback(isLoading, canGoBack, canGoForward)
                }
            }
        }
        client.addLoadHandler(handler)
        attachedHandlers.add(handler)
    }

    /**
     * Registers a Java [KromiumLoadingListener] callback invoked when the browser loading state or navigation history changes.
     */
    fun onLoadingChanged(listener: dev.daviante.kromium.presentation.handler.KromiumLoadingListener) {
        onLoadingChanged { isLoading, canGoBack, canGoForward ->
            listener.onLoadingChanged(isLoading, canGoBack, canGoForward)
        }
    }

    fun dispose() {
        try {
            for (h in attachedHandlers) {
                when (h) {
                    is org.cef.handler.CefLoadHandler -> client.removeLoadHandler(h)
                    is org.cef.handler.CefDisplayHandler -> client.removeDisplayHandler(h)
                }
            }
            attachedHandlers.clear()
            browser.stopLoad()
            browser.setCloseAllowed()
            browser.close(true)
        } catch (e: Exception) {
            KromiumLogger.w(TAG, "Error during browser disposal", e)
        } finally {
            hostPeer?.let { peer ->
                javax.swing.SwingUtilities.invokeLater {
                    try {
                        peer.isVisible = false
                        peer.dispose()
                    } catch (_: Throwable) {}
                }
            }
        }
    }
}
