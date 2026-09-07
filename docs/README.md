<div align="center">
  <img src="../assets/logo.svg" alt="Kromium Logo" width="72" height="72" />
  <h1>Kromium Documentation Hub</h1>
  <p><strong>Comprehensive developer guides, API specifications, and architecture references for Kromium</strong></p>

  <p>
    <a href="https://kromium.daviante.dev"><img src="https://img.shields.io/badge/Interactive_Portal-kromium.daviante.dev-0078d4?style=flat-square" alt="Web Portal" /></a>
    <a href="https://central.sonatype.com/artifact/dev.daviante/kromium-compose"><img src="https://img.shields.io/badge/Maven_Central-dev.daviante-107c41?style=flat-square" alt="Maven Central" /></a>
    <a href="https://github.com/daviantegroup/kromium"><img src="https://img.shields.io/badge/GitHub-kromium-242424?style=flat-square&logo=github" alt="GitHub" /></a>
    <a href="https://github.com/daviantegroup/kromium/blob/master/LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-5c2d91?style=flat-square" alt="License" /></a>
  </p>
</div>

---

## 📖 Documentation Directory

The documentation is organized into 8 functional categories, available both in this repository and interactively with live code simulators on the **[Official Kromium Portal](https://kromium.daviante.dev)**.

| Category | Guides | Online Portal Deep Link |
|:---|:---|:---|
| **1. Overview & Release** | • [Architecture & Engine Lifecycle](ARCHITECTURE_AND_LIFECYCLE.md)<br>• [Migration & Release Notes](MIGRATION_AND_RELEASE_NOTES.md) | [Portal Overview](https://kromium.daviante.dev/#/docs/overview)<br>[What's New in v1.2.150-b11](https://kromium.daviante.dev/#/docs/whats-new) |
| **2. Get Started** | • [Prerequisites & System Requirements](ARCHITECTURE_AND_LIFECYCLE.md#platform-detection)<br>• [Installation & Gradle Setup](COMPOSE_DESKTOP_GUIDE.md#quickstart) | [Installation Guide](https://kromium.daviante.dev/#/docs/installation)<br>[First Browser Tutorial](https://kromium.daviante.dev/#/docs/first-browser) |
| **3. Compose Multiplatform** | • [Compose Desktop Integration Guide](COMPOSE_DESKTOP_GUIDE.md) | [KromiumView Reference](https://kromium.daviante.dev/#/docs/compose-view)<br>[Browser State & Lifecycle](https://kromium.daviante.dev/#/docs/compose-state) |
| **4. Core Web Capabilities** | • [JavaScript Bridge & DOM Inspection](JAVASCRIPT_BRIDGE.md)<br>• [Cookie Management Reference](COOKIE_MANAGEMENT.md)<br>• [Network Interception & Security](NETWORK_AND_SECURITY.md) | [JavaScript Bridge](https://kromium.daviante.dev/#/docs/js-bridge)<br>[Cookie Management](https://kromium.daviante.dev/#/docs/cookie-management)<br>[Network Interception](https://kromium.daviante.dev/#/docs/network-interception) |
| **5. JVM & Swing Integration** | • [Core JVM & Swing Integration Guide](SWING_AND_JVM_GUIDE.md)<br>• [Browser & Client API Reference](BROWSER_AND_CLIENT_API.md) | [Swing Embedding Guide](https://kromium.daviante.dev/#/docs/swing-embedding)<br>[Swing Lifecycle](https://kromium.daviante.dev/#/docs/swing-lifecycle) |
| **6. Handlers & Diagnostics** | • [Handlers, Listeners & Events](HANDLERS_AND_EVENTS.md)<br>• [Error Handling & Logging Guide](ERROR_HANDLING_AND_LOGGING.md) | [DevTools & Console](https://kromium.daviante.dev/#/docs/devtools-console)<br>[Dialogs & Downloads](https://kromium.daviante.dev/#/docs/dialogs-downloads) |
| **7. Production & Troubleshooting** | • [Troubleshooting, Distribution & FAQ](TROUBLESHOOTING_AND_FAQ.md) | [Troubleshooting Guide](https://kromium.daviante.dev/#/docs/troubleshooting)<br>[Frequently Asked Questions](https://kromium.daviante.dev/#/docs/faq) |
| **8. Policies & Contributing** | • [Contributing Guidelines](../CONTRIBUTING.md)<br>• [Security Policy](../SECURITY.md)<br>• [Code of Conduct](../CODE_OF_CONDUCT.md)<br>• [Changelog](../CHANGELOG.md) | [Privacy Policy](https://kromium.daviante.dev/#/docs/privacy-policy)<br>[Terms of Use](https://kromium.daviante.dev/#/docs/terms-of-use) |

---

## ⚡ Quick Start Reference

### 1. Add Gradle Dependency
```kotlin
// build.gradle.kts
dependencies {
    // For Compose Multiplatform Desktop applications
    implementation("dev.daviante:kromium-compose:1.2.150-b11")

    // Or for standalone Kotlin JVM / Swing / Headless applications
    // implementation("dev.daviante:kromium-core:1.2.150-b11")
}
```

### 2. Initialize Engine
```kotlin
import dev.daviante.kromium.presentation.browser.Kromium

suspend fun main() {
    Kromium.initialize {
        windowlessRendering = false
        remoteDebuggingPort = 9222
    }
}
```

### 3. Render Declarative Browser in Compose
```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumState

@Composable
fun App() {
    val state = rememberKromiumState(initialUrl = "https://github.com/daviantegroup/kromium")
    KromiumView(
        state = state,
        modifier = Modifier.fillMaxSize()
    )
}
```

---

## 🏗️ Architecture Overview

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

## 🌐 AI Coding Agent Ingestion

Autonomous coding agents (Cursor, Claude Code, GitHub Copilot) can ingest full plain-text documentation endpoints directly from the live portal:

- **[Quickstart & API Index (llms.txt)](https://kromium.daviante.dev/llms.txt)**: Structured library overview and API catalog.
- **[Full Documentation Feed (llms-full.txt)](https://kromium.daviante.dev/llms-full.txt)**: Consolidated 40KB plain-text markdown file containing the entire documentation suite in a single request.

---

[← Return to Repository Home](../README.md) &bull; [Explore the Interactive Web Portal](https://kromium.daviante.dev)
