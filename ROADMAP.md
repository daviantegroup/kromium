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

### 2. Print to PDF & Native Print Dialog (`printToPdfAsync`)
* **Goal**: Provide automated, headless, and interactive document printing capabilities.
* **Scope**:
  - `CompletableFuture<File> printToPdfAsync(File targetPdf, PdfPrintSettings settings)` for Java.
  - Suspending `printToPdf(targetPdf, settings)` for Kotlin.
  - Support for header/footer customization, background graphics, page margins, and orientation.

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

### 5. Media & Web Permission Request Interception (`CefPermissionHandler`)
* **Goal**: Allow host applications to grant, deny, or prompt users for web permissions (Microphone, Camera, Geolocation, Notifications).
* **Scope**:
  - SAM functional interface for Java: `(origin, permissionType) -> PermissionDecision`.
  - Automatic memory persistence for allowed domains.
