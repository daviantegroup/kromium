<div align="center">
  <img src="assets/logo.svg" alt="Kromium Logo" width="96" height="96" />
  <br />
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/brand-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="assets/brand.svg">
    <img alt="Kromium" src="assets/brand.svg" width="300">
  </picture>
  <p><strong>Modern Chromium Embedded Framework for Compose Multiplatform Desktop &amp; Kotlin JVM</strong></p>

  <p>
    <a href="https://central.sonatype.com/artifact/dev.daviante/kromium-compose"><img src="https://img.shields.io/badge/Maven_Central-v2.1.150--b11-107c41?style=flat-square&logo=apachemaven" alt="Maven Central" /></a>
    <a href="https://kromium.daviante.dev"><img src="https://img.shields.io/badge/Docs_Portal-kromium.daviante.dev-0078d4?style=flat-square" alt="Documentation Portal" /></a>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin" /></a>
    <a href="https://www.jetbrains.com/lp/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose_Desktop-1.7.3-4285F4?style=flat-square&logo=jetpackcompose" alt="Compose Multiplatform" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-5c2d91?style=flat-square" alt="License" /></a>
    <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-24292e?style=flat-square" alt="Platforms" />
  </p>
</div>

---

**Kromium** is an open-source, production-grade Chromium Embedded Framework (CEF) library built specifically for **Compose Multiplatform Desktop** and **Kotlin JVM** applications across Windows, macOS, and Linux.

Backed by standard **Chromium Embedded Framework (CEF 150)** and the battle-tested JetBrains JCEF runtime, Kromium delivers zero-bloat automated engine installation, hardware-accelerated rendering, coroutine-based JavaScript bridges, asynchronous cookie management, enterprise network proxying, and a declarative `@Composable KromiumView`.

