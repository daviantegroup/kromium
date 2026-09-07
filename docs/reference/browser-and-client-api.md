# Browser & Client API Reference

[Documentation Hub](../README.md) &bull; **API Reference** &bull; Browser & Client API

---

## 🏛️ `Kromium` (Singleton Coordinator)

The primary entry point for managing engine lifecycle and creating browser instances.

### Properties & State
* `val state: StateFlow<KromiumState>`: Observable engine state flow.
* `val isReady: Boolean`: Returns `true` when engine is initialized and ready for browser creation.
* `val activeProxy: KromiumProxy`: The currently active global proxy configuration.

### Lifecycle Methods
* `suspend fun initialize(configure: KromiumConfig.() -> Unit = {})`: Idempotently initializes and bootstraps the native engine.
* `fun dispose()`: Shuts down Chromium, frees native memory, flushes cookies, and kills helper subprocesses.
* `fun setProxy(proxy: KromiumProxy): Result<Unit>`: Dynamically updates proxy settings across all active browser windows without restarting.

### Factory Methods
* `fun newClient(): KromiumClient`: Creates an isolated client wrapper around a fresh `CefClient`.
* `suspend fun awaitClient(): KromiumClient`: Suspends until `Ready`, then creates a new `KromiumClient`.
* `fun createBrowser(url: String? = "about:blank"): KromiumBrowser`: Creates a new browser backed by a fresh client.
* `fun createHeadlessBrowser(url: String? = "about:blank", width: Int = 1280, height: Int = 800): KromiumBrowser`: Creates a zero-dependency headless browser backed by an off-screen Swing peer.
* `suspend fun awaitBrowser(url: String? = "about:blank"): KromiumBrowser`: Suspends until `Ready`, then instantiates a `KromiumBrowser`.
* `suspend fun awaitHeadlessBrowser(url: String? = "about:blank", width: Int = 1280, height: Int = 800): KromiumBrowser`: Suspends until `Ready`, then instantiates a headless `KromiumBrowser`.

---

## 💻 `KromiumBrowser`

Represents a single browser view instance.

### Properties
* `val uiComponent: java.awt.Component`: The underlying AWT/Swing component to embed in Compose (`SwingPanel`) or Swing (`JFrame`).
* `val isDisposed: Boolean`: Returns `true` if the browser has been closed or disposed.
* `val client: KromiumClient`: The client instance managing this browser.
* `val currentUrl: String`: The currently active URL.

### Navigation & Loading
* `fun loadUrl(url: String)`: Navigates to the specified URL.
* `fun loadHtml(html: String, baseUrl: String = "http://kromium.local/")`: Loads an HTML string directly into the main frame.
* `fun canGoBack(): Boolean`: Returns `true` if navigation history contains a previous entry.
* `fun goBack()`: Navigates backward in history.
* `fun canGoForward(): Boolean`: Returns `true` if forward history is available.
* `fun goForward()`: Navigates forward in history.
* `fun reload()`: Reloads the active page.
* `fun reloadIgnoreCache()`: Reloads bypassing local cache stores.
* `fun stopLoad()`: Halts pending network requests. (On Compose `KromiumViewState`: `state.stopLoading()`).

### Zoom & Search
* `fun getZoom(): Double`: Retrieves current zoom level (`0.0` = 100%).
* `fun setZoom(level: Double)`: Sets zoom factor (`1.0` = ~120%, `-1.0` = ~80%).
* `fun find(searchText: String, forward: Boolean, matchCase: Boolean, findNext: Boolean)`: Searches in-page text.
* `fun stopFinding(clearSelection: Boolean)`: Clears search highlights.

### DOM & JavaScript
* `suspend fun evaluateJavaScript(expression: String): String?`: Evaluates JavaScript asynchronously and returns the result string.
* `suspend fun getHtml(): String`: Extracts full outer HTML of the active frame.
* `suspend fun getText(): String`: Extracts visible rendered plain text.
* `suspend fun getFaviconUrl(): String?`: Resolves active favicon URL.

