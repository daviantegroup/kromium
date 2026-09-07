# JavaScript Bridge & DOM Inspection

This guide explains how to execute JavaScript, extract DOM elements, exchange structured data, and handle asynchronous results using Kromium's coroutine-based JavaScript evaluation engine.

---

## Table of Contents

1. [Overview](#overview)
2. [Basic Evaluation (`evaluateJavaScript`)](#basic-evaluation-evaluatejavascript)
3. [How the Bridge Works Under the Hood](#how-the-bridge-works-under-the-hood)
   - [CEF Message Router Mechanism](#cef-message-router-mechanism)
   - [Query ID Sequencing & Message Framing](#query-id-sequencing--message-framing)
   - [Cancellation & Coroutine Cleanup](#cancellation--coroutine-cleanup)
4. [DOM Extraction Helpers](#dom-extraction-helpers)
   - [Extracting Complete HTML (`getHtml`)](#extracting-complete-html-gethtml)
   - [Extracting Visible Text (`getText`)](#extracting-visible-text-gettext)
   - [Extracting Favicon URL (`getFaviconUrl`)](#extracting-favicon-url-getfaviconurl)
5. [Passing and Returning Structured Data (JSON)](#passing-and-returning-structured-data-json)
6. [DOM Manipulation & Click Triggers](#dom-manipulation--click-triggers)
7. [Timeout Configuration & Exception Handling](#timeout-configuration--exception-handling)
8. [Security Guidelines](#security-guidelines)

---

## Overview

Unlike standard WebView wrappers that rely on asynchronous fire-and-forget scripts, Kromium implements a bidirectional, suspendable message bridge using Chromium's native `CefMessageRouter`.

### Key Capabilities:
- **Direct Coroutine Suspension**: Call `evaluateJavaScript()` inside any coroutine and receive the stringified return value directly.
- **Leak-Free Cancellation**: Canceling a Kotlin coroutine automatically unregisters pending callbacks inside `KromiumJsHandler` to prevent memory leaks.
- **Error Capturing**: JavaScript runtime exceptions are caught inside an internal IIFE wrapper and returned with an `ERROR: <message>` prefix.
- **Configurable Timeouts**: Protects against hung scripts (e.g. infinite loops or unresolved promises) using `withTimeoutOrNull`.

---

## Basic Evaluation (`evaluateJavaScript`)

You can evaluate JavaScript from either `KromiumViewState` (in Compose) or `KromiumBrowser` (in raw JVM):

```kotlin
// In Compose
val title: String? = state.evaluateJavaScript("document.title")

// On KromiumBrowser
val title: String? = browser.evaluateJavaScript("document.title")
```

### Simple Expressions

```kotlin
// Read numbers or booleans (returned as String)
val userAgent = browser.evaluateJavaScript("navigator.userAgent")
val screenWidth = browser.evaluateJavaScript("window.innerWidth")
val isDarkMode = browser.evaluateJavaScript("window.matchMedia('(prefers-color-scheme: dark)').matches")

println("Window width: $screenWidth px, Dark mode: $isDarkMode")
```

---

## How the Bridge Works Under the Hood

### CEF Message Router Mechanism
1. During `KromiumClient` initialization, a `CefMessageRouter` is attached with two global JavaScript functions:
   - Query function: `window.kromiumQuery({ request, onSuccess, onFailure })`
   - Cancel function: `window.kromiumQueryCancel(queryId)`
2. `KromiumJsHandler` registers as a handler on this router.

### Query ID Sequencing & Message Framing
When `evaluateJavaScript("...")` is called:
1. `KromiumJsHandler.nextQueryId()` generates a unique atomic ID: `"q_1"`, `"q_2"`, etc.
2. `JsEvaluator.wrapExpression()` wraps the user expression inside an Immediately Invoked Function Expression (IIFE):

```javascript
(function() {
    try {
        var __result = (function() { /* your code here */ })();
        var __str = (__result === undefined || __result === null) ? '' : String(__result);
        window.kromiumQuery({
            request: 'q_42:::' + __str,
            onSuccess: function(response) {},
            onFailure: function(error_code, error_message) {}
        });
    } catch(e) {
        window.kromiumQuery({
            request: 'q_42:::ERROR: ' + e.message,
            onSuccess: function(response) {},
            onFailure: function(error_code, error_message) {}
        });
    }
})();
```

3. `CefBrowser.executeJavaScript()` delivers the script to the Chromium V8 execution context.
4. When executed, `window.kromiumQuery` dispatches IPC back to the Java process.
5. `KromiumJsHandler.onQuery()` parses the `queryId:::payload` message framing, matches the ID in its `pendingCallbacks` map, and resumes the suspended coroutine.

### Cancellation & Coroutine Cleanup
If the calling coroutine is cancelled while waiting for the script to execute:
```kotlin
continuation.invokeOnCancellation {
    handler.removePending(queryId)
}
```
The query registration is immediately dropped, ensuring no abandoned references linger in memory.

---

## DOM Extraction Helpers

Kromium provides pre-built suspendable helpers for common DOM inspection tasks:

### Extracting Complete HTML (`getHtml`)
Extracts the outer HTML of the root document (`document.documentElement.outerHTML`):

```kotlin
val fullHtml: String = state.getHtml()
println("HTML Length: ${fullHtml.length} characters")
```

### Extracting Visible Text (`getText`)
Extracts the rendered, human-readable text of the page body (`document.body.innerText`):

```kotlin
val visibleText: String = state.getText()
println("Page text:\n$visibleText")
```

### Extracting Favicon URL (`getFaviconUrl`)
Searches the DOM for `<link rel="icon">` or `<link rel="shortcut icon">` and returns the resolved absolute URL:

```kotlin
val faviconUrl: String? = browser.getFaviconUrl()
println("Favicon: $faviconUrl")
```

---

## Passing and Returning Structured Data (JSON)

JavaScript primitive evaluations return strings. To pass or receive complex structured objects, use `JSON.stringify` on the JavaScript side and parse the resulting JSON in Kotlin with `kotlinx.serialization`:

### Receiving Structured Data from JavaScript

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PageMetadata(
    val title: String,
    val metaDescription: String,
    val linkCount: Int,
    val headingTags: List<String>
)

suspend fun extractMetadata(browser: KromiumBrowser): PageMetadata? {
    val script = """
        (function() {
            var desc = document.querySelector('meta[name="description"]');
            var headings = Array.from(document.querySelectorAll('h1, h2, h3')).map(h => h.innerText.trim());
            var links = document.querySelectorAll('a').length;

            return JSON.stringify({
                title: document.title,
                metaDescription: desc ? desc.content : '',
                linkCount: links,
                headingTags: headings
            });
        })()
    """.trimIndent()

    val jsonString = browser.evaluateJavaScript(script) ?: return null
    return Json.decodeFromString<PageMetadata>(jsonString)
}
```

### Passing Data from Kotlin into JavaScript

When inserting dynamic variables into JavaScript, encode values into JSON to avoid syntax errors and code injection:

```kotlin
suspend fun fillForm(browser: KromiumBrowser, username: String, theme: String) {
    val safeUser = Json.encodeToString(username)
    val safeTheme = Json.encodeToString(theme)

    val script = """
        document.getElementById('username').value = $safeUser;
        document.body.setAttribute('data-theme', $safeTheme);
    """.trimIndent()

    browser.evaluateJavaScript(script)
}
```

---

## DOM Manipulation & Click Triggers

You can trigger DOM events or interact with forms programmatically:

```kotlin
suspend fun submitLoginForm(browser: KromiumBrowser) {
    val script = """
        (function() {
            var submitBtn = document.querySelector("button[type='submit']");
            if (submitBtn) {
                submitBtn.click();
                return "submitted";
            }
            return "button_not_found";
        })()
    """.trimIndent()

    val result = browser.evaluateJavaScript(script)
    println("Form submission status: $result")
}
```

---

## Timeout Configuration & Exception Handling

### Global Default Timeout
The global default timeout for JavaScript evaluation is 10 seconds (10,000 ms). You can modify this via `JsEvaluator`:

```kotlin
import dev.daviante.kromium.presentation.js.JsEvaluator

// Set global evaluation timeout to 15 seconds
JsEvaluator.defaultTimeoutMs = 15_000L
```

### Low-Level Evaluation with Custom Timeout & Exceptions
To customize timeout or receive explicit exceptions rather than `null`:

```kotlin
import dev.daviante.kromium.presentation.js.JsEvaluator
import dev.daviante.kromium.domain.exception.KromiumException

suspend fun executeWithStrictTimeout(browser: KromiumBrowser) {
    try {
        val result = JsEvaluator.evaluate(
            browser = browser.client.createBrowser().rawBrowser, // or underlying CefBrowser
            handler = browser.client.jsHandler,
            expression = "while(true) {}", // Infinite loop
            timeoutMs = 3_000L,
            throwOnTimeout = true // Throws KromiumException.JsEvaluationTimeout
        )
    } catch (e: KromiumException.JsEvaluationTimeout) {
        println("Script aborted after ${e.timeoutMs}ms")
    }
}
```

---

## Security Guidelines

> [!WARNING]
> **JavaScript Code Injection**:
> The `expression` parameter passed to `evaluateJavaScript()` is evaluated directly in the page's V8 context. Never concatenate unescaped user-supplied input directly into an executable script. Always sanitize inputs or use JSON serialization (e.g. `Json.encodeToString()`) when interpolating dynamic data into JavaScript strings.
