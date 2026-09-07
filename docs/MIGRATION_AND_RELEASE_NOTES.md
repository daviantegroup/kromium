# Migration Guide & Release Notes

[Kromium Documentation](README.md) &bull; [Interactive Web Portal](https://kromium.daviante.dev/#/docs/whats-new)

This document provides release notes for **v1.0.150**, compatibility matrices, and step-by-step guides for migrating from JavaFX WebView or legacy JCEF wrappers.

---

## Table of Contents

1. [What's New in Kromium v1.0.150](#whats-new-in-kromium-v10150)
2. [Platform & Runtime Compatibility](#platform--runtime-compatibility)
3. [Migrating from JavaFX WebView](#migrating-from-javafx-webview)
4. [Migrating from Raw JCEF](#migrating-from-raw-jcef)
5. [Upgrading from Pre-Release Builds](#upgrading-from-pre-release-builds)

---

## Release History

### Kromium v1.0.150-b11 (2026-09-07)
- **Engine Runtime**: Upgraded underlying JCEF runtime to JetBrains certified release `150.0.14-g7c1aa68-chromium-150.0.7871.129-api-1.21-263-b11`.
- **Runtime Cache Isolation**: Native bundle directory isolated to `jcef-150-b11` to eliminate conflicts with earlier engine binaries.
- **Coordinates**:
  - Compose: `dev.daviante:kromium-compose:1.0.150-b11`
  - Core JVM: `dev.daviante:kromium-core:1.0.150-b11`

---

## What's New in Kromium v1.0.150

Kromium v1.0.150 represents a milestone release, delivering an enterprise-ready Chromium Embedded Framework engine for Compose Multiplatform Desktop and Kotlin JVM:

- **CEF 150 Core Upgrade**: Upgraded underlying native runtime to Chromium 150, supporting latest ECMAScript 2024 features, WebAssembly SIMD, WebGPU, and modern CSS container queries.
- **Zero-Config Automated Downloader**: Eliminates manual native binary extraction. Kromium automatically detects the host architecture, fetches verified release archives from JetBrains Runtime, and validates SHA-256 integrity on the fly.
- **Compose Multiplatform 1.7.3 & Kotlin 2.1+**: First-class declarative `@Composable KromiumView` with reactive `rememberKromiumState` state observation (`url`, `title`, `isLoading`, `canGoBack`, `canGoForward`).
- **Idiomatic Coroutines & Memory Leak Elimination**:
  - `evaluateJavaScript()` now runs as a suspend function with automatic cancellation cleanup (`suspendCancellableCoroutine`).
  - Native cookie visitor callbacks wrapped in suspendable `KromiumCookieManager` methods.
- **Zero Telemetry Enforced**: All Chromium metrics, crashpad reporting, and Google tracking services are hard-disabled at initialization.
- **Hardware-Accelerated 120 FPS Rendering**: Direct JAWT canvas integration on Windows, macOS, and Linux with full HiDPI auto-scaling.

---

## Platform & Runtime Compatibility

| Platform | Architecture | Min OS Version | Rendering Engine |
|---|---|---|---|
| **Windows** | x86_64, aarch64 | Windows 10 (1809+) / Windows 11 | Direct3D 11, SwiftShader fallback |
| **macOS** | Apple Silicon (arm64), Intel (x86_64) | macOS 12 Monterey+ | Metal, OpenGL (JAWT) |
| **Linux** | x86_64, aarch64 | Ubuntu 20.04+, Fedora 38+, Debian 11+ | Vulkan, OpenGL, SwiftShader |

**JDK Requirements**:
- **Supported**: JDK 17, JDK 21 (LTS)
- **Recommended**: Eclipse Adoptium Temurin or JetBrains Runtime (JBR)

---

## Migrating from JavaFX WebView

If your existing desktop application uses JavaFX `WebView` (`javafx.scene.web.WebView`), migrating to Kromium unlocks full Chromium compatibility, modern JavaScript execution, and superior GPU rendering performance.

### 1. Dependency Comparison

**Before (JavaFX):**
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.openjfx:javafx-web:21")
}
```

**After (Kromium Compose):**
```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.daviante:kromium-compose:1.0.150-b11")
}
```

### 2. UI Declaration

**Before (JavaFX via SwingPanel):**
```kotlin
// JavaFX WebView wrapped in SwingPanel
SwingPanel(
    factory = {
        JFXPanel().apply {
            Platform.runLater {
                val webView = WebView()
                val engine = webView.engine
                engine.load("https://github.com")
                scene = Scene(webView)
            }
        }
    }
)
```

**After (Kromium Compose):**
```kotlin
// Pure declarative Compose
val state = rememberKromiumState("https://github.com")
KromiumView(
    state = state,
    modifier = Modifier.fillMaxSize()
)
```

### 3. JavaScript Execution

**Before (JavaFX):**
```kotlin
// Synchronous, blocks the JavaFX Application Thread:
val result = webEngine.executeScript("document.title") as String
```

**After (Kromium):**
```kotlin
// Non-blocking coroutine, runs safely on any dispatcher:
val title: String = state.evaluateJavaScript("document.title")
```

---

## Migrating from Raw JCEF

Migrating from raw JCEF eliminates boilerplate message loop plumbing, manual native binary copying, and complex callback handling.

### 1. Engine Initialization

**Before (Raw JCEF):**
```java
// Raw JCEF required manually setting java.library.path and registering native handlers
CefApp.startup(args);
CefSettings settings = new CefSettings();
settings.windowless_rendering_enabled = false;
CefApp cefApp = CefApp.getInstance(settings);
CefClient client = cefApp.createClient();
```

**After (Kromium):**
```kotlin
// Single asynchronous call handles download, extraction, and native loading:
Kromium.initialize {
    windowlessRendering = false
    remoteDebuggingPort = 9222
}
val client = Kromium.newClient()
```

### 2. Cookie Management

**Before (Raw JCEF):**
```java
// Requires creating custom CefCookieVisitor and handling nested callbacks
CefCookieManager manager = CefCookieManager.getGlobalManager();
manager.visitAllCookies(new CefCookieVisitor() {
    @Override
    public boolean visit(CefCookie cookie, int count, int total, BoolRef deleteCookie) {
        System.out.println(cookie.name + " = " + cookie.value);
        return true;
    }
});
```

**After (Kromium):**
```kotlin
// Direct suspend function returns standard Kotlin Map:
val cookies: Map<String, String> = KromiumCookieManager.getCookies("https://example.com")
```

---

[← Return to Documentation Index](README.md) &bull; [Visit Online Documentation](https://kromium.daviante.dev)
