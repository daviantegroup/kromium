# Handlers, Listeners & Callbacks

[Documentation Hub](../README.md) &bull; **API Reference** &bull; Handlers & Events

---

## 🔀 Composite Handler Multiplexing

In raw JCEF, calling `client.addLoadHandler(...)` or `client.addDisplayHandler(...)` silently replaces previous handlers if only one listener is supported natively.

Kromium features **Composite Multiplexing**: multiple listeners can be attached to the same client concurrently without stomping on each other. When an event fires, all registered handlers are called in registration order.

```kotlin
// Thread-safe composite listener registration
client.addLoadHandler(myLoggingHandler)
client.addLoadHandler(myMetricsHandler)

// Clean removal
client.removeLoadHandler(myLoggingHandler)
```

---

## 📋 Handler Catalog

### 1. `CefLoadHandler`
Tracks page loading progress, HTTP status codes, and frame readiness:

```kotlin
client.addLoadHandler(object : CefLoadHandlerAdapter() {
    override fun onLoadingStateChange(
        browser: CefBrowser?,
        isLoading: Boolean,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        println("Is Loading: $isLoading | Back: $canGoBack | Forward: $canGoForward")
    }

    override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
        if (frame?.isMain == true) {
            println("Main page loaded with HTTP status: $httpStatusCode")
        }
    }

    override fun onLoadError(
        browser: CefBrowser?,
        frame: CefFrame?,
        errorCode: CefLoadHandler.ErrorCode?,
        errorText: String?,
        failedUrl: String?
    ) {
        println("Load failed: $failedUrl ($errorCode - $errorText)")
    }
})
```

### 2. `CefDisplayHandler`
Monitors title, address, favicon, and tooltip changes:

```kotlin
client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
    override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {
        println("URL changed to: $url")
    }

    override fun onTitleChange(browser: CefBrowser?, title: String?) {
        println("Page title: $title")
    }

    override fun onTooltip(browser: CefBrowser?, text: String?): Boolean {
        // Return true to suppress default tooltip
        return false
    }
})
```

### 3. `CefContextMenuHandler`
Customize or disable context menus (right-click):

```kotlin
// Disable context menus entirely
client.enableContextMenus = false

// Or customize via adapter
client.addContextMenuHandler(object : CefContextMenuHandlerAdapter() {
    override fun onBeforeContextMenu(
        browser: CefBrowser?,
        frame: CefFrame?,
        params: CefContextMenuParams?,
        model: CefMenuModel?
    ) {
        // Clear default items and add custom actions
        model?.clear()
        model?.addItem(1001, "Copy Link Location")
    }
})
```

### 4. `CefLifeSpanHandler` (Popup Interception)
Control how popups and `window.open` requests are handled:

```kotlin
client.onPopupListener = { targetUrl ->
    println("Intercepted popup: $targetUrl")
    // Return true to block popup, false to allow opening in new window
    true
}
```

### 5. `CefPermissionHandler`
Handle HTML5 permission prompts (Microphone, Camera, Geolocation):

```kotlin
client.onPermissionRequest = { requestingUrl ->
    // Return true to grant permission, false to deny
    requestingUrl.startsWith("https://trusted.corp")
}
```
