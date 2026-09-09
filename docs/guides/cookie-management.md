# Cookie & Session Management

Kromium provides complete control over HTTP cookies and session data via `KromiumCookieManager` and `KromiumBrowser`.

---

## 🍪 Cookie Persistence: In-Memory vs. Disk Storage

Cookie persistence is controlled by `cachePath` in `KromiumConfig`:
- **In-Memory Mode (`cachePath = null`)**: Cookies, `sessionStorage`, and cached network assets are kept exclusively in RAM and evaporate immediately when the application process terminates.
- **Persistent Disk Mode (`cachePath = "/path/to/cache"`)**: Cookies with expiration dates are encrypted and safely persisted to the specified disk directory.

```kotlin
val config = KromiumConfig().apply {
    cachePath = File(System.getProperty("user.home"), ".myapp/cache").absolutePath
}
KromiumEngine.getInstance().initialize(config)
```

---

## 🔍 Reading & Inspecting Cookies

### Kotlin Coroutines

```kotlin
// Retrieve all cookies for the current active page as Map<String, String>:
val cookies: Map<String, String> = browser.getCookies()
cookies.forEach { (name, value) ->
    println("Cookie $name = $value")
}

// Retrieve a specific cookie by name:
val sessionToken = browser.getCookie("SESSIONID")
```

### Pure Java with CompletableFuture

```java
import dev.daviante.kromium.KromiumBrowser;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class CookieJavaDemo {
    public static void printSession(KromiumBrowser browser) {
        CompletableFuture<Map<String, String>> future = browser.getCookiesAsync();

        future.thenAccept(cookies -> {
            System.out.println("Session Auth Token: " + cookies.get("authToken"));
        });
    }
}
```

---

## ✍️ Injecting & Modifying Cookies

Inject authentication tokens, feature flags, or tracking bypasses programmatically:

```kotlin
// In Kotlin:
browser.setCookie(
    name = "authToken",
    value = "jwt_token_example_12345",
    domain = ".corp.internal",
    path = "/",
    isSecure = true,
    isHttpOnly = true,
    expires = java.util.Date(System.currentTimeMillis() + 86400000) // 24 hours
)
```

```java
// In Pure Java:
browser.setCookie(
    "authToken",
    "jwt_token_example_12345",
    ".corp.internal",
    "/",
    true,
    true,
    new java.util.Date(System.currentTimeMillis() + 86400000)
);
```

---

## 🧹 Deleting & Clearing Cookies

### Clearing Cookies for Active View

```kotlin
// Clear cookies asynchronously
browser.clearCookiesAsync()
```

### Global Cookie Operations (`KromiumCookieManager`)

For multi-tenant sign-out or session cleanup across all browser instances:

```kotlin
import dev.daviante.kromium.presentation.network.KromiumCookieManager

// Inspect/audit all cookies stored across all domains:
val allCookies: List<CefCookie> = KromiumCookieManager.getAllCookies()
allCookies.forEach { cookie ->
    println("${cookie.domain} -> ${cookie.name} (secure=${cookie.secure}, httpOnly=${cookie.httponly})")
}

// Delete a single cookie:
KromiumCookieManager.deleteCookie("https://example.com", "authToken")

// Clear all cookies globally across the engine:
KromiumCookieManager.clearCookies()

// Flush memory cookie changes to disk immediately:
KromiumCookieManager.flush()
```

---

## 🧼 Complete Privacy Reset (`clearBrowsingData`)

In addition to cookie deletion, Kromium allows purging HTML5 `localStorage` and `sessionStorage` in a single coordinated operation:

```kotlin
// In Kotlin:
browser.clearBrowsingData(clearCookies = true, clearStorage = true)
```

```java
// In Pure Java:
browser.clearBrowsingDataAsync(true, true).thenAccept(success -> {
    System.out.println("Session reset complete: " + success);
});
```
