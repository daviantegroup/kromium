# Cookie & Session Management

[Documentation Hub](../README.md) &bull; **Guides** &bull; Cookie Management

---

## 🍪 Asynchronous `KromiumCookieManager`

Kromium wraps Chromium's native asynchronous cookie store with a modern, coroutine-friendly Kotlin singleton API (`KromiumCookieManager`).

All query operations are suspendable and non-blocking, automatically timed out using `timeoutMs` to prevent permanent coroutine suspension when visiting empty cookie stores.

---

## 🔍 Reading Cookies

Retrieve cookies for any HTTP or HTTPS URL as a structured key-value map:

```kotlin
import dev.daviante.kromium.presentation.network.KromiumCookieManager
import kotlinx.coroutines.launch

coroutineScope.launch {
    // 1. Fetch all cookies for a specific destination
    val cookies: Map<String, String> = KromiumCookieManager.getCookies("https://github.com")

    for ((name, value) in cookies) {
        println("Cookie: $name = $value")
    }

    // 2. Fetch a specific cookie by name
    val sessionToken = KromiumCookieManager.getCookie(
        url = "https://example.com",
        name = "session_token"
    )
    println("Session: $sessionToken")
}
```

---

## ✍️ Setting Cookies

Inject session tokens, authentication cookies, or user preferences:

```kotlin
import dev.daviante.kromium.presentation.network.KromiumCookieManager
import java.util.Date

val success: Boolean = KromiumCookieManager.setCookie(
    url = "https://example.com",
    name = "session_token",
    value = "abc123xyz456",
    domain = "example.com", // Optional: defaults to URL host
    path = "/",
    isSecure = true,
    isHttpOnly = true,
    expires = Date(System.currentTimeMillis() + 86400000L) // 24 hours (null = session cookie)
)

if (success) {
    println("Session cookie injected successfully!")
}
```

---

## 🗑️ Deleting & Clearing Cookies

Clear specific session tokens or perform complete user logouts:

```kotlin
// Delete a specific cookie by name for a given URL
val deleted: Boolean = KromiumCookieManager.deleteCookie(
    url = "https://example.com",
    name = "session_token"
)

// Delete all cookies across all domains from Chromium's store
val cleared: Boolean = KromiumCookieManager.clearCookies()
println("Cookies cleared: $cleared")
```

---

## 💻 Browser & Compose State Convenience Methods

Both `KromiumBrowser` and `KromiumViewState` expose contextual cookie convenience methods that automatically target the active page URL:

### In Compose Multiplatform (`KromiumViewState`)
```kotlin
coroutineScope.launch {
    // Reads cookies for the current active page
    val currentCookies = state.getCookies()
    val token = state.getCookie("auth_token")

    // Injects a cookie into the current page domain
    state.setCookie(
        name = "theme",
        value = "dark",
        isSecure = false
    )

    // Wipes all cookies
    state.clearCookies()
}
```

### In Kotlin JVM / Swing (`KromiumBrowser`)
```kotlin
coroutineScope.launch {
    val cookies = browser.getCookies()
    browser.setCookie(name = "user_pref", value = "compact")
    browser.clearCookies()
}
```

---

## ⏱️ Timeout Configuration

If a URL has no cookies, Chromium's native cookie visitor does not invoke any callbacks. To avoid suspending coroutines indefinitely, `KromiumCookieManager` applies a configurable timeout:

```kotlin
// Set global cookie lookup timeout (default: 2,000ms)
KromiumCookieManager.timeoutMs = 1_000L
```
