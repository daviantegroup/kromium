package dev.daviante.kromium.presentation.menu

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.presentation.browser.KromiumBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import java.awt.Desktop
import java.awt.Point
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Context passed to context menu action callbacks when an item is triggered by the user.
 *
 * Exposes reference to the active browser instance, clicked parameters, and turnkey helpers
 * (clipboard, DevTools inspect, web search, downloads).
 *
 * @property browser High-level [KromiumBrowser] instance, if resolved.
 * @property rawBrowser Low-level JCEF [CefBrowser] instance.
 * @property params Contextual parameters of the right-click event.
 * @property frame The specific [CefFrame] where the right-click occurred.
 */
class KromiumContextMenuContext(
    val browser: KromiumBrowser?,
    val rawBrowser: CefBrowser?,
    val params: KromiumContextMenuParams,
    val frame: CefFrame?
) {
    /**
     * Copies the given text to the system clipboard.
     */
    fun copyToClipboard(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            KromiumLogger.d(TAG, "Copied text to clipboard: $text")
        } catch (t: Throwable) {
            KromiumLogger.e(TAG, "Failed to copy text to clipboard", t)
        }
    }

    /**
     * Opens Chromium DevTools targeting the clicked element coordinates.
     */
    fun inspectElement() {
        try {
            rawBrowser?.openDevTools(Point(params.x, params.y))
            KromiumLogger.i(TAG, "Opened DevTools inspect element at (${params.x}, ${params.y})")
        } catch (t: Throwable) {
            KromiumLogger.e(TAG, "Failed to open DevTools inspect element", t)
        }
    }

    /**
     * Initiates a download for the given URL using Chromium's native download pipeline.
     */
    fun startDownload(url: String) {
        try {
            rawBrowser?.startDownload(url)
            KromiumLogger.i(TAG, "Started download for: $url")
        } catch (t: Throwable) {
            KromiumLogger.e(TAG, "Failed to start download for: $url", t)
        }
    }

    /**
     * Performs a web search using the provided query and template.
     *
     * @param query The search query (defaults to highlighted [KromiumContextMenuParams.selectionText]).
     * @param engineUrl Search URL template formatted with `%s` for query insertion.
     * @param openInSystemBrowser If true, attempts to open in the OS default desktop browser; otherwise loads in [browser].
     */
    @JvmOverloads
    fun searchWeb(
        query: String = params.selectionText ?: "",
        engineUrl: String = "https://www.google.com/search?q=%s",
        openInSystemBrowser: Boolean = true
    ) {
        if (query.isBlank()) return
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val targetUrl = engineUrl.replace("%s", encoded)

        if (openInSystemBrowser && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(targetUrl))
                KromiumLogger.i(TAG, "Opened web search in system browser: $targetUrl")
                return
            } catch (t: Throwable) {
                KromiumLogger.w(TAG, "Failed to launch system browser, falling back to embedded browser", t)
            }
        }

        browser?.loadUrl(targetUrl) ?: rawBrowser?.loadURL(targetUrl)
        KromiumLogger.i(TAG, "Loaded web search in embedded browser: $targetUrl")
    }

    companion object {
        private const val TAG = "KromiumContextMenu"
    }
}
