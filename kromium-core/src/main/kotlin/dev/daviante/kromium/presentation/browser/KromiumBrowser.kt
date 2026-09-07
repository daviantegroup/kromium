package dev.daviante.kromium.presentation.browser

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


import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import java.awt.Component
import java.awt.Point
import java.awt.image.BufferedImage
import java.awt.Graphics2D
import java.awt.event.MouseEvent

private const val TAG = "KromiumBrowser"

/**
 * High-level wrapper over [CefBrowser] providing Kotlin coroutine JS evaluation,
 * DOM extraction, Zoom controls, DevTools inspection, and lifecycle management.
 */
class KromiumBrowser(
    val client: KromiumClient,
    private val browser: CefBrowser
) {

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

    fun find(
        searchText: String,
        forward: Boolean,
        matchCase: Boolean,
        findNext: Boolean
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
     * Convenience function to fetch the complete HTML of the current document (`document.documentElement.outerHTML`).
     */
    suspend fun getHtml(): String {
        return evaluateJavaScript("document.documentElement.outerHTML") ?: ""
    }

    /**
     * Convenience function to fetch the visible text content of the current document (`document.body.innerText`).
     */
    suspend fun getText(): String {
        return evaluateJavaScript("document.body ? document.body.innerText : ''") ?: ""
    }

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
     * Loads raw HTML content with an optional base URL.
     * Uses a custom resource handler to serve the HTML while maintaining proper origin constraints,
     * avoiding the limitations and security restrictions of `data:` URIs.
     */
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
     * Prints the current page to a PDF file.
     */
    fun printToPdf(filePath: String) {
        val settings = org.cef.misc.CefPdfPrintSettings()
        // Default settings (A4, etc)
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
                val rect = java.awt.Rectangle(loc.x, loc.y, component.width, component.height)
                return java.awt.Robot().createScreenCapture(rect)
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

    fun dispose() {
        try {
            browser.stopLoad()
            browser.setCloseAllowed()
            browser.close(true)
        } catch (e: Exception) {
            KromiumLogger.w(TAG, "Error during browser disposal", e)
        }
    }
}
