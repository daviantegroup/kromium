# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.0.150-b11] - 2026-09-10 [Major Release]

Major release establishing first-class **100% Pure Java & Swing enterprise dual ergonomics**, delivering all 5 strategic roadmap milestones (virtual custom schemes, asynchronous vector PDF export, declarative context menu DSL, native window chrome insets, WebRTC device permissions), self-healing JavaScript execution, navigation lifecycle awaiters, frame-aware host locking, and fine-grained asset filtering & allowlisting.

### Added
- **Self-Healing JavaScript Evaluation (`JsEvaluator`)**:
  - Implemented an in-engine polling retry loop inside `JsEvaluator.wrapExpression` that automatically waits for CEF's `CefMessageRouter` to bind `window.kromiumQuery` to the V8 context. Eliminates silent evaluation drops and timeout warnings during early page initialization.
  - Added configurable polling thresholds: global defaults (`JsEvaluator.defaultRouterBindingTimeoutMs = 3000L`, `defaultRouterBindingIntervalMs = 50L`), client-level properties (`client.routerBindingTimeoutMs`, `client.routerBindingIntervalMs`), and per-call overrides in `evaluateJavaScript(...)`.
- **Navigation Lifecycle Awaiters (`NavigationStage`)**:
  - Added `NavigationStage` enum (`STARTED`, `LOADED`, `NETWORK_IDLE`) and `browser.isLoading` property.
  - Added suspending `browser.loadUrl(url, waitUntil, timeoutMs)` and `browser.waitForNavigation(stage, timeoutMs)` with Java `CompletableFuture` variants (`loadUrlAsync`, `waitForNavigationAsync`).
  - Added suspending `client.createHeadlessBrowser(url, waitUntil, ...)` and `Kromium.awaitHeadlessBrowser(url, waitUntil, ...)` / `awaitHeadlessBrowserAsync(...)`.
  - Added `state.waitForNavigation(...)` and suspending `state.loadUrl(url, waitUntil, ...)` in `KromiumViewState`.
- **Frame-Aware Host Locking (`hostLock`)**:
  - Enhanced `onBeforeBrowse` to enforce host lock strictly on main frames (`frame.isMain == true`) by default.
  - Allows embedded third-party verification challenges (Cloudflare Turnstile, Google reCAPTCHA, OAuth) running inside `<iframe>` subframes to navigate freely without breaking domain locks.
  - Added `lockSubframes: Boolean = false` parameter to `setHostLock(...)` and `@Volatile var hostLockSubframes: Boolean` on `KromiumClient` and `KromiumViewState`.
- **Asset Filtering & Strict Allowlist Mode (`KromiumAssetFilter`)**:
  - Added `AssetFilterMode` (`BLOCKLIST` vs `ALLOWLIST`) for a clean, zero-duplicate-options architecture.
  - Added standard presets: `MEDIA_ONLY` (blocks images/media/fonts, retains CSS for SPAs and Turnstile) and `AGGRESSIVE_HEADLESS` (blocks CSS too, aliasing `ALL_BLOCKED`).
  - Added custom blocking and allowlisting criteria: custom file extensions, URL patterns/keywords, CEF resource types, and dynamic lambda predicates.
  - Added `allowMainFrame: Boolean = true` to preserve top-level document navigations in allowlist mode.
  - Added public reusable extension sets: `IMAGE_EXTENSIONS`, `MEDIA_EXTENSIONS`, `FONT_EXTENSIONS`, `STYLESHEET_EXTENSIONS`, `SCRIPT_EXTENSIONS`.
  - Added `allowOnlyAssets(...)` and updated `blockAssets(...)` across `KromiumBrowser`, `KromiumClient`, and `KromiumViewState`.
- **Virtual Asset Streaming & Custom Schemes (`app://`)**:
  - Added `KromiumSchemeHandler` and `KromiumEngine.registerSchemeHandler` to stream Single Page Applications (React, Vue, Vite) and bundled JAR assets with full Web API support (`localStorage`, `IndexedDB`, Web Workers) without localhost servers or CORS restrictions.
  - Added configurable scheme registration options (`isStandard`, `isSecure`, `isCorsEnabled`, `isLocal`).
