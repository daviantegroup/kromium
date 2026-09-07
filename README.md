<div align="center">
  <img src="assets/logo.svg" alt="Kromium Logo" width="88" height="88" />
  <br />
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/brand-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="assets/brand.svg">
    <img alt="Kromium" src="assets/brand.svg" width="280">
  </picture>
  <p><strong>Embed Modern Web Capabilities in Compose Multiplatform Desktop &amp; Kotlin JVM</strong></p>

  <p>
    <a href="https://central.sonatype.com/artifact/dev.daviante/kromium-compose"><img src="https://img.shields.io/badge/Maven_Central-v1.2.150--b11-107c41?style=flat-square&logo=apachemaven" alt="Maven Central" /></a>
    <a href="https://kromium.daviante.dev"><img src="https://img.shields.io/badge/Docs_Portal-kromium.daviante.dev-0078d4?style=flat-square" alt="Documentation Portal" /></a>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin" /></a>
    <a href="https://www.jetbrains.com/lp/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose_Desktop-1.7.3-4285F4?style=flat-square&logo=jetpackcompose" alt="Compose Multiplatform" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-5c2d91?style=flat-square" alt="License" /></a>
  </p>
</div>

---

**Kromium** is an open-source, production-grade Chromium Embedded Framework (CEF) library tailored specifically for **Compose Multiplatform Desktop** and **Kotlin JVM** applications across Windows, macOS, and Linux.

Powered by standard **CEF 150** without third-party wrappers, Kromium delivers zero-config automated engine downloads from JetBrains Runtime releases, hardware-accelerated 120 FPS rendering, coroutine-based JavaScript bridges, suspendable cookie managers, network request interception, and a first-class declarative `@Composable KromiumView`.

> [!NOTE]
> **Explore the Online Portal**: An interactive documentation portal with live Compose simulators, full API browser, and search is available at **[kromium.daviante.dev](https://kromium.daviante.dev)**.

---

## 📚 Complete Documentation Hub

Detailed guides covering every subsystem are available in the **[`docs/`](docs/)** directory:

| Guide | Topic Description |
|:---|:---|
| **[Documentation Index](docs/README.md)** | Master hub linking all 11 guides, categorized architecture, and API index. |
| **[Architecture & Engine Lifecycle](docs/ARCHITECTURE_AND_LIFECYCLE.md)** | Automated JBR bundle download, SHA-256 checksums, Zip-Slip safe extraction, platform detection, and `KromiumState` machine. |
| **[Compose Multiplatform Guide](docs/COMPOSE_DESKTOP_GUIDE.md)** | Declarative `KromiumView`, `rememberKromiumState`, observable properties (`url`, `title`, `isLoading`), and multi-tab browser tutorial. |
| **[Core JVM & Swing Integration](docs/SWING_AND_JVM_GUIDE.md)** | Pure Kotlin JVM & Java Swing integration without Compose (`JFrame`, `JPanel`, EDT safety, window listeners). |
| **[Browser & Client API Reference](docs/BROWSER_AND_CLIENT_API.md)** | Direct browser control via `KromiumClient` & `KromiumBrowser`, navigation, in-page search, zoom, screenshots, and PDF printing. |
| **[JavaScript Bridge & DOM Inspection](docs/JAVASCRIPT_BRIDGE.md)** | Suspendable `evaluateJavaScript`, cancellation cleanup, timeout handling, and DOM extraction (`getHtml`, `getText`, `getFaviconUrl`). |
| **[Network Interception & Security](docs/NETWORK_AND_SECURITY.md)** | `KromiumRequestInterceptor`, header injection, ad/tracker blocking, navigation overrides, `SslErrorPolicy`, and proxies. |
| **[Cookie Management Reference](docs/COOKIE_MANAGEMENT.md)** | Suspendable `KromiumCookieManager`: asynchronous `getCookies()`, `setCookie()`, `deleteCookie()`, and disk flushing. |
| **[Handlers, Listeners & Events](docs/HANDLERS_AND_EVENTS.md)** | Callbacks for HTTP authentication, JS modal dialogs (`alert`/`confirm`/`prompt`), downloads, console logs, and errors. |
| **[Error Handling & Logging](docs/ERROR_HANDLING_AND_LOGGING.md)** | Sealed `KromiumException` catalog, recovery strategies, and pluggable `KromiumLogger` interface. |
| **[Troubleshooting, Distribution & FAQ](docs/TROUBLESHOOTING_AND_FAQ.md)** | Black screen fixes, Linux package dependencies, macOS Gatekeeper handling, packaging with Conveyor / Gradle, and ProGuard rules. |
| **[Migration & Release Notes](docs/MIGRATION_AND_RELEASE_NOTES.md)** | What's new in v1.2.150-b11 (Stable), compatibility matrices, and migration guides from JavaFX WebView and raw JCEF. |

---

## 🏗️ Architecture at a Glance

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

## 📦 Modules

```
kromium/
├── kromium-core       // Pure Kotlin/JVM engine, downloader, JCEF lifecycle, cookies, and network
├── kromium-compose    // Compose Multiplatform desktop UI integration (KromiumView)
└── kromium-demo       // Showcase browser app with tabs, DevTools workbench, and JS REPL
```

---

## 📥 Installation

Add the desired module to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // For Compose Multiplatform Desktop applications:
    implementation("dev.daviante:kromium-compose:1.2.150-b11")

    // Or for standalone Kotlin JVM / Swing / Headless applications:
    // implementation("dev.daviante:kromium-core:1.2.150-b11")
}
```

---

## ⚡ Quick Start

### 1. Initialize the Engine
Initialize Kromium once in your application startup before rendering any UI:

```kotlin
import dev.daviante.kromium.presentation.browser.Kromium