> [!TIP]
> **Explore the Interactive Documentation Portal**: Visit **[kromium.daviante.dev](https://kromium.daviante.dev)** for interactive guides, API search, and live demos.

---

## 🌟 Why Kromium?

* 🚀 **Zero-Bloat On-Demand Bootstrapping**: Ship lightweight 15–30MB desktop installers. Kromium downloads, verifies (SHA-256), and caches the native platform JCEF runtime upon first launch.
* 🎨 **First-Class Compose Multiplatform UI**: Declarative `@Composable KromiumView` integrated with Compose Desktop's layout, lifecycle, and state system.
* 🛡️ **Zero Telemetry & Registry Protection**: Automated suppression of Windows Registry modifications, Crashpad crash reporters, Omaha updates, toast notifications, and telemetry.
* 🏢 **Enterprise & SMB Proxy Architecture**: Full support for PAC scripts, WPAD auto-discovery, HTTPS TLS tunnels, SOCKS5 remote DNS leak protection, Multi-Protocol split routing, NTLM/Kerberos SSO, and **dynamic runtime proxy switching**.
* ☕ **Zero JVM Module Configuration**: Dynamic module opening at engine boot eliminates manual `--add-opens` flags on Java 17, 21, and 23+.
* 🤖 **Zero-Dependency Headless Automation**: Run full-featured background web scraping and automation without fragile OpenGL/JOGL dependencies.
* ⚖️ **100% Royalty-Free & Apache 2.0 Licensed**: Open-source, compliant with patent guidelines, and free from commercial MPEG LA licensing encumbrances.

---

## 📚 Documentation Hub

Complete, in-depth documentation organized according to the **Diátaxis framework** is available in the **[`docs/`](docs/)** directory:

| Section | Guides & References | Key Topics |
|:---|:---|:---|
| **🚀 Getting Started** | [Installation](docs/getting-started/installation.md)<br/>[Compose Quickstart](docs/getting-started/quickstart-compose.md)<br/>[Swing JVM Quickstart](docs/getting-started/quickstart-jvm.md) | Gradle dependencies, repository setup, first browser window, and dynamic JVM module opening. |
| **🏛️ Core Concepts** | [Architecture](docs/core-concepts/architecture.md)<br/>[State & Lifecycle](docs/core-concepts/state-and-lifecycle.md)<br/>[Security & Privacy](docs/core-concepts/security-and-privacy.md) | Bootstrap pipeline, multi-process Chromium architecture, `KromiumState` flow, and registry suppression. |
| **📖 Guides** | [Compose UI Integration](docs/guides/compose-ui.md)<br/>[Navigation & History](docs/guides/navigation-and-history.md)<br/>[JavaScript & DOM](docs/guides/javascript-and-dom.md)<br/>[Network & Proxies](docs/guides/network-and-proxies.md) | Multi-tab UI, navigation controls, coroutine JS execution, IPC routers, and enterprise proxy configuration. |
| **📖 Guides (Cont.)** | [Asset Filtering](docs/guides/asset-filtering-and-security.md)<br/>[Cookie Management](docs/guides/cookie-management.md)<br/>[Downloads & Dialogs](docs/guides/downloads-and-dialogs.md)<br/>[Headless & Automation](docs/guides/headless-and-automation.md) | Ad blocking, host locking, async cookie store, downloads, modal dialogs, and off-screen headless scraping. |
| **📋 API Reference** | [Configuration (`KromiumConfig`)](docs/reference/configuration.md)<br/>[Browser & Client API](docs/reference/browser-and-client-api.md)<br/>[Handlers & Events](docs/reference/handlers-and-events.md)<br/>[Exceptions & Logging](docs/reference/exceptions-and-logging.md) | Complete property catalogs, method signatures, composite multiplexers, and pluggable logging. |
| **📦 Deployment** | [Packaging & Distribution](docs/deployment/packaging-and-distribution.md)<br/>[Platform Considerations](docs/deployment/platform-specifics.md)<br/>[Troubleshooting & FAQ](docs/deployment/troubleshooting-and-faq.md) | Desktop installers (MSI, DMG, DEB), ProGuard/R8 rules, macOS framework symlinks, and Linux dependencies. |

Visit the master sitemap at **[`docs/README.md`](docs/README.md)**.

---

## 🏗️ Architecture

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
                     │ (StateFlow lifecycle, │
                     │  Mutex-safe startup)  │
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

* **`dev.daviante:kromium-compose`**: Declarative `@Composable KromiumView`, `KromiumViewState`, and Compose multi-tab helpers. (Includes `kromium-core` transitively).
* **`dev.daviante:kromium-core`**: Core engine coordination, dynamic JCEF downloader, `KromiumBrowser`, `KromiumClient`, cookie management, network interception, and headless automation.
* **`kromium-sample-compose`**: Complete showcase desktop browser built with Kotlin & Jetpack Compose Multiplatform.
* **`kromium-sample-swing`**: Complete showcase desktop browser built with 100% Pure Java & Swing with FlatLaf modern UI.

---

## 📥 Installation

Add the dependency to your `build.gradle.kts`:

### Compose Multiplatform Desktop

```kotlin
repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Kromium Compose Multiplatform bindings
                implementation("dev.daviante:kromium-compose:2.1.150-b11")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            }
        }
    }
}
```

### Pure Kotlin JVM / Java Swing (Without Compose)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.daviante:kromium-core:2.1.150-b11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
```

---

## ⚡ Quick Start

### 1. Initialize the Engine
Call `Kromium.initialize()` once during application startup before rendering UI:

```kotlin
import dev.daviante.kromium.presentation.browser.Kromium

suspend fun main() {
    Kromium.initialize {
        // Suppresses Windows Registry modifications and telemetry (default: true)
        blockRegistryAndTelemetry = true
        
        // Optional: Remote Chrome DevTools port (0 = disabled)
        remoteDebuggingPort = 9222
    }
}
```

Monitor engine download and bootstrap progress reactively:

```kotlin
val state by Kromium.state.collectAsState()

when (val s = state) {
    is KromiumState.Downloading -> Text("Downloading Chromium: ${s.progress.percentage}%")
    is KromiumState.Extracting -> Text("Extracting runtime binaries...")
    is KromiumState.Initializing -> Text("Bootstrapping Chromium engine...")
    is KromiumState.Ready -> Text("Engine ready!")
    is KromiumState.Error -> Text("Initialization error: ${s.cause.message}")
    else -> {}
}
```

### 2. Embed Web Content in Compose Desktop

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
        // Navigation Bar
        Row {
            Button(onClick = { state.goBack() }, enabled = state.canGoBack) { Text("Back") }
            Button(onClick = { state.goForward() }, enabled = state.canGoForward) { Text("Forward") }
            Button(onClick = { state.reload() }) { Text("Reload") }
            Button(onClick = { state.openDevTools() }) { Text("DevTools") }
        }

        // Native Browser Rendering Surface
        KromiumView(
            state = state,
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

---

## 💡 Feature Highlights

### 🍪 Coroutine Cookie Management
```kotlin
import dev.daviante.kromium.presentation.network.KromiumCookieManager

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

### 🌐 Network Request Interception & Security
```kotlin
import dev.daviante.kromium.presentation.network.KromiumRequestInterceptor

state.requestInterceptor = KromiumRequestInterceptor { request ->
    // 1. Block tracking scripts & ads
    if (request.url.contains("doubleclick.net")) {
        return@KromiumRequestInterceptor true // Cancel request
    }
    // 2. Inject custom authentication headers
    request.headers["Authorization"] = "Bearer my-secret-token"
    false // Allow request to proceed
}
```

### 🏢 Enterprise Proxies & Dynamic Runtime Switching
```kotlin
import dev.daviante.kromium.domain.config.KromiumProxy

// Switch proxy at runtime across all active browsers without restarting:
Kromium.setProxy(
    KromiumProxy.Http(
        host = "proxy.corp.internal",
        port = 8080,
        username = "domain\\user",
        password = "SecurePassword",
        bypassList = listOf("<local>", "127.0.0.1", "*.internal.corp")
    )
)
```

### 📄 Suspending JavaScript & DOM Extraction
```kotlin
coroutineScope.launch {
    // Evaluate arbitrary JavaScript asynchronously
    val pageTitle: String? = state.evaluateJavaScript("document.title")

    // Extract outer HTML of current frame
    val html: String = state.getHtml()

    // Extract visible plain text
    val plainText: String = state.getText()
}
```

### 📸 Screenshots & PDF Printing
```kotlin
// Capture an in-memory BufferedImage screenshot
val screenshot: BufferedImage? = browser.takeScreenshot()
if (screenshot != null) {
    ImageIO.write(screenshot, "PNG", File("page.png"))
}

// Export page to a vectorized PDF
browser.printToPdf("report.pdf")
```

---

## 💻 Supported Platforms & Systems

| Operating System | Architectures | Minimum Version | Prerequisites |
|:---|:---|:---|:---|
| **Windows** | x64 (64-bit) | Windows 10 / 11, Server 2019+ | [Visual C++ 2015–2022 Redistributable](https://aka.ms/vs/17/release/vc_redist.x64.exe) |
| **macOS** | ARM64 (Apple Silicon) & Intel x64 | macOS 11.0 (Big Sur)+ | None *(Universal binaries resolved automatically)* |
| **Linux** | x64 & ARM64 | Ubuntu 20.04+, Debian 11+, Fedora 36+ | Standard desktop X11/GTK libraries (`libnss3`, `libasound2`, `libdrm2`) |

*Minimum Java Runtime: **Java 17 LTS** (Java 21 LTS Recommended).*

---

## 🚀 Running the Showcase Demos

The repository includes ready-to-run desktop browser applications for both Kotlin Compose Desktop and Pure Java Swing:

```bash
# Kotlin Compose Desktop Showcase
./gradlew :kromium-sample-compose:run

# Pure Java Swing Showcase
./gradlew :kromium-sample-swing:run
```

---

## 🤖 AI Coding Agent Endpoints

For developers and teams using autonomous AI coding assistants (Cursor, Claude Code, GitHub Copilot), plain-text documentation feeds conforming to the `llmstxt.org` specification are available:

* **[llms.txt (Quickstart & Index)](https://kromium.daviante.dev/llms.txt)**: Fast-loading index and API definitions.
* **[llms-full.txt (Full Concatenated Docs)](https://kromium.daviante.dev/llms-full.txt)**: Comprehensive single-file documentation reference.

---

## 🛡️ Security & Responsible Disclosure

Kromium strictly guarantees **Zero Built-In Telemetry**. All Google metrics reporting, crash dumps (`crashpad`), and diagnostic ping services are suppressed at engine boot.

Please report security vulnerabilities in accordance with our [Security Policy](SECURITY.md).

---

## 📄 License

```
Copyright 2026 Daviante Group

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
