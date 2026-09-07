# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.1.150-b11] - 2026-09-08

Security release resolving all 26 GitHub CodeQL High-severity path-injection alerts (CWE-022 / CWE-073) and streamlining repository branding.

### Security
- **Path-Injection Remediation (CWE-022 / CWE-073)**:
  - Sanitized engine install directory resolution, environment variable handling (`user.home`, `APPDATA`, `XDG_DATA_HOME`), and root prefix boundary validation in `EngineRegistry` and `Kromium`.
  - Added traversal sequence checks (`..`) and prefix containment barriers to cache path derivation in `KromiumConfig`.
  - Enforced canonical path boundary validation for macOS Framework symlinks and helper app resolution in `OperatingSystem.MacOS`.
  - Hardened native library resolution in `CefBootstrapper` across JVM home and macOS framework candidate paths.
  - Sanitized default and fallback download directory resolution in `KromiumClient`.

### Changed
- Refined and standardized project README and documentation structure.
- Removed legacy promotional banner assets.

---

## [2.0.150-b11] - 2026-09-08 [Major Release]

Major release delivering enterprise security hardening, Windows Registry write suppression, comprehensive enterprise proxy infrastructure with dynamic runtime switching, and a redesigned Diátaxis documentation suite.

### Added
- **Windows Registry Write & Telemetry Suppression**:
  - Added `blockRegistryAndTelemetry = true` (default) on `KromiumConfig` to suppress background telemetry, crash reporting (`crashpad`/`breakpad`), Omaha component update state, shell integration, autorun hooks, and Windows Action Center toasts.
  - Enforced complete cache and profile quarantining via `--root-cache-path` and `--user-data-dir`, preventing Chromium from polluting `%LOCALAPPDATA%\Chromium` or user registry hives.
- **Enterprise & SMB Proxy Architecture**:
  - Full proxy strategy hierarchy: `KromiumProxy.System`, `Direct`, `AutoDetect` (WPAD DHCP/DNS), `Pac`, `Http`/`Https` (with bypass rules and TLS tunnels), `Socks5` (with remote DNS leak protection), and `MultiProtocol` split routing.
  - **Dynamic Runtime Proxy Switching**: Added `Kromium.setProxy(proxy)` and `KromiumClient.setProxy(proxy)` to update proxy configurations dynamically across all active browser windows without restarting the engine.
  - **Integrated Windows Authentication (SSO)**: Added `authServerAllowlist` and `authNegotiateDelegateAllowlist` for seamless NTLM and Kerberos SPNEGO enterprise authentication.
  - **Automatic HTTP 407 Challenge Resolution**: Automatically supplies proxy credentials when challenged.
- **Network Resilience & Asset Filtering**:
  - Enhanced `KromiumCookieManager` with timeout protection (`timeoutMs`) and fast-path handling for non-HTTP/blank URLs.
  - Added typed `KromiumAssetFilter` with `ALL_BLOCKED` and `MEDIA_ONLY` presets.
- **Comprehensive Diátaxis Documentation Suite**:
  - 22 structured documentation guides organized across Getting Started, Core Concepts, Guides, Reference, and Deployment with 100% verified executable code signatures and zero broken links.

---

## [1.2.150-b11] - 2026-09-08 [Stable]

First official stable release of Kromium. Incorporates all production-tested features, cross-platform engine bootstrapping, enhanced download manager, dynamic Java 17/21+ module opening, CodeQL security hardening, and Chrome-styled demo application.

### Added
- **Dynamic macOS JBR 25 Path Resolution**: `OperatingSystem.MacOS` dynamically detects CEF frameworks and helper applications inside `Frameworks/cef_server.app/Contents/Frameworks/` for JetBrains Runtime 25 bundles while maintaining backward compatibility with legacy `Frameworks/` hierarchies.
- **Universal Archive Symlink Support**: `EngineExtractor` handles tar symbolic links (`isSymbolicLink`) with Zip-Slip path traversal validation, native symbolic link creation via `Files.createSymbolicLink`, and automated copy fallback on Windows environments without developer mode.
- **Automatic Binary Permission Enforcement**: Automatically restores executable flags and recursively enforces executable permissions across all helper executables (`jcef helper`, `jcef_helper`, `cef_server`), shared libraries (`.so`, `.dylib`), and shell scripts post-extraction.
- **Dynamic JVM Module Opener (`JvmModuleOpener`)**: Dynamically opens required `java.desktop/sun.awt` internal packages on Java 17 and 21+ at runtime via Unsafe and Module reflection, enabling zero-config desktop startup without requiring external `--add-opens` JVM flags.
- **Download Management Subsystem**:
  - `KromiumClient`: Added configurable `downloadDirectory` (defaults to OS Downloads folder), `onBeforeDownloadListener`, `cancelDownload`, `pauseDownload`, and `resumeDownload`.
  - `KromiumBrowser`: Added programmatic `startDownload(url)` to trigger downloads from any URL.
  - `KromiumDownloadItem`: Added `fullPath` property resolving the on-disk destination path.
  - Conforms to CEF specifications by returning `true` from `onBeforeDownload` when executing callbacks, ensuring Chromium proceeds with downloads reliably.
