# Kromium Documentation Hub

Welcome to the official documentation for **Kromium**, the high-performance, enterprise-grade Chromium browser engine for **Compose Multiplatform Desktop** and **Pure Java (Swing / Enterprise JVM)** applications.

---

## 🗺️ Diátaxis Documentation Structure

Our documentation is organized following the [Diátaxis framework](https://diataxis.fr/) to help you learn, solve problems, understand architecture, and look up technical specifications:

```
docs/
├── getting-started/       # 🚀 Learning-oriented tutorials for quick onboarding
│   ├── installation.md
│   ├── quickstart-compose.md
│   └── quickstart-jvm.md
│
├── core-concepts/         # 💡 Understanding-oriented architectural deep dives
│   ├── architecture.md
│   ├── state-and-lifecycle.md
│   └── security-and-privacy.md
│
├── guides/                # 🛠️ Task-oriented how-to guides for real-world scenarios
│   ├── compose-ui.md
│   ├── navigation-and-history.md
│   ├── javascript-and-dom.md
│   ├── network-and-proxies.md
│   ├── asset-filtering-and-security.md
│   ├── cookie-management.md
│   ├── downloads-and-dialogs.md
│   └── headless-and-automation.md
│
├── reference/             # 📖 Information-oriented technical references and APIs
│   ├── browser-and-client-api.md
│   ├── handlers-and-events.md
│   ├── configuration.md
│   └── exceptions-and-logging.md
│
└── deployment/            # 📦 Operations, native packaging, and platform guides
    ├── packaging-and-distribution.md
    ├── platform-specifics.md
    └── troubleshooting-and-faq.md
```

---

## 🚀 Getting Started

New to Kromium? Start here:

1. [**Installation & Setup**](getting-started/installation.md)  
   Gradle (Kotlin & Groovy) coordinates, Maven POM configuration, OS-level prerequisites, and JVM module flags.
2. [**Quickstart: Compose Multiplatform Desktop**](getting-started/quickstart-compose.md)  
   Build a complete reactive desktop browser with `@Composable KromiumView`, state observers, native window chrome, custom context menus, WebRTC permissions, and PDF printing in minutes.
3. [**Quickstart: Pure Java / Swing**](getting-started/quickstart-jvm.md)  
   Mount `KromiumBrowser` directly inside enterprise `JFrame` or `JPanel` windows with zero Kotlin runtime dependencies, using standard `CompletableFuture` and SAM lambdas.

---

## 💡 Core Concepts

Understand how Kromium works under the hood:

- [**Architecture & Process Model**](core-concepts/architecture.md)  
  Multi-process Chromium architecture, JCEF bridging layer, Off-Screen Rendering (OSR) vs. Windowed rendering, and thread boundaries.
- [**State & Lifecycle Management**](core-concepts/state-and-lifecycle.md)  
  Engine initialization, reactive `KromiumState` flows, navigation state machines, composition disposal, and clean process teardown.
- [**Security & Privacy by Design**](core-concepts/security-and-privacy.md)  
  Zero-telemetry enforcement, Windows registry bypass, origin sandboxing, WebRTC permission security model, and remote debugging security.

---

## 🛠️ Practical How-To Guides

Step-by-step guides for common desktop application requirements:

- [**Compose UI Integration & Window Chrome**](guides/compose-ui.md)  
  Overlay Compose components over WebGL canvases, custom window chrome titlebars, draggable regions, and tab strip insets.
- [**Navigation & History Controls**](guides/navigation-and-history.md)  
  Back/forward stacks, stop/reload, address bar synchronization, loading indicators, and intercepting link clicks.
- [**JavaScript Execution & DOM Bridge**](guides/javascript-and-dom.md)  
  Evaluate JavaScript asynchronously, parse structured return values, and establish bi-directional IPC bridges between Web and JVM.
- [**Network Configuration & Proxy Switching**](guides/network-and-proxies.md)  
  Dynamic proxy switching at runtime (HTTP, HTTPS, SOCKS5), authenticated proxies, and custom network headers.
- [**Virtual Asset Streaming (`app://`) & Security Filters**](guides/asset-filtering-and-security.md)  
  Serve single-page React/Vue apps from local JAR resources without spinning up an HTTP server, plus request blocking and ad-filtering.
- [**Cookie & Session Management**](guides/cookie-management.md)  
  Inspect, inject, and delete HTTP cookies, configure persistent encrypted storage, and manage partitioned session data.
- [**Downloads & Native Dialogs**](guides/downloads-and-dialogs.md)  
  Intercept file downloads with progress tracking, pause/resume, and customize native file choosers and JavaScript alert/prompt dialogs.
- [**Headless Automation & PDF Generation**](guides/headless-and-automation.md)  
  Run off-screen headless instances for automated testing, web scraping, and background vector PDF generation in CI/CD pipelines.

---

## 📖 API Reference

Complete class catalogs, methods, and configurations:

- [**Browser & Client API Reference**](reference/browser-and-client-api.md)  
  Detailed documentation for `KromiumBrowser`, `KromiumClient`, and `KromiumViewState`.
- [**Handlers & Event Listeners**](reference/handlers-and-events.md)  
  `KromiumContextMenuHandler`, `KromiumPermissionHandler`, `KromiumDownloadListener`, `KromiumLoadListener`, and display handlers.
- [**Configuration Catalog**](reference/configuration.md)  
  All settings in `KromiumConfig`, `KromiumChromeConfig`, proxy configurations, cache directories, and switch flags.
- [**Exceptions & Diagnostic Logging**](reference/exceptions-and-logging.md)  
  Kromium error hierarchy (`PdfPrintFailed`, `ProxyError`, `KromiumException`) and plugging into `KromiumLogger`.

---

## 📦 Deployment & Native Packaging

Deliver polished, native desktop executables:

- [**Packaging & Distribution**](deployment/packaging-and-distribution.md)  
  Package with Compose Gradle plugin (`packageDmg`, `packageMsi`, `packageDeb`), jpackage, or Conveyor.
- [**Platform-Specific Considerations**](deployment/platform-specifics.md)  
  macOS notarization, app entitlements (camera/microphone), Windows DPI scaling, Linux GTK3/ALSA dependencies, and Wayland compatibility.
- [**Troubleshooting & FAQ**](deployment/troubleshooting-and-faq.md)  
  Diagnose native crashes, missing shared libraries, GPU acceleration issues, and frequent enterprise integration questions.
