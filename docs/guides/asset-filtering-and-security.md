# Virtual Asset Streaming & Security Filters

This guide covers serving local assets, offline Single Page Applications (React, Vue, Svelte), and desktop UI bundles securely using custom virtual schemes (`app://`), along with high-performance resource filtering and ad-blocking.

---

## 📦 Virtual Custom Schemes (`app://`)

Desktop applications often bundle a web frontend (built with React, Next.js static export, Vite, or Vue) inside the application JAR or resources folder.

Loading local assets via `file://` causes severe security restrictions:
- Modern Web APIs (`localStorage`, `IndexedDB`, Web Workers, WebRTC, Service Workers) are disabled or restricted on `file://`.
- Strict CORS policies block local `fetch()` and `XMLHttpRequest`.

Kromium solves this with **Standard Custom Schemes** (`app://`):

```
┌────────────────────────────────────────────────────────┐
│ Browser URL: app://local/index.html                     │
├────────────────────────────────────────────────────────┤
│ • Treated by Chromium as a secure, standard origin     │
│ • Full localStorage & IndexedDB support                │
│ • No open localhost HTTP server or socket ports needed │
│ • Streams directly from JAR or local filesystem memory │
└────────────────────────────────────────────────────────┘
```

### 1. Registering the Scheme During Engine Initialization

Custom schemes must be registered before the Chromium engine starts:

```kotlin
// Kotlin Initialization
val config = KromiumConfig().apply {
    registerScheme("app") {
        isStandard = true
        isSecure = true
        isCorsEnabled = true
        isLocal = true
    }
}
KromiumEngine.getInstance().initialize(config)
```

```java
// Pure Java Initialization
KromiumConfig config = KromiumConfig.builder()
    .registerScheme("app", true, true, true, true)
    .build();
KromiumEngine.getInstance().initialize(config);
```

### 2. Mounting the Asset Handler

Provide a `KromiumSchemeHandler` to stream content from your resources:

```kotlin
import dev.daviante.kromium.presentation.scheme.KromiumSchemeHandler

KromiumEngine.getInstance().registerSchemeHandler("app", "local") { url ->
    val path = url.removePrefix("app://local/").trimStart('/')
    val resourceStream = Thread.currentThread().contextClassLoader.getResourceAsStream("web/$path")
    
    if (resourceStream != null) {
        val mimeType = when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }
        KromiumSchemeResponse(
            data = resourceStream,
            mimeType = mimeType,
            status = 200
        )
    } else {
        KromiumSchemeResponse(status = 404)
    }
}

// Now navigate in the browser:
browser.loadUrl("app://local/index.html")
```

---

## 🚫 Resource Interception & Asset Filtering

For automated data extraction, synthetic monitoring, or enterprise bandwidth optimization, blocking heavyweight assets (images, video, audio, fonts) dramatically reduces bandwidth and CPU utilization.

### One-Line Asset Blocking

```kotlin
// In Compose or Kotlin:
browser.blockMediaAssets(
    images = true,
    media = true,
    fonts = true,
    stylesheets = false
)
```

```java
// In Pure Java:
browser.blockMediaAssets(true, true, true, false);
```

### Fine-Grained Asset Filter (`KromiumAssetFilter`)

```kotlin
client.assetFilter = KromiumAssetFilter(
    blockImages = true,
    blockMedia = true,
    blockFonts = true,
    urlBlockList = listOf(
        "doubleclick.net",
        "google-analytics.com",
        "facebook.net"
    )
)
```