- **Asynchronous Vector PDF Export & Native Print Dialog**:
  - Added `browser.printToPdf(targetFile, settings)` (Kotlin Coroutines) and `browser.printToPdfAsync(targetFile, settings)` (`CompletableFuture<File>`) with full page dimension, margin, and header/footer layout controls (`KromiumPdfSettings`).
  - Added interactive native OS print dialog via `browser.print()`.
- **Declarative Context Menu Customization DSL**:
  - Added `KromiumContextMenuHandler` and `KromiumMenuBuilder` with declarative Kotlin DSL (`state.setContextMenu { ... }`) and pure Java fluent chaining (`client.setContextMenuHandler(...)`).
  - Added turnkey context actions: `addInspectElement()`, `addSearchWeb()`, `addCopyLink()`, `addCopySelection()`, and `addSaveImageAs()`.
- **Native Window Chrome & Custom Tab Strip Insets**:
  - Added `KromiumWindowChrome` and `KromiumChromeConfig` providing turnkey macOS traffic light insets (`macTrafficLightsWidth`, `macTrafficLightsHeight`), window title suppression, and draggable window titlebar regions.
- **WebRTC Media & Hardware Device Permissions**:
  - Added `KromiumPermissionHandler`, `KromiumPermissionRequest`, and `KromiumPermissionType` supporting audio capture, video capture, geolocation, and DRM identifiers with session decision caching (`clearPermissionCache()`).
- **100% Pure Java Swing Interoperability & Dual Ergonomics**:
  - Complete Java 8+ ergonomics across `kromium-core`: standard `java.util.concurrent.CompletableFuture`, JavaBeans properties (`zoomLevel`, `activeProxy`), SAM functional interfaces, and fluent builders.
  - Added `client.updateProxy(proxy)` returning `boolean` to bypass Kotlin `Result` value-class ABI mangling for pure Java callers.
  - Added `KromiumConfig.Builder` and `KromiumChromeConfig.Builder` for Java application bootstrapping.
- **Universal Java Desktop Interoperability (Swing, AWT, SWT, JavaFX)**:
  - Added official sample applications demonstrating production-grade embedding across all major JVM desktop toolkits:
    - `kromium-sample-awt`: Standard AWT `java.awt.Frame` heavyweight native window embedding.
    - `kromium-sample-swt`: Eclipse SWT `SWT_AWT.new_Frame(composite)` bridged container embedding with native event loop dispatching.
    - `kromium-sample-javafx`: JavaFX `javafx.embed.swing.SwingNode` lightweight OSR embedding with dynamic scene resize synchronization.
  - Added functional navigation toolbars (Back, Forward, Reload, Address bar) and clean lifecycle management across all sample modules.
- **Pure Java2D Lightweight OSR Engine (Zero JOGL/OpenGL)**:
  - Completely eliminated external JOGL and OpenGL dependencies from `kromium-core`.
  - Implemented `KromiumOSRPanel`: an ultra-fast, pure Java2D component mapping Chromium's BGRA byte buffer to native little-endian `IntBuffer` and double-buffering into `BufferedImage` (`TYPE_INT_ARGB_PRE`).
  - Added dedicated popup layer compositing (`handlePopupPaint`, `popupBuffer`) for HTML select dropdowns, autocompletion menus, and context menus.
  - Permanently resolved the Java Heavyweight/Lightweight "airspace" problem: Swing popup menus, dialogs, and tooltips float flawlessly over web content without focus-stealing loops.
- **Dynamic HiDPI / Retina Scale Factor Auto-Detection**:
  - Enhanced `CefBrowserOsr` and `KromiumOSRPanel` with automatic display scale factor detection via `Graphics2D.getTransform().getScaleX()`.
  - Automatically notifies Chromium via `notifyScreenInfoChanged()` and `wasResized()`, delivering razor-sharp text and graphics on fractional scaling (125%, 150%) and Retina displays (200%) on Windows, macOS, and Linux.
- **Interaction & Tab Usability Improvements**:
  - Intercepted `target="_blank"` popups to cleanly spawn new tabs in desktop UI.
  - Tuned native mouse wheel scroll amplification for smooth desktop scrolling.
- **Rebuilt Diátaxis Documentation Hub**:
  - Rebuilt the entire `docs/` documentation directory from scratch across 22 guides covering Getting Started, Core Concepts, Guides, Reference, and Deployment with paired Compose Multiplatform and Universal Java Desktop examples.

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
