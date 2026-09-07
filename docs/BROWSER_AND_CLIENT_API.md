# Browser & Client API Reference

This document provides a detailed reference for `KromiumClient` and `KromiumBrowser`, enabling direct usage of Kromium in Kotlin JVM, headless environments, or traditional Swing/AWT desktop applications without Compose.

---

## Table of Contents

1. [Overview](#overview)
2. [Obtaining Clients](#obtaining-clients)
   - [Synchronous: `Kromium.newClient()`](#synchronous-kromiumnewclient)
   - [Asynchronous: `Kromium.awaitClient()`](#asynchronous-kromiumawaitclient)
3. [`KromiumClient` Reference](#kromiumclient-reference)
   - [Configuration Properties](#configuration-properties)
   - [Browser Creation](#browser-creation)
   - [Direct JCEF Handler Registration](#direct-jcef-handler-registration)
   - [Disposal](#disposal)
4. [`KromiumBrowser` Reference](#kromiumbrowser-reference)
   - [UI Integration (`uiComponent`)](#ui-integration-uicomponent)
   - [Navigation Methods](#navigation-methods)
   - [In-Page Search (`find`)](#in-page-search-find)
   - [Loading Raw HTML (`loadHtml`)](#loading-raw-html-loadhtml)
   - [Zoom Controls](#zoom-controls)
   - [DevTools Inspection](#devtools-inspection)
   - [Mouse Click Simulation](#mouse-click-simulation)
   - [PDF Export (`printToPdf`)](#pdf-export-printtopdf)
   - [Screenshot Capture (`takeScreenshot`)](#screenshot-capture-takescreenshot)
   - [DOM Extraction (`getHtml`, `getText`, `getFaviconUrl`)](#dom-extraction-gethtml-gettext-getfaviconurl)
5. [Complete Swing/AWT Integration Example](#complete-swingawt-integration-example)

---

## Overview

The `kromium-core` module provides two primary presentation abstractions:

- **`KromiumClient`**: Wraps the native `CefClient`. Owns the message routing system, request interception pipelines, SSL policies, and dialog listeners. A single client can manage multiple browser instances sharing the same settings.
- **`KromiumBrowser`**: Wraps a single `CefBrowser` instance. Provides tab-level navigation, DOM extraction, JavaScript evaluation, zoom controls, DevTools inspection, screenshots, and PDF printing.

---

## Obtaining Clients

### Synchronous: `Kromium.newClient()`
Assumes `Kromium.initialize()` has already completed and `Kromium.isReady == true`:

```kotlin
import dev.daviante.kromium.presentation.browser.Kromium
import dev.daviante.kromium.presentation.browser.KromiumClient

val client: KromiumClient = Kromium.newClient()
```
> Throws `KromiumException.NotInitialized` if the engine has not started, or `KromiumException.Disposed` if the engine was shut down.

### Asynchronous: `Kromium.awaitClient()`
Suspends until the engine reaches `KromiumState.Ready`, then instantiates the client:

```kotlin
suspend fun setupBrowser() {
    val client: KromiumClient = Kromium.awaitClient()
    val browser = client.createBrowser("https://example.com")
}
```

---

## `KromiumClient` Reference

### Configuration Properties

| Property | Type | Default | Description |
|---|---|---|---|
| `customUserAgent` | `String?` | `null` | Overrides the HTTP `User-Agent` header for all requests emitted by browsers under this client. |
| `requestInterceptor` | `KromiumRequestInterceptor?` | `null` | Functional interface to inspect/mutate headers or block outgoing network requests. |
| `shouldOverrideUrlLoading` | `((url: String) -> Boolean)?` | `null` | Invoked on `onBeforeBrowse`. Return `true` to cancel browser navigation (e.g. for custom URI schemes). |
| `sslErrorPolicy` | `SslErrorPolicy` | `Strict` | Policy for handling SSL/TLS certificate errors (`Strict`, `AllowDomains`, `AllowAll`). |
| `downloadListener` | `KromiumDownloadListener?` | `null` | Receives updates on file download progress and completion. |
| `jsDialogListener` | `KromiumJsDialogListener?` | `null` | Intercepts JavaScript `alert()`, `confirm()`, and `prompt()` dialogs. |
| `consoleMessageListener` | `((KromiumConsoleMessage) -> Unit)?` | `null` | Receives messages logged via JavaScript `console.log`, `console.warn`, etc. |
| `authListener` | `KromiumAuthListener?` | `null` | Supplies credentials for HTTP Basic/Digest and proxy authentication challenges. |
| `onPopupListener` | `((url: String) -> Boolean)?` | `null` | Intercepts `window.open` calls. Defaults to blocking popups if unhandled. |
| `onPermissionRequest` | `((url: String) -> Boolean)?` | `null` | Handles permission requests (e.g. geolocation, notifications). |
| `enableContextMenus` | `Boolean` | `true` | When `false`, clears the Chromium context menu on right-click. |
| `loadErrorListener` | `((KromiumLoadError) -> Unit)?` | `null` | Triggered when main-frame page loading fails (e.g. connection refused, DNS error). |

### Browser Creation

```kotlin
val browser: KromiumBrowser = client.createBrowser(
    url = "https://example.com",     // Initial URL (defaults to "about:blank")
    isOffScreenRendered = false,    // Off-screen rendering (defaults to false for windowed AWT/Swing)
    isTransparent = false,          // Background transparency
    requestContext = null           // Optional custom CefRequestContext
)

// Convenience overload:
val simpleBrowser = client.createBrowser("https://example.com", isTransparent = false)
```

### Direct JCEF Handler Registration

For low-level CEF extension, `KromiumClient` allows adding standard JCEF handlers:

```kotlin
client.addLoadHandler(handler: CefLoadHandler)
client.removeLoadHandler()

client.addDisplayHandler(handler: CefDisplayHandler)
client.removeDisplayHandler()

client.addLifeSpanHandler(handler: CefLifeSpanHandler)
client.removeLifeSpanHandler()

client.addContextMenuHandler(handler: CefContextMenuHandler)
client.removeContextMenuHandler()

client.addFocusHandler(handler: CefFocusHandler)
client.removeFocusHandler()

client.addKeyboardHandler(handler: CefKeyboardHandler)
client.removeKeyboardHandler()
```

### Disposal

```kotlin
client.dispose()
```
Releases the underlying native `CefClient` and unregisters message routers.

---

## `KromiumBrowser` Reference

### UI Integration (`uiComponent`)
Exposes the native Java AWT component representing the browser canvas:

```kotlin
val awtComponent: java.awt.Component = browser.uiComponent
```
This component can be added directly into any Swing or AWT layout (`BorderLayout`, `BoxLayout`, etc.).

### Navigation Methods

```kotlin
// Load an address
browser.loadUrl("https://news.ycombinator.com")

// Current URL
val currentUrl: String? = browser.url

// Navigation History
if (browser.canGoBack()) {
    browser.goBack()
}
if (browser.canGoForward()) {
    browser.goForward()
}

// Reloading
browser.reload()             // Standard reload
browser.reloadIgnoreCache()  // Hard reload (bypasses disk/memory cache)

// Stop loading
browser.stopLoad()
```

### In-Page Search (`find`)

Perform text searches inside the rendered page:

```kotlin
// Search forward, case-insensitive, finding next occurrence
browser.find(
    searchText = "Chromium",
    forward = true,
    matchCase = false,
    findNext = true
)

// Stop finding and clear selection highlights
browser.stopFinding(clearSelection = true)
```

### Loading Raw HTML (`loadHtml`)

Renders arbitrary HTML content without using restrictive `data:` URIs:

```kotlin
val html = """
    <!DOCTYPE html>
    <html>
      <head><title>Offline Dashboard</title></head>
      <body>
        <h1>System Status: OK</h1>
        <p>This content was loaded in-memory.</p>
      </body>
    </html>
""".trimIndent()

browser.loadHtml(html, baseUrl = "http://my-internal-app/")
```
> **How it works**: Kromium creates a UUID-backed local URL under `baseUrl` and stores the HTML in a thread-safe payload map. When CEF requests the resource, `KromiumHtmlResourceHandler` intercepts the request and streams the HTML bytes directly from memory with HTTP 200 and `text/html` headers.

### Zoom Controls

Adjust the browser magnification factor:

```kotlin
// 0.0 is default (100%)
// 1.0 is ~120%
// -1.0 is ~80%
browser.setZoom(1.5)

val currentZoom: Double = browser.getZoom()
```

### DevTools Inspection

Inspect DOM, CSS, network waterfall, and console logs using Chromium's native DevTools:

```kotlin
// Opens an AWT Frame containing the Chromium DevTools UI
browser.openDevTools()

// Closes the DevTools window
browser.closeDevTools()
```

### Mouse Click Simulation

Dispatches synthetic AWT mouse events (`MOUSE_PRESSED`, `MOUSE_RELEASED`, `MOUSE_CLICKED`) at the specified pixel coordinates:

```kotlin
browser.simulateClick(x = 150, y = 300)
```

### PDF Export (`printToPdf`)

Renders the entire web page to a local PDF file using default A4 print settings:

```kotlin
browser.printToPdf("C:/Users/Reports/page_export.pdf")
```

### Screenshot Capture (`takeScreenshot`)

Paints the active browser component into an in-memory `BufferedImage`:

```kotlin
import javax.imageio.ImageIO
import java.io.File

val image: BufferedImage? = browser.takeScreenshot()
if (image != null) {
    ImageIO.write(image, "PNG", File("screenshot.png"))
}
```

### DOM Extraction (`getHtml`, `getText`, `getFaviconUrl`)

Convenience coroutine-based functions to read page data:

```kotlin
// Complete document HTML (outerHTML)
val fullHtml: String = browser.getHtml()

// Human-readable visible text (body.innerText)
val pageText: String = browser.getText()

// Resolves <link rel="icon"> or <link rel="shortcut icon">
val faviconUrl: String? = browser.getFaviconUrl()
```

---

## Complete Swing/AWT Integration Example

The following standalone application initializes Kromium and renders a web browser inside a standard Swing `JFrame`:

```kotlin
import dev.daviante.kromium.domain.config.KromiumConfig
import dev.daviante.kromium.domain.model.KromiumState
import dev.daviante.kromium.presentation.browser.Kromium
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities

fun main() = runBlocking {
    // 1. Initialize Kromium
    Kromium.initialize {
        windowlessRendering = false // Can be false for standard Swing windows
        remoteDebuggingPort = 9222
    }

    // 2. Wait for engine to be ready
    Kromium.state.first { it is KromiumState.Ready }

    // 3. Create Client and Browser on the Swing EDT
    SwingUtilities.invokeLater {
        val client = Kromium.newClient()
        val browser = client.createBrowser("https://github.com")

        // 4. Build Swing Window
        val frame = JFrame("Kromium Swing Browser")
        frame.setSize(1280, 800)
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

        val topBar = JPanel(BorderLayout())
        val urlField = JTextField("https://github.com")
        val goButton = JButton("Go")
        val devToolsButton = JButton("Inspect")

        goButton.addActionListener {
            browser.loadUrl(urlField.text)
        }
        devToolsButton.addActionListener {
            browser.openDevTools()
        }

        topBar.add(urlField, BorderLayout.CENTER)
        topBar.add(goButton, BorderLayout.EAST)
        topBar.add(devToolsButton, BorderLayout.WEST)

        frame.contentPane.add(topBar, BorderLayout.NORTH)
        frame.contentPane.add(browser.uiComponent, BorderLayout.CENTER)

        // Clean up on window close
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                browser.dispose()
                client.dispose()
            }
        })

        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}
```
