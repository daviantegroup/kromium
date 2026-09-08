# Configuration Catalog

This document details all configuration switches, flags, and options in **`KromiumConfig`**, **`KromiumChromeConfig`**, and **`KromiumProxy`**.

---

## ⚙️ `KromiumConfig`

Package: `dev.daviante.kromium.domain.config.KromiumConfig`

The master configuration object passed to `KromiumEngine.getInstance().initialize(config)` or `Kromium.initialize { ... }`.

### Options Table

| Property | Java Type | Default | Description |
|:---|:---|:---|:---|
| `installDir` | `java.io.File` | Platform default (`~/.kromium/jcef`) | Directory where native JCEF binaries are located or extracted. |
| `cachePath` | `String?` | `null` (in-memory) | Directory for persistent HTTP cache, cookies, and `localStorage`. |
| `userAgent` | `String?` | Standard Chromium | Custom global HTTP `User-Agent` header. |
| `windowlessRendering` | `boolean` | `false` | `false` enables high-performance Windowed GPU rendering; `true` enables Off-Screen Rendering (OSR). |
| `remoteDebuggingPort` | `int` | `0` (disabled) | TCP port for Chrome DevTools protocol (`chrome://inspect`). |
| `autoDownload` | `boolean` | `true` | When `false`, prohibits downloading native binaries if missing. Can also be set via `-Dkromium.auto.download=false`. |
| `sandboxEnabled` | `boolean` | `true` | Enforces OS sandboxing on renderer and utility processes. |
| `proxy` | `KromiumProxy` | `KromiumProxy.System` | Initial proxy routing configuration. |
| `blockRegistryAndTelemetry` | `boolean` | `true` | Strips telemetry, metrics, autorun entries, and Windows registry modifications. |
| `logSeverity` | `CefSettings.LogSeverity` | `LOGSEVERITY_DEFAULT` | Logging verbosity (`DEFAULT`, `INFO`, `WARNING`, `ERROR`, `DISABLE`). |
| `customBundleUrl` | `String?` | `null` | Internal mirror or CDN URL for fetching JCEF binaries in air-gapped corporate environments. |
| `customChecksumUrl` | `String?` | `null` | Optional SHA256 checksum URL for validating custom bundles. |
| `windowChromeConfig` | `KromiumChromeConfig?` | `null` | Native window titlebar and traffic light inset configuration. |

### Configuration Example (Kotlin DSL)

```kotlin
val config = KromiumConfig().apply {
    cachePath = File(System.getProperty("user.home"), ".myapp/browser-cache").absolutePath
    userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/128.0.0.0 Safari/537.36 MyApp/2.1"
    remoteDebuggingPort = 0 // Keep closed in production
    autoDownload = true
    sandboxEnabled = true
    blockRegistryAndTelemetry = true
    
    // Register custom virtual scheme:
    registerScheme("app") {
        isStandard = true
        isCorsEnabled = true
        isSecure = true
    }
}
```

### Configuration Example (Pure Java Builder)

```java
import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.domain.config.KromiumProxy;
import java.io.File;

KromiumConfig config = KromiumConfig.builder()
    .setCachePath(new File(System.getProperty("user.home"), ".myapp/cache").getAbsolutePath())
    .setUserAgent("EnterprisePortal/3.0 (Windows NT 10.0; Win64; x64)")
    .setAutoDownload(true)
    .setSandboxEnabled(true)
    .setBlockRegistryAndTelemetry(true)
    .setProxy(KromiumProxy.direct())
    .build();
```

---

## 🪟 `KromiumChromeConfig`

Package: `dev.daviante.kromium.presentation.chrome.KromiumChromeConfig`

Configures embedding custom browser tab strips, URL bars, and toolbars directly into the native OS window titlebar area (e.g. macOS traffic light insets).

### Options Table

| Property | Type | Default | Description |
|:---|:---|:---|:---|
| `enabled` | `boolean` | `false` | Whether custom window chrome styling is active. Disabled by default. |
| `macTrafficLightsWidth` | `int` | `76` | Width in points allocated for macOS traffic lights (close/minimize/maximize). |
| `macTrafficLightsHeight` | `int` | `38` | Height in points allocated for macOS traffic lights. |
| `transparentTitleBar` | `boolean` | `true` | Makes the titlebar background transparent on macOS. |
| `hideWindowTitle` | `boolean` | `true` | Hides the default OS title text so your custom header can fill the area. |

### Kotlin Setup

```kotlin
val chromeConfig = KromiumChromeConfig(
    enabled = true,
    macTrafficLightsWidth = 80,
    macTrafficLightsHeight = 40,
    transparentTitleBar = true,
    hideWindowTitle = true
)
```

### Pure Java Setup

```java
import dev.daviante.kromium.presentation.chrome.KromiumChromeConfig;

KromiumChromeConfig chromeConfig = KromiumChromeConfig.builder()
    .enabled(true)
    .macTrafficLightsWidth(80)
    .macTrafficLightsHeight(40)
    .transparentTitleBar(true)
    .hideWindowTitle(true)
    .build();
```

---

## 🌐 `KromiumProxy`

Package: `dev.daviante.kromium.domain.config.KromiumProxy`

Represents a proxy configuration strategy.

### Factory Methods

```java
// Pure Java & Kotlin Factories:
KromiumProxy.system();                        // Follow OS system proxy
KromiumProxy.direct();                        // Bypass all proxies (direct connection)
KromiumProxy.http("10.0.0.1", 8080);          // HTTP proxy
KromiumProxy.https("proxy.corp.internal", 443); // HTTPS proxy
KromiumProxy.socks5("127.0.0.1", 1080);       // SOCKS5 proxy
KromiumProxy.pac("https://pac.internal/proxy.pac"); // PAC script URL
```
