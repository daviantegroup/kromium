# Kromium

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.7.3-purple.svg?logo=jetpackcompose)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Website](https://img.shields.io/badge/Website-kromium.daviante.dev-2ea44f.svg)](https://kromium.daviante.dev)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

**Kromium** ([kromium.daviante.dev](https://kromium.daviante.dev)) is a modern, high-performance Chromium Embedded Framework (CEF) library for **Kotlin** and **Compose Multiplatform Desktop** (Windows, macOS, Linux).

Built directly on pure **JCEF** (`jcef.jar`), Kromium features zero-config automated engine downloads from JetBrains Runtime releases, reactive `StateFlow` download & initialization progress, coroutine-based JavaScript evaluation, cookie management, network interception, and a first-class declarative `@Composable` component (`KromiumView`).

---

## 📚 Complete Documentation

Comprehensive guides covering every feature in depth are available in the [`docs/`](docs/) directory:

| Document | Description |
|---|---|
| **[Architecture & Engine Lifecycle](docs/ARCHITECTURE_AND_LIFECYCLE.md)** | Automated JBR bundle download, SHA-256 checksums, Zip-Slip-safe extraction, platform detection, `KromiumState` state machine, `KromiumConfig`, and shutdown hooks. |
| **[Compose Multiplatform Desktop Guide](docs/COMPOSE_DESKTOP_GUIDE.md)** | Declarative `KromiumView`, `rememberKromiumState`, `KromiumViewState`, observable state properties (`url`, `title`, `isLoading`, `canGoBack`, `canGoForward`), and a complete multi-tab browser example. |
| **[Browser & Client API Reference](docs/BROWSER_AND_CLIENT_API.md)** | Direct JVM / Swing integration via `KromiumClient` and `KromiumBrowser`, navigation, in-page text search, zoom control, DevTools, screenshots, and PDF printing. |
| **[JavaScript Bridge & DOM Inspection](docs/JAVASCRIPT_BRIDGE.md)** | Coroutine-based `evaluateJavaScript`, cancellation cleanup, timeout handling, JSON data exchange, DOM extraction (`getHtml`, `getText`, `getFaviconUrl`), and click simulation. |
| **[Network Interception & Security](docs/NETWORK_AND_SECURITY.md)** | `KromiumRequestInterceptor`, header injection, ad/tracker blocking, navigation overrides (`shouldOverrideUrlLoading`), `SslErrorPolicy`, `KromiumProxy`, and Chromium sandboxing. |
| **[Cookie Management Reference](docs/COOKIE_MANAGEMENT.md)** | Suspendable `KromiumCookieManager`: asynchronous `getCookies()`, `getCookie()`, `setCookie()`, `deleteCookie()`, `clearCookies()`, disk flushing, and session rehydration. |
| **[Handlers, Listeners & Events](docs/HANDLERS_AND_EVENTS.md)** | Event callbacks: HTTP/Proxy authentication (`KromiumAuthListener`), JS modal dialogs (`KromiumJsDialogListener`), downloads (`KromiumDownloadListener`), console logs, and load errors. |
| **[Error Handling & Logging](docs/ERROR_HANDLING_AND_LOGGING.md)** | The complete sealed `KromiumException` catalog, recovery strategies, and the pluggable `KromiumLogger` interface (SLF4J, Logback, Kermit). |

---

## Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│                       Your Application                          │
│        (Compose Multiplatform Desktop / Kotlin JVM)             │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
       ┌──────────────────┐            ┌──────────────────┐
       │  kromium-compose │            │   kromium-core   │
       │  (KromiumView)   │            │ (KromiumClient,  │
       │(KromiumViewState)│            │  KromiumBrowser) │
       └─────────┬────────┘            └─────────┬────────┘
                 │                               │
                 └───────────────┬───────────────┘
                                 ▼
                     ┌───────────────────────┐
                     │   Kromium Singleton   │
                     │  (StateFlow lifecycle,│
                     │   Mutex-safe startup) │
                     └───────────┬───────────┘
                                 ▼
                     ┌───────────────────────┐
                     │    CefBootstrapper    │
                     │  (Native lib loading, │
                     │   JAWT, SwiftShader)  │
                     └───────────┬───────────┘
                                 ▼
                     ┌───────────────────────┐
                     │   Pure JCEF Runtime   │
                     │      (jcef.jar)       │
                     └───────────┬───────────┘
                                 ▼
         ┌───────────────────────────────────────────────┐
         │          Platform Native Binaries             │
         │  Windows (jcef.dll, libcef.dll)               │
         │  macOS (Chromium Embedded Framework.framework)│
         │  Linux (libcef.so, libjcef.so)                │
         └───────────────────────────────────────────────┘
```

---

## Modules

```
kromium/
├── kromium-core       // Pure Kotlin/JVM engine, downloader, JCEF lifecycle, cookies, and network
├── kromium-compose    // Compose Multiplatform desktop UI integration (KromiumView)
└── kromium-demo       // Showcase browser app with tabs, DevTools workbench, and JS REPL
```

---

## Installation

Add the desired module to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // For Compose Multiplatform Desktop applications
    implementation("dev.daviante:kromium-compose:1.0.150")

    // Or for standalone Kotlin JVM / Swing / Headless applications
    implementation("dev.daviante:kromium-core:1.0.150")
}
```

---

## Quick Start

### 1. Initialize the Engine
Initialize Kromium once in your application startup:

```kotlin
import dev.daviante.kromium.presentation.browser.Kromium

suspend fun main() {
    Kromium.initialize {
        // Standard hardware-accelerated windowed rendering (default: false)
        windowlessRendering = false
        
        // Optional: enable DevTools remote debugging port
        remoteDebuggingPort = 9222
    }
}
```

Monitor download and bootstrap progress reactively using `Kromium.state`:

```kotlin
val state by Kromium.state.collectAsState()

when (val s = state) {
    is KromiumState.Downloading -> Text("Downloading Chromium: ${s.progress.percentage}%")
    is KromiumState.Extracting -> Text("Extracting runtime binaries...")
    is KromiumState.Initializing -> Text("Bootstrapping Chromium...")
    is KromiumState.Ready -> Text("Engine ready!")
    is KromiumState.Error -> Text("Failed to load engine: ${s.cause.message}")
    else -> {}
}
```

### 2. Render Web Content in Compose Desktop

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumState

@Composable
fun BrowserScreen() {
    val state = rememberKromiumState(initialUrl = "https://github.com")

    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation toolbar
        Row {
            Button(onClick = { state.goBack() }, enabled = state.canGoBack) { Text("Back") }
            Button(onClick = { state.goForward() }, enabled = state.canGoForward) { Text("Forward") }
            Button(onClick = { state.reload() }) { Text("Reload") }
            Button(onClick = { state.openDevTools() }) { Text("DevTools") }
        }

        // Web view surface
        KromiumView(
            state = state,
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

---

## Feature Highlights

### 🍪 Coroutine Cookie Management
```kotlin
// Retrieve all cookies for a domain
val cookies: Map<String, String> = KromiumCookieManager.getCookies("https://example.com")

// Set a session or persistent cookie
KromiumCookieManager.setCookie(
    url = "https://example.com",
    name = "session_token",
    value = "xyz123",
    isHttpOnly = true,
    isSecure = true
)

// Clear all cookies
KromiumCookieManager.clearCookies()
```

### 🌐 Request & Header Interception
```kotlin
state.requestInterceptor = KromiumRequestInterceptor { request ->
    // Block tracking scripts
    if (request.url.contains("doubleclick.net")) {
        return@KromiumRequestInterceptor true
    }
    // Inject custom headers
    request.headers["Authorization"] = "Bearer my-secret-token"
    false // Proceed
}
```

### 🔗 Navigation Overrides (OAuth / Deep Links)
```kotlin
state.shouldOverrideUrlLoading = { url ->
    if (url.startsWith("myapp://oauth-callback")) {
        handleOAuthToken(url)
        true // Intercept and cancel browser navigation
    } else {
        false // Let browser navigate normally
    }
}
```

### 📄 Extracting HTML & Executing JavaScript
```kotlin
// Run custom JavaScript and get the result
val title = state.evaluateJavaScript("document.title")

// Extract complete page HTML
val html = state.getHtml()

// Extract visible text content
val text = state.getText()
```

### 📸 Screenshots & PDF Printing
```kotlin
// Capture an in-memory BufferedImage screenshot
val screenshot: BufferedImage? = browser.takeScreenshot()

// Print page to a PDF file
browser.printToPdf("C:/Reports/page.pdf")
```

---

## 🚀 Running the Demo Application

The repository includes a ready-to-run showcase browser application in `:kromium-demo` illustrating multi-tab navigation, custom dialogs, asynchronous JavaScript evaluation, cookie inspection, live canvas rendering, and developer tools:

```bash
./gradlew :kromium-demo:run
```

---

## License

```
Copyright 2026 Daviante Group

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
