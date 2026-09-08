# JavaScript Execution & DOM Bridge

Kromium provides bi-directional interoperability between the JVM host and the Chromium V8 JavaScript runtime:
1. **JVM to Web**: Asynchronously execute arbitrary JavaScript and inspect DOM elements.
2. **Web to JVM (IPC)**: Expose native JVM functions that can be invoked from JavaScript via `window.kromiumQuery`.

---

## ⚡ Executing JavaScript from JVM

### Suspending Evaluation (Kotlin Coroutines)

In Kotlin or Compose Desktop, evaluate JavaScript inside a coroutine:

```kotlin
import dev.daviante.kromium.presentation.browser.KromiumBrowser

suspend fun extractPageMetadata(browser: KromiumBrowser) {
    // 1. Evaluate single expression:
    val title = browser.evaluateJavaScript("document.title")
    println("Page title: $title")

    // 2. Evaluate complex IIFE with JSON result:
    val script = """
        (function() {
            return JSON.stringify({
                headingsCount: document.querySelectorAll("h1, h2, h3").length,
                linksCount: document.querySelectorAll("a").length,
                theme: document.body.getAttribute("data-theme") || "light"
            });
        })();
    """.trimIndent()

    val jsonResult = browser.evaluateJavaScript(script)
    println("Metadata JSON: $jsonResult")
}
```

### Asynchronous Evaluation with CompletableFuture (Pure Java)

In pure Java, use `evaluateJavaScriptAsync` which returns a `CompletableFuture<String?>`:

```java
package com.example.js;

import dev.daviante.kromium.KromiumBrowser;
import java.util.concurrent.CompletableFuture;

public final class JsEvaluationJavaDemo {
    public static void runScript(KromiumBrowser browser) {
        CompletableFuture<String> future = browser.evaluateJavaScriptAsync("document.title");

        future.thenAccept(title -> {
            System.out.println("Document title evaluated: " + title);
        }).exceptionally(throwable -> {
            System.err.println("JS evaluation failed: " + throwable.getMessage());
            return null;
        });
    }
}
```

---

## 📄 Built-in DOM Convenience Queries

`KromiumBrowser` provides fast convenience helpers for common DOM inspection tasks:

| Helper (Kotlin Suspending) | Helper (Java `CompletableFuture`) | What it Evaluates |
|:---|:---|:---|
| `browser.getHtml()` | `browser.getHtmlAsync()` | `document.documentElement.outerHTML` |
| `browser.getText()` | `browser.getTextAsync()` | `document.body ? document.body.innerText : ''` |
| `browser.getFaviconUrl()` | `browser.getFaviconUrlAsync()` | Resolves `<link rel="icon">` or returns `null` |

---

## 🌉 Bi-Directional IPC: Calling Native JVM Code from Web Pages

You can expose native JVM methods to web pages without needing a local HTTP/WebSocket server.

### 1. Registering the Native Handler (Kotlin & Java)

```kotlin
// In Kotlin:
client.registerFunction("saveUserData") { jsonPayload ->
    println("Native received: $jsonPayload")
    // Return a response string back to JavaScript:
    """{"status":"saved","timestamp":${System.currentTimeMillis()}}"""
}
```

```java
// In Pure Java:
client.registerFunction("saveUserData", jsonPayload -> {
    System.out.println("Native received: " + jsonPayload);
    return "{\"status\":\"saved\",\"timestamp\":" + System.currentTimeMillis() + "}";
});
```

### 2. Invoking from JavaScript (Web Page / React / Vue)

Chromium exposes `window.kromiumQuery`:

```javascript
// In frontend JavaScript:
function sendToNative() {
    const payload = JSON.stringify({
        action: "saveUserData",
        username: "alice",
        preferences: { darkMode: true }
    });

    window.kromiumQuery({
        request: payload,
        onSuccess: function(response) {
            console.log("Response from JVM:", response);
            const data = JSON.parse(response);
            alert("Saved at: " + data.timestamp);
        },
        onFailure: function(errorCode, errorMessage) {
            console.error("Native call failed (" + errorCode + "): " + errorMessage);
        }
    });
}
```

---

## ⏱️ Timeout Handling & Error Recovery

Long-running or infinite loops in untrusted JavaScript can be bounded by setting a timeout:

```kotlin
try {
    val result = browser.evaluateJavaScript(
        expression = "while(true){}",
        // timeoutMs parameter:
    )
} catch (e: KromiumException.JsEvaluationTimeout) {
    System.err.println("JavaScript execution timed out after ${e.timeoutMs}ms")
}
```