### Cookies & Security
* `suspend fun getCookies(): Map<String, String>`: Fetches all cookies for the active page.
* `suspend fun getCookie(name: String): String?`: Fetches specific cookie value for active page.
* `fun setCookie(name: String, value: String, domain: String? = null, path: String = "/", isSecure: Boolean = false, isHttpOnly: Boolean = false, expires: Date? = null): Boolean`: Injects cookie.
* `fun clearCookies(): Boolean`: Clears all cookies from store.
* `fun setHostLock(hosts: Set<String>, lockSubresources: Boolean = false)`: Restricts navigation to whitelist.
* `fun clearHostLock()`: Clears active host lock constraints.
* `fun blockMediaAssets(images: Boolean = true, media: Boolean = true, fonts: Boolean = true, stylesheets: Boolean = false)`: Configures asset filter.

### Media, Printing & DevTools
* `fun takeScreenshot(): java.awt.image.BufferedImage?`: Captures viewport image surface.
* `fun printToPdf(filePath: String)`: Exports page to vector PDF.
* `fun simulateClick(x: Int, y: Int)`: Dispatches mouse click events to the rendering surface.
* `fun openDevTools()`: Launches standalone Chrome DevTools window.
* `fun closeDevTools()`: Closes open DevTools window.
* `fun dispose()`: Closes browser window and destroys native peers.

---

## 📡 `KromiumClient`

Manages request routing, handlers, and IPC for a group of browsers.

### Properties & Handlers
* `val rawClient: CefClient`: Underlying CEF client instance.
* `var customUserAgent: String?`: Overrides user agent for this client.
* `var requestInterceptor: KromiumRequestInterceptor?`: Intercepts and mutates requests/headers.
* `var assetFilter: KromiumAssetFilter?`: Filters resource types (media, images, fonts).
* `var hostLock: Set<String>?`: Restricts browsing to allowed hostnames.
* `var hostLockSubresources: Boolean`: Enforces host locking on subresources.
* `var sslErrorPolicy: SslErrorPolicy`: Controls certificate error handling (`Strict`, `AllowDomains`, `AllowAll`).
* `var downloadListener: KromiumDownloadListener?`: Monitors file download progress.
* `var downloadDirectory: java.io.File`: Default download directory.
* `var onBeforeDownloadListener: ((item: KromiumDownloadItem, suggestedFileName: String) -> String?)?`: Intercepts target download destination.
* `var jsDialogListener: KromiumJsDialogListener?`: Intercepts `alert`/`confirm`/`prompt`.
* `var authListener: KromiumAuthListener?`: Supplies HTTP/proxy authentication credentials.
* `var consoleMessageListener: ((KromiumConsoleMessage) -> Unit)?`: Intercepts web console logs.
* `var onPopupListener: ((url: String) -> Boolean)?`: Controls popup windows (`true` = block).
* `var onPermissionRequest: ((url: String) -> Boolean)?`: Controls HTML5 permissions (`true` = grant).

### Methods
* `fun setProxy(proxy: KromiumProxy): Result<Unit>`: Dynamically sets proxy for this client session.
* `fun cancelDownload(downloadId: Int): Boolean`: Cancels an active download.
* `fun pauseDownload(downloadId: Int): Boolean`: Pauses an in-progress download.
* `fun resumeDownload(downloadId: Int): Boolean`: Resumes a paused download.
* `fun addLoadHandler(handler: CefLoadHandler)`: Attaches composite load handler.
* `fun addDisplayHandler(handler: CefDisplayHandler)`: Attaches composite display handler.
* `fun addLifeSpanHandler(handler: CefLifeSpanHandler)`: Attaches composite lifespan handler.
* `fun addContextMenuHandler(handler: CefContextMenuHandler)`: Attaches context menu handler.
* `fun addFocusHandler(handler: CefFocusHandler)`: Attaches focus handler.
* `fun addKeyboardHandler(handler: CefKeyboardHandler)`: Attaches keyboard handler.
* `fun dispose()`: Disposes client and attached handlers.
