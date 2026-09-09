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
| `evaluateJavaScript` | `suspend (expression: String, timeoutMs: Long? = null): String?` | Coroutine-based evaluation. Returns stringified result. |
| `evaluateJavaScriptAsync` | `(expression: String, timeoutMs: Long? = null): CompletableFuture<String?>` | Java-idiomatic non-blocking future. |
| `getHtml` / `getHtmlAsync` | `suspend (): String` / `(): CompletableFuture<String>` | Convenience method fetching `document.documentElement.outerHTML`. |
| `getText` / `getTextAsync` | `suspend (): String` / `(): CompletableFuture<String>` | Convenience method fetching `document.body.innerText`. |
| `getFaviconUrl` / `getFaviconUrlAsync` | `suspend (): String?` / `(): CompletableFuture<String?>` | Resolves page favicon URL via DOM `<link rel="icon">` inspection. |

### Web Automation & Interaction DSL

High-level automation methods with smart auto-waiting, React/Angular synthetic event compatibility, and zero raw JavaScript requirements:

| Method | Signature | Description |
|:---|:---|:---|
| `waitForSelector` / `waitForSelectorAsync` | `(selector: String, timeoutMs: Long = 10000): Boolean` | Auto-waits using `MutationObserver` until the CSS selector exists in the DOM. |
| `click` / `clickAsync` | `(selector: String, timeoutMs: Long = 10000): Boolean` | Auto-waits, scrolls into view, and dispatches full pointer/mouse click sequence. |
| `fill` / `fillAsync` | `(selector: String, value: String, timeoutMs: Long = 10000): Boolean` | Auto-waits and sets value via prototype setter (React/Angular/Vue compatible) with input/change events. |
| `type` / `typeAsync` | `(selector: String, text: String, delayMs: Long = 20, timeoutMs: Long = 10000): Boolean` | Types character-by-character with optional delay to trigger live autocomplete dropdowns. |
| `selectOption` / `selectOptionAsync` | `(selector: String, value: String, timeoutMs: Long = 10000): Boolean` | Selects dropdown `<option>` by value or label and emits change events. |
| `getTextContent` / `getTextContentAsync` | `(selector: String, timeoutMs: Long = 10000): String?` | Returns visible inner text or textContent of selector. |
| `getAttribute` / `getAttributeAsync` | `(selector: String, attribute: String, timeoutMs: Long = 10000): String?` | Returns attribute value (e.g. `href`, `src`, `data-*`). |
| `isVisible` / `isVisibleAsync` | `(selector: String): Boolean` | Checks if element exists, has non-zero bounding rect, and is not hidden by CSS. |
| `isChecked` / `isCheckedAsync` | `(selector: String): Boolean` | Checks if checkbox or radio element is checked. |
| `count` / `countAsync` | `(selector: String): Int` | Returns number of elements matching selector. |
| `waitForNetworkIdle` / `waitForNetworkIdleAsync` | `(idleTimeMs: Long = 500, maxTimeoutMs: Long = 15000): Boolean` | Waits until 0 active in-flight network requests exist for `idleTimeMs` (essential for SPAs). |
| `waitForUrl` / `waitForUrlAsync` | `(pattern: String, isRegex: Boolean = false, timeoutMs: Long = 15000): Boolean` | Waits until browser navigates to a URL matching substring or regex. |
| `emulateDesktopEnvironment()` | `(): Unit` | Normalizes headless environment (plugins, languages, chrome runtime, WebGL) to match desktop sessions. |

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

### Off-Screen Rendering (OSR) & HiDPI Configuration

The following APIs are active when the browser is running in OSR mode (Java Swing, JavaFX, or headless):

| Property / Method | Type | Default | Description |
|:---|:---|:---|:---|
| `isOffScreenRendered` | `Boolean` | — | `true` if this browser is running in lightweight OSR mode. |
| `osrPanel` | `KromiumOSRPanel?` | — | Accesses the underlying pure Java2D Swing panel hosting the raster buffer. |
| `scaleFactor` | `Double` | Auto | Gets the active DPI scale or sets a manual override (disables auto-detection). |
| `isAutoDetectScaleFactor` | `Boolean` | `true` | When true, dynamically tracks display scaling from `Graphics2D.getTransform()`. |
| `resetScaleFactorToAuto()` | `Unit` | — | Re-enables automatic display DPI tracking and triggers an immediate resample. |
| `scrollMultiplier` | `Double` | `1.0` | Sensitivity factor for mouse wheel and trackpad scroll deltas. |
| `setRenderingHint(key, value)` | `Unit` | — | Sets a custom Java2D `RenderingHints` key on the OSR panel. |
| `getRenderingHint(key)` | `Any?` | — | Retrieves a custom Java2D `RenderingHints` value from the OSR panel. |
| `setInterpolation(hint)` | `Unit` | Bilinear | Sets image scaling hint (`VALUE_INTERPOLATION_BILINEAR`, `BICUBIC`, `NEAREST_NEIGHBOR`). |
| `bufferedImageType` | `Int` | `TYPE_INT_ARGB_PRE` | Overrides the internal `BufferedImage` raster format. |
| `byteOrder` | `ByteOrder` | `LITTLE_ENDIAN` | Overrides the byte order used when interpreting native Chromium frames. |

### Dynamic Proxy & Network Filtering

```kotlin
val activeProxy: KromiumProxy // Active proxy strategy on this browser's client
fun setProxy(proxy: KromiumProxy): Result<Unit>
fun updateProxy(proxy: KromiumProxy): Boolean // Pure Java friendly boolean return
fun setHostLock(vararg allowedHosts: String, lockSubresources: Boolean = false)
fun blockMediaAssets(images: Boolean = true, media: Boolean = true, fonts: Boolean = true, stylesheets: Boolean = false)
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