- **Compose Multiplatform `KromiumView` Enhancements**:
  - Added `loadingContent: @Composable (BoxScope.() -> Unit)?` slot for custom placeholder/loading indicators during engine bootstrap.
  - Added support for passing external user-owned `KromiumClient` instances without premature disposal upon recomposition.
  - Exposed download properties (`downloadDirectory`, `onBeforeDownload`, `startDownload`) directly on `KromiumViewState`.
- **ProGuard / R8 Consumer Rules**: Added `kromium-core.pro` preserving JCEF native JNI bindings, callback interfaces, and JavaScript serialization models during desktop distribution packaging.
- **Chrome-Style Demo Browser Showcase**:
  - Redesigned demo UI to match modern browser styling with omnibox, security badges, responsive tab strips with close buttons, and home page set to `https://kromium.daviante.dev`.
  - Real-time Downloader workbench tab displaying active/completed downloads with speed metrics and "Show in Folder" actions.
  - Interactive "Clear Browsing Data" dialog with configurable cache and cookie eviction.
  - Added complete platform icon suite (`.icns`, `.ico`, `.png`, `.svg`).

### Fixed
- **Engine Redownload Loop on macOS**: Fixed `EngineRegistry.isInstalled` to check for `Frameworks/cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework`, stopping redundant 250MB redownloads on subsequent launches.
- **Bootstrap Idempotency & Failure Cleanup**: Moved `EngineRegistry.markInstalled` to execute strictly **after** `CefBootstrapper.bootstrap()` verifies successfully. Added automatic cleanup of `install.lock` and temporary archives on bootstrap failure so corrupted states do not prevent future launches.
- **JVM Runtime Integrity**: Removed `System.setProperty("java.home", ...)` mutation in `CefBootstrapper` that corrupted JVM runtime lookups for `libjawt.dylib` and CA certificates on macOS.
- **Cross-Platform JAWT Resolution**: Enhanced native JAWT library resolution to look up `${java.home}/lib` and `${java.home}/bin` across Windows, macOS, and Linux.
- **Windows Native Graphics Preloading**: Preloaded `chrome_elf.dll`, `d3dcompiler_47.dll`, `libEGL.dll`, `libGLESv2.dll`, `vk_swiftshader.dll`, and `vulkan-1.dll` prior to CEF initialization on Windows.
- **GitHub API Rate Limit Resilience**: `EngineDownloader` detects HTTP 403 rate limits and throws descriptive exception messages, and prioritizes `.tar.gz` and non-SDK bundles.
- **Security & CodeQL Hardening**:
  - Resolved path injection (CWE-022) in `FileUtils` and `KromiumConfig` by enforcing canonical path normalization and strict directory boundary validation on `cachePath`, `installDir`, and custom download directories.
  - Resolved command-line argument injection (CWE-078/088) by separating process executable paths and argument arrays safely with `--`.
  - Hardened JavaScript evaluator against script injection and Promise deadlocks using safe string literal escaping and universal `Function`/`eval` execution.
- **macOS Bundle Asset Matching**: Expanded OS keyword matching in `EngineDownloader` to support `osx`, `darwin`, `arm64`, and `x86_64` aliases in release assets.

## [1.1.150-b11] - 2026-09-08 [Pre-Test]

### Fixed
- Early testing build for macOS osx bundle matching and initial download fixes.

## [1.0.150-b11] - 2026-09-07 [Pre-Test]

### Changed
- Early testing build upgrading underlying JCEF runtime to JetBrains certified release `150.0.14-g7c1aa68-chromium-150.0.7871.129-api-1.21-263-b11`.
- Isolated native runtime cache directory to `jcef-150-b11`.

## [1.0.150] - 2026-09-07 [Pre-Test]

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
