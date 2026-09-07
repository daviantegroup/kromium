# Cookie Management Reference

[Kromium Documentation](README.md) &bull; [Interactive Web Portal](https://kromium.daviante.dev/#/docs/cookie-management)

This guide details Kromium's cookie management system via `KromiumCookieManager`, enabling coroutine-based cookie retrieval, insertion, deletion, and disk synchronization.

---

## Table of Contents

1. [Overview](#overview)
2. [API Reference Summary](#api-reference-summary)
3. [Reading Cookies](#reading-cookies)
   - [Reading All Cookies for a URL (`getCookies`)](#reading-all-cookies-for-a-url-getcookies)
   - [Reading a Specific Cookie (`getCookie`)](#reading-a-specific-cookie-getcookie)
   - [HttpOnly Cookies & Security](#httponly-cookies--security)
4. [Setting Cookies (`setCookie`)](#setting-cookies-setcookie)
   - [Session vs Persistent Cookies](#session-vs-persistent-cookies)
   - [Secure & HttpOnly Flags](#secure--httponly-flags)
5. [Deleting Cookies](#deleting-cookies)
   - [Deleting a Single Cookie (`deleteCookie`)](#deleting-a-single-cookie-deletecookie)
   - [Clearing All Cookies (`clearCookies`)](#clearing-all-cookies-clearcookies)
6. [Flushing to Disk (`flush`)](#flushing-to-disk-flush)
7. [Timeout Configuration & Concurrency](#timeout-configuration--concurrency)
8. [Example: Authenticated Session Export & Rehydration](#example-authenticated-session-export--rehydration)

---

## Overview

In traditional JCEF, interacting with the Chromium cookie store requires implementing low-level asynchronous callback interfaces (`CefCookieVisitor`, `CefSetCookieCallback`, etc.).

`KromiumCookieManager` provides a Kotlin Coroutines API wrapping Chromium's native global cookie store:
- **Direct Coroutine Returns**: No callback nesting.
- **Safety Against Hanging Visitors**: If a domain contains zero cookies, Chromium's native visitor may never invoke callback methods. Kromium guards all retrieval operations with `withTimeoutOrNull` to guarantee resumption.
- **Cross-Domain Isolation**: Supports scoping cookies by domain, path, secure flags, and expiration timestamps.

---

## API Reference Summary

All functions are available statically on the `KromiumCookieManager` singleton:

| Function | Signature | Return | Description |
|---|---|---|---|
| `getCookies` | `suspend getCookies(url, includeHttpOnly)` | `Map<String, String>` | Retrieves all cookies for a given URL as key-value pairs. |
| `getCookie` | `suspend getCookie(url, name)` | `String?` | Retrieves a single cookie value by name. |
| `setCookie` | `setCookie(url, name, value, domain, path, isSecure, isHttpOnly, expires)` | `Boolean` | Inserts or updates a cookie in the store. |
| `deleteCookie`| `deleteCookie(url, name)` | `Boolean` | Deletes a specific cookie for the given URL. |
| `clearCookies` | `clearCookies()` | `Boolean` | Deletes all cookies across all domains in the store. |
| `flush` | `flush()` | `Boolean` | Flushes in-memory cookies to disk storage. |

---

## Reading Cookies

### Reading All Cookies for a URL (`getCookies`)

```kotlin
import dev.daviante.kromium.presentation.network.KromiumCookieManager

suspend fun printSessionCookies() {
    val cookies: Map<String, String> = KromiumCookieManager.getCookies("https://github.com")

    cookies.forEach { (name, value) ->
        println("$name = $value")
    }
}
```

### Reading a Specific Cookie (`getCookie`)

```kotlin
suspend fun checkAuthToken(): String? {
    val token = KromiumCookieManager.getCookie(
        url = "https://my-app.internal",
        name = "session_id"
    )
    return token
}
```

### HttpOnly Cookies & Security
By default, `getCookies()` sets `includeHttpOnly = true`, allowing your desktop application to inspect session tokens that are inaccessible to JavaScript `document.cookie` in the browser context:

```kotlin
// Only read cookies accessible to JavaScript (exclude HttpOnly):
val jsAccessibleCookies = KromiumCookieManager.getCookies(
    url = "https://example.com",
    includeHttpOnly = false
)
```

---

## Setting Cookies (`setCookie`)

To inject cookies into Chromium before or during a browsing session:

```kotlin
val success: Boolean = KromiumCookieManager.setCookie(
    url = "https://example.com",
    name = "auth_token",
    value = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    domain = "example.com",   // Optional: defaults to host from URL
    path = "/",               // Scope path (defaults to "/")
    isSecure = true,          // Restrict to HTTPS connections
    isHttpOnly = true,        // Hide from JavaScript document.cookie
    expires = null            // Expiration Date. Null creates a session cookie
)
```

### Parameters Reference:
- `url`: Target URL where the cookie is valid.
- `name`: Cookie key name.
- `value`: Cookie value string.
- `domain`: Cookie domain. If omitted, Kromium automatically parses the host from `url`.
- `path`: URL subpath scope (default is `"/"`).
- `isSecure`: If `true`, browser only transmits this cookie over HTTPS.
- `isHttpOnly`: If `true`, inaccessible to browser scripts via `document.cookie`.
- `expires`: A `java.util.Date` timestamp. When `null`, the cookie is a session cookie that expires when the browser process ends.

---

## Deleting Cookies

### Deleting a Single Cookie (`deleteCookie`)

Deletes a specific cookie for the given URL:

```kotlin
val deleted: Boolean = KromiumCookieManager.deleteCookie(
    url = "https://example.com",
    name = "session_token"
)
```

### Clearing All Cookies (`clearCookies`)

Wipes the entire Chromium cookie store:

```kotlin
val cleared: Boolean = KromiumCookieManager.clearCookies()
println("All cookies cleared: $cleared")
```

---

## Flushing to Disk (`flush`)

Chromium maintains an in-memory write buffer for cookie mutations. When using a persistent disk cache (`KromiumConfig.cachePath != null`), call `flush()` to immediately serialize pending changes to disk:

```kotlin
KromiumCookieManager.setCookie("https://example.com", "pref", "dark_mode")

// Ensure writes are committed to disk before shutting down
KromiumCookieManager.flush()
```

---

## Timeout Configuration & Concurrency

When calling `getCookies()`, Kromium registers an internal `CefCookieVisitor`. If Chromium's cookie store has no cookies recorded for the domain, or if native callbacks are delayed, the operation will time out after `KromiumCookieManager.timeoutMs` (default: 5,000 ms) and safely return `emptyMap()` without blocking or hanging your coroutines:

```kotlin
// Customize cookie query timeout to 2 seconds
KromiumCookieManager.timeoutMs = 2_000L
```

---

## Example: Authenticated Session Export & Rehydration

Save user session cookies to disk (e.g. for persisting logins across application restarts without re-entering credentials):

```kotlin
import dev.daviante.kromium.presentation.network.KromiumCookieManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// 1. Export session cookies to JSON file
suspend fun exportSession(url: String, outputFile: File) {
    val cookies = KromiumCookieManager.getCookies(url)
    val json = Json.encodeToString(cookies)
    outputFile.writeText(json)
    println("Exported ${cookies.size} cookies to ${outputFile.name}")
}

// 2. Rehydrate session cookies from JSON file
fun rehydrateSession(url: String, inputFile: File) {
    if (!inputFile.exists()) return

    val json = inputFile.readText()
    val cookies = Json.decodeFromString<Map<String, String>>(json)

    for ((name, value) in cookies) {
        KromiumCookieManager.setCookie(
            url = url,
            name = name,
            value = value,
            isSecure = url.startsWith("https://")
        )
    }

    KromiumCookieManager.flush()
    println("Rehydrated ${cookies.size} cookies for $url")
}
```

---

[← Return to Documentation Index](README.md) &bull; [Visit Online Documentation](https://kromium.daviante.dev/#/docs/cookie-management)
