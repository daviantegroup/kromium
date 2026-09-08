# Browser & Client API Reference

This document provides a comprehensive API reference for the primary controller classes in Kromium: **`KromiumBrowser`**, **`KromiumClient`**, and **`KromiumEngine`**.

---

## 🧭 `KromiumBrowser`

Package: `dev.daviante.kromium.presentation.browser.KromiumBrowser`

`KromiumBrowser` controls an active web rendering surface, tab, or window. It manages navigation, JavaScript execution, DOM queries, zoom levels, DevTools, PDF printing, and cookie manipulation.

### UI & Underlying Handles

| Member | Return Type | Description |
|:---|:---|:---|
| `getUiComponent()` / `uiComponent` | `java.awt.Component` | The AWT/Swing component representing the browser surface. Add this to any Swing layout or let Compose host it. |
| `getRawBrowser()` / `rawBrowser` | `org.cef.browser.CefBrowser` | Low-level C++ JCEF browser pointer for advanced native operations. |
| `getClient()` / `client` | `KromiumClient` | The parent `KromiumClient` context managing handlers and message routers. |
| `getWindowChrome()` / `windowChrome` | `KromiumWindowChrome?` | Insets and titlebar dragging configuration if native window chrome is active. |

### Navigation & Content Loading

```kotlin
// Kotlin
fun loadUrl(url: String)
fun loadHtml(html: String, baseUrl: String = "http://kromium.local/")
fun reload()
fun reloadIgnoreCache()
fun goBack()
fun goForward()
fun canGoBack(): Boolean
fun canGoForward(): Boolean
fun stopLoad()
```

```java
// Java
browser.loadUrl("https://example.com");
browser.loadHtml("<h1>Hello Kromium</h1>", "http://kromium.local/");
browser.reload();
browser.reloadIgnoreCache();
if (browser.canGoBack()) browser.goBack();
if (browser.canGoForward()) browser.goForward();
browser.stopLoad();
```

### JavaScript Execution & DOM Queries

| Method | Signature | Description |
|:---|:---|:---|
| `evaluateJavaScript` | `suspend (expression: String): String?` | Coroutine-based evaluation. Returns stringified result. |
| `evaluateJavaScriptAsync` | `(expression: String, timeoutMs: Long? = null): CompletableFuture<String?>` | Java-idiomatic non-blocking future. |
| `getHtml` / `getHtmlAsync` | `suspend (): String` / `(): CompletableFuture<String>` | Convenience method fetching `document.documentElement.outerHTML`. |
| `getText` / `getTextAsync` | `suspend (): String` / `(): CompletableFuture<String>` | Convenience method fetching `document.body.innerText`. |
| `getFaviconUrl` / `getFaviconUrlAsync` | `suspend (): String?` / `(): CompletableFuture<String?>` | Resolves page favicon URL via DOM `<link rel="icon">` inspection. |

### Printing & PDF Generation

```kotlin
// Interactive Native OS Print Dialog
fun print()

// Asynchronous Vector PDF Generation (Kotlin Coroutine)
suspend fun printToPdf(
    targetFile: File,
    settings: KromiumPdfSettings = KromiumPdfSettings.Default
): File

// Asynchronous Vector PDF Generation (Java CompletableFuture)
fun printToPdfAsync(
    targetFile: File,
    settings: KromiumPdfSettings = KromiumPdfSettings.Default
): CompletableFuture<File>
```

### Zoom & Viewport

```kotlin
var zoomLevel: Double // JavaBeans property (getZoomLevel / setZoomLevel)
fun setZoom(level: Double) // 0.0 = 100%, 1.0 = ~120%, -1.0 = ~80%
fun getZoom(): Double
```

### Developer Tools & Diagnostics

```kotlin
fun openDevTools()
fun openDevTools(inspectPoint: java.awt.Point)
fun closeDevTools()
fun takeScreenshot(): java.awt.image.BufferedImage?
```

### In-Page Search

```kotlin
fun find(searchText: String, forward: Boolean = true, matchCase: Boolean = false, findNext: Boolean = false)
fun stopFinding(clearSelection: Boolean)
```

### Lifecycle & Disposal

```kotlin
fun close(force: Boolean = true)
fun onClose(callback: () -> Unit)
```

---

## 🎛️ `KromiumClient`

Package: `dev.daviante.kromium.presentation.browser.KromiumClient`

`KromiumClient` manages browser instances, message routers, network request interceptors, proxy credentials, and permission handlers.

### Browser Factory

```kotlin
fun createBrowser(
    url: String,
    isOffScreenRendering: Boolean = false,
    isTransparent: Boolean = false,
    requestContext: CefRequestContext? = null
): KromiumBrowser
```

### Dynamic Proxy Management

```kotlin
var activeProxy: KromiumProxy
fun setProxy(proxy: KromiumProxy): Result<Unit>
fun updateProxy(proxy: KromiumProxy): Boolean // Pure Java friendly boolean return
```

### JavaScript Bi-Directional IPC (`registerFunction`)

Exposes a JVM callback callable directly from JavaScript via `window.kromiumQuery`:

```kotlin
// Kotlin
client.registerFunction("onNativeAction") { payload ->
    println("Received from web: $payload")
    "OK: $payload"
}
```

```java
// Java
client.registerFunction("onNativeAction", payload -> {
    System.out.println("Received from web: " + payload);
    return "OK: " + payload;
});
```

Web page JavaScript invokes it via:
```javascript
window.kromiumQuery({
    request: JSON.stringify({ action: "save", id: 42 }),
    onSuccess: function(response) { console.log("Response:", response); },
    onFailure: function(errCode, errMsg) { console.error("Error:", errMsg); }
});
```

### Handlers & Listeners

```kotlin
var permissionHandler: KromiumPermissionHandler?
var rememberPermissions: Boolean
fun clearPermissionCache()

var contextMenuHandler: KromiumContextMenuHandler?
fun setContextMenu(block: KromiumMenuBuilder.(KromiumContextMenuContext) -> Unit)

var downloadListener: KromiumDownloadListener?
var downloadDirectory: File
var onBeforeDownloadListener: ((item: KromiumDownloadItem, suggestedFileName: String) -> String?)?

var requestInterceptor: KromiumRequestInterceptor?
var assetFilter: KromiumAssetFilter?

fun setHostLock(vararg allowedHosts: String, lockSubresources: Boolean = false)
fun clearHostLock()

fun addLoadListener(listener: KromiumLoadListener)
fun removeLoadListener(listener: KromiumLoadListener)
```

### Lifecycle

```kotlin
fun dispose()
```

---

## 🚀 `KromiumEngine`

Package: `dev.daviante.kromium.KromiumEngine`

Singleton lifecycle manager for the global Chromium runtime.

### Methods

| Method | Return Type | Description |
|:---|:---|:---|
| `KromiumEngine.getInstance()` | `KromiumEngine` | Returns the global singleton instance. |
| `initialize(config: KromiumConfig)` | `Unit` | Initializes the CEF C++ runtime, unpacks native binaries, and registers custom schemes. Must be invoked before creating clients. |
| `createClient(): KromiumClient` | `KromiumClient` | Spawns a new client context. |
| `registerSchemeHandler(factory)` | `Unit` | Registers a global custom scheme (e.g. `app://`). |
| `dispose()` | `Unit` | Completely shuts down Chromium processes and releases native DLLs/dylibs. |
