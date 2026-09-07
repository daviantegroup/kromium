# JavaScript Bridge & Two-Way IPC

[Documentation Hub](../README.md) &bull; **Guides** &bull; JavaScript & DOM

---

## ⚡ Suspendable JavaScript Evaluation

Kromium features a modern, coroutine-based JavaScript execution pipeline. Instead of callback hell, `evaluateJavaScript` suspends until V8 finishes execution and returns the stringified result directly.

```kotlin
// Suspending call in a coroutine
coroutineScope.launch {
    // Read page title
    val pageTitle: String? = browser.evaluateJavaScript("document.title")
    println("Title: $pageTitle")

    // Complex calculation
    val sum: String? = browser.evaluateJavaScript("1 + 2 + 3")
    println("Sum: $sum") // "6"
}
```

### Configurable Evaluation Timeouts
Prevent rogue JavaScript or infinite loops from hanging your coroutines indefinitely:

```kotlin
import dev.daviante.kromium.presentation.js.JsEvaluator

// Set global JavaScript evaluation timeout (default: 10,000ms)
JsEvaluator.defaultTimeoutMs = 5_000L
```

---

## 🔄 Two-Way IPC (`CefMessageRouter`)

Communicate between web frontend code and your Kotlin desktop application using CEF's message router pipeline.

### 1. Registering the Query Router in Kotlin
Create a `CefMessageRouter` and attach it to your client's raw CEF client:

```kotlin
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter

// 1. Configure the query function name (e.g. window.cefQuery)
val routerConfig = CefMessageRouter.CefMessageRouterConfig("cefQuery", "cefQueryCancel")
val messageRouter = CefMessageRouter.create(routerConfig)

// 2. Attach your IPC request handler
messageRouter.addHandler(object : CefMessageRouterHandlerAdapter() {
    override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: CefQueryCallback?
    ): Boolean {
        if (request == null) return false

        when {
            request.startsWith("FETCH_USER:") -> {
                val userId = request.removePrefix("FETCH_USER:")
                // Send JSON payload back to web JavaScript
                callback?.success("""{"id": "$userId", "name": "Alice Doe", "role": "Admin"}""")
                return true
            }
            request == "GET_APP_VERSION" -> {
                callback?.success("2.0.150")
                return true
            }
            else -> {
                callback?.failure(404, "Unknown query command: $request")
                return true
            }
        }
    }
}, true)

// 3. Register router with client's raw CEF client
client.rawClient.addMessageRouter(messageRouter)
```

### 2. Sending Queries from JavaScript
In your frontend HTML/JavaScript:

```javascript
// Promise-based wrapper around CEF query router
function callDesktopApp(command) {
    return new Promise((resolve, reject) => {
        window.cefQuery({
            request: command,
            persistent: false,
            onSuccess: function(response) {
                resolve(response);
            },
            onFailure: function(errorCode, errorMessage) {
                reject(new Error(errorMessage + " (" + errorCode + ")"));
            }
        });
    });
}

// Call Kotlin from web app
async function loadUserData() {
    try {
        const response = await callDesktopApp("FETCH_USER:42");
        const user = JSON.parse(response);
        console.log("Loaded user:", user.name);
    } catch (err) {
        console.error("Desktop bridge error:", err);
    }
}
```

---

## 📄 DOM Extraction Utilities

Kromium provides dedicated helper functions on `KromiumBrowser` and `KromiumViewState` to extract DOM properties asynchronously without writing manual JavaScript strings:

```kotlin
coroutineScope.launch {
    // Extract full outer HTML of the active frame
    val html: String = browser.getHtml()

    // Extract raw visible text (stripping tags and scripts)
    val visibleText: String = browser.getText()

    // Extract active favicon URL
    val faviconUrl: String? = browser.getFaviconUrl()
}
```