suspend fun main() {
    Kromium.initialize {
        // Hardware-accelerated windowed rendering (default: false)
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
    val state = rememberKromiumState(initialUrl = "https://github.com/daviantegroup/kromium")

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

## ✨ Feature Highlights

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
    // Inject custom authorization headers
    request.headers["Authorization"] = "Bearer my-secret-token"
    false // Allow request to proceed
}
```

### 🔗 Navigation Overrides (OAuth & Deep Links)
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

### 📥 File Downloads & Manager
```kotlin
// Set download target directory
state.downloadDirectory = File(System.getProperty("user.home"), "Downloads")

// Listen for download progress updates
state.onDownload = { item ->
    println("Downloading ${item.suggestedFileName}: ${item.percentComplete}% (${item.speed / 1024} KB/s)")
}

// Programmatically trigger downloads
state.startDownload("https://example.com/latest-release.zip")
```

### 📄 Extracting DOM & Executing JavaScript
```kotlin
// Run custom JavaScript asynchronously and get the result
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

## 🤖 AI Coding Agent Feeds

If you are developing with autonomous AI coding agents (Cursor, Claude Code, GitHub Copilot), direct plain-text documentation endpoints adhering to the `llmstxt.org` specification are available:

- **[llms.txt (Quickstart & Index)](https://kromium.daviante.dev/llms.txt)**: Fast-loading index and API definitions.
- **[llms-full.txt (Full Concatenated Docs)](https://kromium.daviante.dev/llms-full.txt)**: 40KB single-file complete documentation of all 26 articles.

---

## 🚀 Running the Showcase Demo

The repository includes a ready-to-run showcase desktop browser application in `:kromium-demo` illustrating multi-tab navigation, custom dialogs, asynchronous JavaScript evaluation, cookie inspection, live canvas rendering, and developer tools:

```bash
./gradlew :kromium-demo:run
```

---

## 🛡️ Zero Telemetry & Security

Kromium strictly guarantees **Zero Built-In Telemetry**. All Google metrics collection, crash reporting (`crashpad`), and background diagnostic pings are disabled at engine boot.

Security vulnerabilities should be reported responsibly in accordance with our [Security Policy](SECURITY.md).

---

## 📄 License

```
Copyright 2026 Daviante Group

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
