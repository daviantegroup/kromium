# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.150] - 2026-09-07

### Added
- **Pure JCEF Integration**: Decoupled from third-party wrappers, compiling against official standard `jcef.jar`.
- **Automated Runtime Downloader**:
  - Automatically fetches matching native runtime bundles from JetBrains Runtime releases.
  - SHA-256 checksum verification with buffered verification stream.
  - Zip-Slip directory traversal protection during archive extraction.
  - Automatic removal of `com.apple.quarantine` on macOS.
- **Compose Multiplatform Integration (`kromium-compose`)**:
  - Declarative `@Composable KromiumView` component.
  - State management via `rememberKromiumState` and `KromiumViewState`.
  - Reactive observable state for `url`, `title`, `isLoading`, `canGoBack`, and `canGoForward`.
  - Pending URL buffering and automatic lifecycle disposal.
- **JavaScript & DOM Bridge**:
  - Coroutine-based `evaluateJavaScript()` with `JsEvaluator` message router.
  - Leak-free cancellation cleanup via `suspendCancellableCoroutine`.
  - DOM extraction helpers: `getHtml()`, `getText()`, and `getFaviconUrl()`.
  - Configurable timeouts (`JsEvaluator.defaultTimeoutMs`).
- **Cookie Management (`KromiumCookieManager`)**:
  - Suspendable asynchronous cookie retrieval: `getCookies()` and `getCookie()`.
  - Non-hanging timeout protection when domains have zero cookies.
  - Cookie insertion (`setCookie`), deletion (`deleteCookie`), and clearing (`clearCookies`).
  - Disk store flushing (`flush()`).
- **Network Interception & Security**:
  - Functional interface `KromiumRequestInterceptor` for header mutation and request blocking.
  - Navigation override hook `shouldOverrideUrlLoading` for OAuth and custom deep links.
  - Sealed SSL error handling hierarchy `SslErrorPolicy` (`Strict`, `AllowDomains`, `AllowAll`).
  - Proxy configuration support via `KromiumProxy` (`System`, `Direct`, `Http`, `Socks5`).
  - Chromium process sandbox enabled by default (`sandboxEnabled = true`).
- **Client & Browser Presentation Features**:
  - Native DevTools window inspection (`openDevTools()`).
  - In-page text search (`find()`).
  - In-memory raw HTML rendering via `KromiumHtmlResourceHandler` (`loadHtml()`).
  - Zoom factor controls (`setZoom()`, `getZoom()`).
  - Mouse click simulation (`simulateClick()`).
  - In-memory screenshot capture (`takeScreenshot()`).
  - PDF document export (`printToPdf()`).
- **Event Listeners**:
  - HTTP and proxy authentication challenges (`KromiumAuthListener`).
  - JavaScript modal dialogs (`KromiumJsDialogListener` for alerts, confirms, prompts).
  - Download progress tracking (`KromiumDownloadListener`).
  - Developer console logging (`KromiumConsoleMessage`).
  - Main-frame page load error handling (`KromiumLoadError`).
- **Diagnostics & Logging**:
  - Pluggable `KromiumLogger` interface (`JulKromiumLogger`, `NoOpKromiumLogger`).
  - Sealed exception hierarchy (`KromiumException`).
- **Demo Showcase Application (`kromium-demo`)**:
  - Full showcase multi-tab desktop browser application with Compose Multiplatform.
  - Interactive DevTools workbench including a JavaScript REPL, Cookie Inspector, Live Canvas Animation Showcase, and Visible Text Extractor.
  - Clean modular architecture (`theme`, `model`, `state`, `ui.components`) and Daviante brand header with custom window icon.
- **Engine Bootstrapper Hardening & Lifecycle Fixes**:
  - Enforced local in-process CEF mode via `CefApp.setIsRemoteEnabled(false)` to prevent attempting remote `cef_server.exe` launches.
  - Enabled thread-safe native preinitialization via `jcef_app_preinit_any`.
  - Corrected `CefApp` initialization to ensure all `CefSettings` (subprocess path, resources, locales, sandbox configuration) are applied.
  - Added synchronous `CefAppState.INITIALIZED` synchronization to guarantee native readiness before browser creation.
- **Hardware-Accelerated Windowed Rendering**:
  - Defaulted `windowlessRendering` to `false` for optimal, hardware-accelerated Compose Desktop `SwingPanel` windowed output (`CefBrowserWr`).
  - Corrected `createBrowser(url, isOffScreenRendered, isTransparent)` parameter mappings and overloads.
- **CI / CD**:
  - Automated safe JCEF update and developer notification workflow (`.github/workflows/update-jcef.yml`).
