# Kromium Documentation Hub

Welcome to the official documentation for **Kromium**, the modern, production-grade Chromium Embedded Framework (CEF) library for **Compose Multiplatform Desktop** and **Kotlin JVM**.

---

## 🗺️ Documentation Architecture

Our documentation is structured into five distinct sections designed for different stages of development:

```
docs/
├── getting-started/      <- Onboarding, installation, and first application
├── core-concepts/        <- Architecture, state machine, and security foundation
├── guides/               <- Practical, task-oriented tutorials and how-tos
├── reference/            <- Exhaustive API signatures, configurations, and errors
└── deployment/           <- Packaging, platform nuances, and troubleshooting
```

---

## 🚀 Getting Started
Everything you need to install Kromium and launch your first embedded browser.

* [**Installation & Requirements**](getting-started/installation.md)  
  Gradle Kotlin DSL setup for Multiplatform & JVM, Maven Central coordinates, JVM 17/21+ module opening, and platform prerequisites.
* [**Quickstart with Compose Desktop**](getting-started/quickstart-compose.md)  
  Step-by-step tutorial: initializing Kromium, managing `rememberKromiumState`, and embedding `@Composable KromiumView`.
* [**Quickstart with Pure Kotlin JVM & Swing**](getting-started/quickstart-jvm.md)  
  Building traditional desktop applications using `JFrame`, `JPanel`, and Swing EDT threading.

---

## 🏛️ Core Concepts
Deep dives into Kromium's internal engine architecture and design philosophy.

* [**Engine Architecture & Bootstrapping**](core-concepts/architecture.md)  
  Dynamic JetBrains Runtime JCEF bundle downloads, SHA-256 verification, native binary loading, and multi-process architecture.
* [**State & Lifecycle Management**](core-concepts/state-and-lifecycle.md)  
  The `KromiumState` reactive state machine, mutex-guarded initialization, idempotency, and graceful shutdown.
* [**Security Hardening & Privacy**](core-concepts/security-and-privacy.md)  
  Chromium process sandboxing, automated Windows Registry write suppression, anti-telemetry switches, and dangerous flag validation.

---

## 📖 Practical Guides
In-depth, task-oriented guides with complete, copy-pasteable Kotlin code snippets.

* [**Compose Multiplatform UI Integration**](guides/compose-ui.md)  
  Reactive browser state, custom loading placeholders, multi-tab window implementations, and recomposition safety.
* [**Navigation, History & Page Controls**](guides/navigation-and-history.md)  
  URL navigation, back/forward history, zoom control, in-page text search (`find`), and print-to-PDF.
* [**JavaScript Bridge & Two-Way IPC**](guides/javascript-and-dom.md)  
  Suspendable `evaluateJavaScript`, cancellation timeouts, two-way query router (`window.cefQuery`), and DOM extraction (`getHtml`, `getText`).
* [**Network Interception & Enterprise Proxies**](guides/network-and-proxies.md)  
  Custom header injection, ad/tracker blocking, WPAD, PAC scripts, authenticated SOCKS5/HTTP proxies, dynamic runtime proxy switching, and NTLM/Kerberos SSO.
* [**Asset Filtering & Scoped SSL Policies**](guides/asset-filtering-and-security.md)  
  Media/font/script blocking (`KromiumAssetFilter`), strict host-locking for kiosk apps, and scoped `SslErrorPolicy`.
* [**Cookie & Session Management**](guides/cookie-management.md)  
  Suspendable `KromiumCookieManager`, setting/getting/deleting cookies, session isolation, and persistent disk flushing.
* [**Downloads, Dialogs & DevTools**](guides/downloads-and-dialogs.md)  
  Download tracking, pause/resume/cancel, JavaScript modal dialogs (`alert`, `confirm`, `prompt`), and console log redirection.
* [**Headless Browsing & Automation**](guides/headless-and-automation.md)  
  Zero-dependency headless browser backed by an off-screen Swing peer, background web scraping, and automated screenshots.

---

## 📚 API Reference
Exhaustive reference for every public class, configuration, handler, and error code.

* [**Engine Configuration (`KromiumConfig`)**](reference/configuration.md)  
  Complete catalogue of initialization properties, CEF flags, proxy strategies, and cache settings.
* [**Browser & Client API (`KromiumBrowser` & `KromiumClient`)**](reference/browser-and-client-api.md)  
  Complete signatures and documentation for all browser control methods and lifecycle APIs.
* [**Handlers, Listeners & Callbacks**](reference/handlers-and-events.md)  
  Event handlers: `CefLoadHandler`, `CefDisplayHandler`, `CefContextMenuHandler`, composite multiplexers, and download listeners.
* [**Error Handling & Logging (`KromiumException`)**](reference/exceptions-and-logging.md)  
  Complete sealed exception hierarchy, recovery strategies, and the pluggable `KromiumLogger`.

---

## 📦 Deployment & Platform Specifics
Packaging applications for production and resolving platform-specific quirks.

* [**Packaging & Distribution**](deployment/packaging-and-distribution.md)  
  Packaging with Conveyor, Gradle Compose distributions (`packageDmg`, `packageMsi`, `packageDeb`), ProGuard/R8 rules, and bundle size optimization.
* [**Platform-Specific Considerations**](deployment/platform-specifics.md)  
  macOS Apple Silicon/Intel framework symlinks and Gatekeeper signing, Linux native dependencies, and Windows VC++ runtimes.
* [**Troubleshooting & FAQ**](deployment/troubleshooting-and-faq.md)  
  Solutions to black screens, GPU compositing bugs, EDT deadlocks, Wayland/X11 quirks, and cache locking.
