package dev.daviante.kromium.demo.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.daviante.kromium.demo.model.BrowserTab
import dev.daviante.kromium.demo.model.ConsoleEntry
import dev.daviante.kromium.demo.model.ConsoleEntryType
import dev.daviante.kromium.demo.model.WorkbenchTab
import dev.daviante.kromium.presentation.network.KromiumCookieManager
import dev.daviante.kromium.core.logging.KromiumLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WorkspaceState(
    private val scope: CoroutineScope
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var activeTabId by mutableStateOf("")

    var isDevDrawerOpen by mutableStateOf(false)
    var activeWorkbenchTab by mutableStateOf(WorkbenchTab.JS_REPL)

    // JS REPL state
    var jsInputText by mutableStateOf("")
    var jsIsEvaluating by mutableStateOf(false)

    // Cookie Inspector state
    var cookiesMap by mutableStateOf<Map<String, String>>(emptyMap())
    var isLoadingCookies by mutableStateOf(false)

    // Downloads state
    val downloads = mutableStateListOf<dev.daviante.kromium.presentation.handler.KromiumDownloadItem>()

    // Clear Browsing Data state
    var isClearingData by mutableStateOf(false)
    var clearDataStatusMessage by mutableStateOf<String?>(null)

    // Page text state
    var extractedPageText by mutableStateOf("")
    var isExtractingText by mutableStateOf(false)

    // Zoom level tracking
    var currentZoom by mutableStateOf(0.0)

    val activeTab: BrowserTab?
        get() = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()

    fun onDownloadUpdated(item: dev.daviante.kromium.presentation.handler.KromiumDownloadItem) {
        val index = downloads.indexOfFirst { it.id == item.id }
        if (index != -1) {
            downloads[index] = item
        } else {
            downloads.add(0, item)
        }
    }

    fun clearDownloads() {
        downloads.forEach { if (it.isInProgress) cancelDownload(it.id) }
        downloads.clear()
    }

    fun cancelDownload(id: Int) {
        dev.daviante.kromium.presentation.browser.KromiumClient.cancelDownloadGlobally(id)
        val index = downloads.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = downloads[index]
            downloads[index] = item.copy(isInProgress = false, isCanceled = true, isPaused = false)
        }
    }

    fun pauseDownload(id: Int) {
        dev.daviante.kromium.presentation.browser.KromiumClient.pauseDownloadGlobally(id)
        val index = downloads.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = downloads[index]
            downloads[index] = item.copy(isPaused = true)
        }
    }

    fun resumeDownload(id: Int) {
        dev.daviante.kromium.presentation.browser.KromiumClient.resumeDownloadGlobally(id)
        val index = downloads.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = downloads[index]
            downloads[index] = item.copy(isPaused = false)
        }
    }

    fun removeDownload(id: Int) {
        val item = downloads.find { it.id == id }
        if (item?.isInProgress == true) {
            cancelDownload(id)
        }
        downloads.removeAll { it.id == id }
    }

    fun triggerSampleDownload() {
        // Initiates a live download of an actual sample artifact (Kromium Logo SVG)
        val sampleUrl = "https://raw.githubusercontent.com/daviantegroup/kromium/master/assets/logo.svg"
        downloadUrl(sampleUrl)
    }

    fun downloadUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return
        val target = resolveNavigationTarget(trimmed)

        val active = activeTab
        if (active?.viewState?.browser != null) {
            active.viewState.startDownload(target)
        } else if (active != null) {
            active.viewState.loadUrl(target)
        } else {
            openTab(target)
        }
    }

    fun showInFolder(item: dev.daviante.kromium.presentation.handler.KromiumDownloadItem) {
        val path = item.fullPath.ifBlank {
            java.io.File(System.getProperty("user.home"), "Downloads/${item.suggestedFileName}").absolutePath
        }
        val file = java.io.File(path)
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                val desktop = java.awt.Desktop.getDesktop()
                if (file.exists()) {
                    desktop.open(file.parentFile ?: file)
                } else {
                    desktop.open(java.io.File(System.getProperty("user.home"), "Downloads"))
                }
            }
        } catch (e: Exception) {
            KromiumLogger.e("WorkspaceState", "Failed to open folder for download: $path", e)
        }
    }

    fun openDownloadedFile(item: dev.daviante.kromium.presentation.handler.KromiumDownloadItem) {
        val path = item.fullPath.ifBlank {
            java.io.File(System.getProperty("user.home"), "Downloads/${item.suggestedFileName}").absolutePath
        }
        val file = java.io.File(path)
        try {
            if (java.awt.Desktop.isDesktopSupported() && file.exists()) {
                java.awt.Desktop.getDesktop().open(file)
            }
        } catch (e: Exception) {
            KromiumLogger.e("WorkspaceState", "Failed to open downloaded file: $path", e)
        }
    }

    fun handlePopup(url: String): Boolean {
        // Open the popup in a new tab so it integrates seamlessly with the tabbed UI
        openTab(url)
        return true
    }

    fun clearBrowsingData(
        clearCookies: Boolean = true,
        clearCache: Boolean = true,
        clearDownloadHistory: Boolean = true,
        clearLogs: Boolean = true
    ) {
        scope.launch {
            isClearingData = true
            clearDataStatusMessage = null
            try {
                if (clearCookies) {
                    KromiumCookieManager.clearCookies()
                    KromiumCookieManager.flush()
                    cookiesMap = emptyMap()
                }
                if (clearDownloadHistory) {
                    downloads.clear()
                }
                if (clearLogs) {
                    tabs.forEach { it.consoleLogs.clear() }
                }
                clearDataStatusMessage = "Browsing data cleared successfully!"
                kotlinx.coroutines.delay(3500)
                clearDataStatusMessage = null
            } catch (e: Exception) {
                clearDataStatusMessage = "Error clearing data: ${e.message}"
            } finally {
                isClearingData = false
            }
        }
    }

    init {
        // Initialize with default tab
        val defaultTab = BrowserTab(
            initialUrl = DEFAULT_HOME_URL,
            onDownloadUpdated = ::onDownloadUpdated,
            onPopupRequested = ::handlePopup
        )
        tabs.add(defaultTab)
        activeTabId = defaultTab.id
    }

    fun openTab(url: String = DEFAULT_HOME_URL): BrowserTab {
        val newTab = BrowserTab(
            initialUrl = url,
            onDownloadUpdated = ::onDownloadUpdated,
            onPopupRequested = ::handlePopup
        )
        tabs.add(newTab)
        activeTabId = newTab.id
        return newTab
    }

    fun closeTab(tabId: String) {
        if (tabs.size <= 1) {
            // Keep at least one tab open, reset it
            val tab = tabs.first()
            tab.viewState.loadUrl(DEFAULT_HOME_URL)
            return
        }

        val index = tabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            tabs.removeAt(index)
            if (activeTabId == tabId) {
                // Select previous or next tab
                val newIndex = (index - 1).coerceAtLeast(0)
                activeTabId = tabs[newIndex].id
            }
        }
    }

    fun selectTab(tabId: String) {
        activeTabId = tabId
    }

    fun navigate(rawInput: String) {
        val tab = activeTab ?: return
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return

        val targetUrl = resolveNavigationTarget(trimmed)
        tab.viewState.loadUrl(targetUrl)
    }

    fun adjustZoom(delta: Double) {
        val tab = activeTab ?: return
        val newZoom = (currentZoom + delta).coerceIn(-4.0, 5.0)
        currentZoom = newZoom
        tab.viewState.setZoom(newZoom)
    }

    fun resetZoom() {
        val tab = activeTab ?: return
        currentZoom = 0.0
        tab.viewState.setZoom(0.0)
    }

    fun evaluateJs(script: String) {
        val tab = activeTab ?: return
        if (script.isBlank()) return

        tab.consoleLogs.add(
            ConsoleEntry(
                type = ConsoleEntryType.JS_INPUT,
                message = script
            )
        )

        scope.launch {
            jsIsEvaluating = true
            try {
                val result = tab.viewState.evaluateJavaScript(script)
                tab.consoleLogs.add(
                    ConsoleEntry(
                        type = ConsoleEntryType.JS_OUTPUT,
                        message = result ?: "undefined"
                    )
                )
            } catch (e: Exception) {
                tab.consoleLogs.add(
                    ConsoleEntry(
                        type = ConsoleEntryType.ERROR,
                        message = "Eval Error: ${e.message}"
                    )
                )
            } finally {
                jsIsEvaluating = false
            }
        }
    }

    fun captureScreenshot() {
        val tab = activeTab ?: return
        scope.launch {
            try {
                val image = tab.viewState.browser?.takeScreenshot()
                if (image != null) {
                    tab.capturedScreenshot = image
                    tab.consoleLogs.add(
                        ConsoleEntry(
                            type = ConsoleEntryType.INFO,
                            message = "Captured frame buffer (${image.width}x${image.height}px)"
                        )
                    )
                } else {
                    tab.consoleLogs.add(
                        ConsoleEntry(
                            type = ConsoleEntryType.WARNING,
                            message = "Framebuffer capture returned null"
                        )
                    )
                }
            } catch (e: Exception) {
                tab.consoleLogs.add(
                    ConsoleEntry(
                        type = ConsoleEntryType.ERROR,
                        message = "Screenshot failed: ${e.message}"
                    )
                )
            }
        }
    }

    fun refreshCookies() {
        val tab = activeTab ?: return
        val currentUrl = tab.viewState.url
        if (currentUrl.isBlank() || currentUrl == "about:blank") {
            cookiesMap = emptyMap()
            return
        }

        scope.launch {
            isLoadingCookies = true
            try {
                cookiesMap = KromiumCookieManager.getCookies(currentUrl)
            } catch (e: Exception) {
                cookiesMap = emptyMap()
            } finally {
                isLoadingCookies = false
            }
        }
    }

    fun deleteCookie(cookieName: String) {
        val tab = activeTab ?: return
        val currentUrl = tab.viewState.url
        scope.launch {
            KromiumCookieManager.deleteCookie(currentUrl, cookieName)
            KromiumCookieManager.flush()
            refreshCookies()
        }
    }

    fun inspectPageText() {
        val tab = activeTab ?: return
        scope.launch {
            isExtractingText = true
            try {
                extractedPageText = tab.viewState.getText()
            } catch (e: Exception) {
                extractedPageText = "Error extracting text: ${e.message}"
            } finally {
                isExtractingText = false
            }
        }
    }

    fun clearConsoleLogs() {
        activeTab?.consoleLogs?.clear()
    }

    fun loadSampleShowcaseHtml() {
        val tab = activeTab ?: return
        val sampleHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Kromium Live Showcase</title>
                <style>
                    body {
                        margin: 0;
                        padding: 40px;
                        font-family: system-ui, -apple-system, sans-serif;
                        background: #090D16;
                        color: #F8FAFC;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                    }
                    .card {
                        background: #0F172A;
                        border: 1px solid #334155;
                        border-radius: 16px;
                        padding: 32px;
                        max-width: 640px;
                        width: 100%;
                        box-shadow: 0 20px 25px -5px rgba(0,0,0,0.5);
                    }
                    h1 {
                        background: linear-gradient(135deg, #38BDF8, #34D399);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        margin-top: 0;
                        font-size: 28px;
                    }
                    p { color: #94A3B8; line-height: 1.6; }
                    .badge {
                        display: inline-block;
                        padding: 4px 10px;
                        border-radius: 9999px;
                        background: rgba(56, 189, 248, 0.15);
                        color: #38BDF8;
                        font-size: 12px;
                        font-weight: 600;
                        margin-bottom: 12px;
                    }
                    canvas {
                        border-radius: 8px;
                        margin-top: 16px;
                        background: #000;
                        display: block;
                    }
                    button {
                        background: linear-gradient(135deg, #38BDF8, #34D399);
                        border: none;
                        color: #090D16;
                        font-weight: bold;
                        padding: 10px 20px;
                        border-radius: 8px;
                        cursor: pointer;
                        margin-top: 16px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <span class="badge">In-Memory Native Chromium Pipeline</span>
                    <h1>Kromium Compose Engine</h1>
                    <p>This page was loaded dynamically via <code>KromiumViewState.loadHtml(...)</code> with zero external network roundtrips.</p>
                    <canvas id="animCanvas" width="576" height="140"></canvas>
                    <button onclick="alert('Hello from embedded Kromium JavaScript dialog!')">Trigger JS Dialog</button>
                </div>
                <script>
                    const c = document.getElementById('animCanvas');
                    const ctx = c.getContext('2d');
                    let t = 0;
                    function draw() {
                        ctx.fillStyle = 'rgba(9, 13, 22, 0.2)';
                        ctx.fillRect(0, 0, c.width, c.height);
                        for(let i = 0; i < 30; i++) {
                            const x = (i * 20 + t * 2) % c.width;
                            const y = c.height / 2 + Math.sin((i + t * 0.05)) * 40;
                            ctx.beginPath();
                            ctx.arc(x, y, 6, 0, Math.PI * 2);
                            ctx.fillStyle = i % 2 === 0 ? '#38BDF8' : '#34D399';
                            ctx.fill();
                        }
                        t++;
                        requestAnimationFrame(draw);
                    }
                    draw();
                </script>
            </body>
            </html>
        """.trimIndent()
        tab.viewState.loadHtml(sampleHtml, "https://kromium.daviante.dev/showcase")
    }

    companion object {
        const val DEFAULT_HOME_URL = "https://kromium.daviante.dev"

        fun resolveNavigationTarget(input: String): String {
            val lower = input.lowercase()
            return when {
                lower.startsWith("http://") || lower.startsWith("https://") ||
                lower.startsWith("file://") || lower.startsWith("about:") ||
                lower.startsWith("kromium:") -> input

                lower.startsWith("localhost") || lower.startsWith("127.0.0.1") ->
                    "http://$input"

                input.contains(" ") || !input.contains(".") ->
                    "https://duckduckgo.com/?q=${URLEncoder.encode(input, StandardCharsets.UTF_8.name())}"

                else -> "https://$input"
            }
        }
    }
}
