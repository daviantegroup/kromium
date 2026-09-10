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

For automated data extraction, synthetic monitoring, background task automation, or bandwidth optimization, filtering network assets dramatically cuts bandwidth, network requests, and CPU overhead.

### Presets

Kromium provides standard presets tailored for different headless automation requirements:

| Preset | Blocked Assets | Preserved Assets | Best Used For |
|:---|:---|:---|:---|
| **`KromiumAssetFilter.MEDIA_ONLY`** | Images, video, audio, web fonts | **Stylesheets (`.css`)**, scripts, HTML | **Recommended for content extraction & automated testing**. Retaining CSS is vital for SPAs, computed layout metrics, and interactive verification challenges (Cloudflare Turnstile, reCAPTCHA). |
| **`KromiumAssetFilter.AGGRESSIVE_HEADLESS`** (formerly `ALL_BLOCKED`) | Images, video, audio, web fonts, **stylesheets (`.css`)** | Scripts, HTML | Raw text and HTML extraction where visual styling and layout are unnecessary. |

```kotlin
// Apply preset directly to browser or client
browser.assetFilter = KromiumAssetFilter.MEDIA_ONLY
// Or in Compose:
state.assetFilter = KromiumAssetFilter.MEDIA_ONLY
```

---

### Fine-Grained Custom Asset Blocking (`blockAssets`)

You can block standard categories along with custom file extensions, URL patterns/keywords, CEF resource types, and dynamic programmatic predicates:

```kotlin
browser.blockAssets(
    images = true,
    media = true,
    fonts = true,
    stylesheets = false,
    customExtensions = setOf(".wasm", ".pdf"),
    customUrlPatterns = setOf("doubleclick.net", "analytics", "tracker.js"),
    customResourceTypes = setOf(CefRequest.ResourceType.RT_PING, CefRequest.ResourceType.RT_CSP_REPORT),
    customFilter = { request ->
        // Block requests containing specific query parameters or headers
        request.url?.contains("ad_id=") == true
    }
)
```

---

### Strict Allowlist Mode (`allowOnlyAssets`)

When you only want to allow specific, trusted assets and block everything else by default, use allowlist mode. This provides a strict, zero-trust network filter with **no duplicate or contradictory options**:

```kotlin
// Only allow JavaScript and CSS; all other assets (images, fonts, media, trackers) are blocked
browser.allowOnlyAssets(
    extensions = KromiumAssetFilter.SCRIPT_EXTENSIONS + KromiumAssetFilter.STYLESHEET_EXTENSIONS,
    urlPatterns = setOf("trusted-cdn.com", "api.example.com")
)
```

#### Reusable Extension Sets
Kromium exposes standard, public sets on `KromiumAssetFilter`:
- `KromiumAssetFilter.IMAGE_EXTENSIONS` (`.png`, `.jpg`, `.webp`, `.svg`, `.ico`, etc.)
- `KromiumAssetFilter.MEDIA_EXTENSIONS` (`.mp4`, `.webm`, `.mp3`, `.wav`, etc.)
- `KromiumAssetFilter.FONT_EXTENSIONS` (`.woff`, `.woff2`, `.ttf`, etc.)
- `KromiumAssetFilter.STYLESHEET_EXTENSIONS` (`.css`)
- `KromiumAssetFilter.SCRIPT_EXTENSIONS` (`.js`, `.mjs`)

#### Top-Level Navigation Safety (`allowMainFrame`)
By default, `allowMainFrame = true` in allowlist mode. This ensures the root HTML document navigation (`RT_MAIN_FRAME`) is permitted so the page can load while its subresources remain strictly filtered. Set `allowMainFrame = false` if you also want to subject the top-level document URL to the allowlist rules.

---

### Allowlist Bypass in Blocklist Mode

In default `BLOCKLIST` mode, configured allow rules serve as **bypass exceptions** that unblock specific requests:

```kotlin
// Block all images EXCEPT the CAPTCHA and brand logo
browser.blockAssets(
    images = true,
    allowedUrlPatterns = setOf("captcha.png", "logo.svg")
)
```

---

## 🔒 Frame-Aware Host Locking (`setHostLock`)

Host locking restricts browser navigations strictly to whitelisted hostnames/domains. Navigations to unauthorized domains are rejected.

### Subframe & Verification Widget Handling

Embedded verification challenges (such as **Cloudflare Turnstile**, **Google reCAPTCHA**, or OAuth login widgets) navigate inside embedded `<iframe>` elements (subframes). By default, `hostLock` only guards the top-level main frame (`frame.isMain == true`), ensuring these widgets function properly without breaking domain locks.

```kotlin
// Lock top-level navigations to example.com, while allowing embedded Turnstile/reCAPTCHA iframes
browser.setHostLock("example.com", "api.example.com")

// Optionally restrict subresources (fetch/XHR) as well:
browser.setHostLock("example.com", lockSubresources = true)

// Explicitly restrict subframes too (blocks third-party iframes):
browser.setHostLock("example.com", lockSubresources = true, lockSubframes = true)

// Clear host lock
browser.clearHostLock()
```
