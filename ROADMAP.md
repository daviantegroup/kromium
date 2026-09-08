# Kromium Strategic Roadmap

This document outlines the planned future capabilities and architectural milestones for **Kromium** to solidify its position as the premier, open-source Chromium Embedded Framework (CEF) library for both Kotlin Compose Multiplatform and pure Java desktop applications.

---

## 🚀 Upcoming Milestones

### 1. Custom Scheme & Virtual Asset Interception (`KromiumSchemeHandler`) — ✅ Completed
* **Goal**: Enable developers to serve local bundled assets (HTML/JS/CSS/WebAssembly) securely via custom protocols (e.g. `app://myapp/index.html` or `myproto://ui/`) without running an embedded HTTP server.
* **Delivered Capabilities**:
  - `KromiumCustomScheme`: Fine-grained Chromium security flags registration (`isStandard`, `isLocal`, `isSecure`, `isCorsEnabled`, `isFetchEnabled`).
  - `KromiumResourceHandler` & `KromiumSchemeHandlerFactory`: Chunked virtual streaming bridge to JCEF.
  - `KromiumSchemeHandler.fromClasspath(...)`: Streams assets directly from JVM ClassLoader/JAR resources with path traversal shields.
  - `KromiumSchemeHandler.fromDirectory(...)`: Streams local disk folders with canonical path boundaries.
  - Single Page Application (SPA) routing fallback with MIME-confusion protection (strictly for extensionless GET navigation requests).
  - Built-in `MimeTypes` resolving 50+ modern formats (WASM, ES Modules, SVG, WebP, WOFF2).
  - Dual Kotlin & pure Java ergonomics with SAM lambdas and `@JvmStatic` helpers.

### 2. Print to PDF & Native Print Dialog (`printToPdfAsync`) — ✅ Completed
* **Goal**: Provide automated, headless, and interactive document printing capabilities.
* **Delivered Capabilities**:
  - `KromiumPaperSize`: ISO & North American dimensions (A4, Letter, Legal, Tabloid, A3, A5) with millimeter/inch converters.
  - `KromiumPdfMargins`: Margin configurations (`Default`, `None`, `Minimum`, `Custom` in mm or inches).
  - `KromiumPdfSettings`: Full layout controls (orientation, background graphics, scale, page ranges, HTML header/footer templates, accessible tagged PDF, outline bookmarks, and directory creation).
  - Suspending `printToPdf(targetFile, settings): File` for Kotlin Coroutines in both `KromiumBrowser` and Compose `KromiumViewState`.
  - Non-blocking `printToPdfAsync(targetFile, settings): CompletableFuture<File>` with fluent Java builder for pure Java desktop apps.
  - Interactive native OS print dialog invocation via `print()`.
  - Typed error handling with `KromiumException.PdfPrintFailed`.

### 3. Context Menu Customization DSL & Actions
* **Goal**: Allow host applications to customize, add, remove, or completely replace the browser's right-click context menu.
* **Scope**:
  - `CefContextMenuHandler` abstraction with a declarative builder for both Kotlin and Java.
  - Built-in shortcuts for standard actions: "Inspect Element", "Copy Link", "Save Image As...", "Search Google for Selection".
  - Custom action callback execution.

### 4. Native OS Titlebar & Window Chrome Helper (`KromiumWindowChrome`)
* **Goal**: Provide an optional, turnkey helper for embedding native macOS traffic lights and Windows/Linux titlebar controls directly into custom browser tab strips.
* **Scope**:
  - In Swing: Preconfigured FlatLaf root pane client properties and macOS safe-area insets.
  - In Compose: Integrated `WindowDraggableArea` with platform-specific window control offsets.

### 5. Media & Web Permission Request Interception (`CefPermissionHandler`) — ✅ Completed
* **Goal**: Allow host applications to intercept, inspect, grant, deny, or selectively filter web permissions (Microphone, Camera, Screen Sharing, Desktop Audio) requested by web applications (e.g. WebRTC, Meet, Zoom).
* **Delivered Capabilities**:
  - `KromiumPermissionType`: Typed enum for native Chromium bitmasks (`AUDIO_CAPTURE`, `VIDEO_CAPTURE`, `DESKTOP_AUDIO`, `DESKTOP_VIDEO`) with `fromFlags` and `toFlags`.
  - `KromiumPermissionRequest`: Rich request model encapsulating target `url`, normalized `origin`, `requestedTypes`, `rawFlags`, and predicate helpers (`hasAudio()`, `hasVideo()`, `hasScreenShare()`, `hasDesktopAudio()`).
  - `KromiumPermissionDecision`: Sealed decision hierarchy supporting total `Grant`, fine-grained selective `Grant` (e.g. mic allowed but webcam blocked), and secure `Deny`.
  - `KromiumPermissionHandler`: SAM functional interface with turnkey presets (`grantAll()`, `denyAll()`, `forOrigins(...)`), compatible with Kotlin and pure Java lambdas.
  - In-Memory Session Caching: Automatic renegotiation caching (`rememberPermissions = true`) to prevent repetitive WebRTC prompts, with `clearPermissionCache()` for programmatic revocation.
  - Compose & Swing Integration: First-class `permissionHandler` and `rememberPermissions` state properties on both `KromiumViewState` and `KromiumClient`.
  - Security Standards: Unhandled requests default strictly to `Deny`.
