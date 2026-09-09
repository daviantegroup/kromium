# Security & Privacy by Design

Modern desktop applications must meet stringent enterprise security requirements. Kromium provides a hardened runtime environment engineered for zero-telemetry, sandbox enforcement, and strict origin isolation.

---

## 🛡️ Zero-Telemetry by Default

Standard Google Chrome builds contain numerous telemetry probes, Google account sync hooks, crash reporters, and usage metrics. Kromium disables these background phone-home mechanisms out of the box.

### Built-in Hardening Flags
Kromium automatically injects the following security and privacy switches into the native Chromium bootstrap arguments:
- `--disable-metrics` / `--disable-metrics-reporter`: Disables all Google UMA metrics collection.
- `--disable-breakpad`: Prevents automatic crash dump transmissions to external servers.
- `--no-default-browser-check`: Disables system default browser prompts.
- `--disable-sync`: Disables Google Cloud account profile synchronization.
- `--disable-background-networking`: Suppresses background speculative DNS prefetching and auto-updates.

---

## 🔒 WebRTC & Hardware Device Permission Model

WebRTC audio/video capture, geolocation, and desktop media streams cannot be accessed by web pages without explicit application consent.

Kromium features a comprehensive permission architecture via `KromiumPermissionHandler` and `KromiumPermissionRequest`:

```
Web Page (navigator.mediaDevices.getUserMedia)
                      │
                      ▼
        [Native Chromium Security Manager]
                      │
                      ▼
         [KromiumPermissionHandler]
                      │
         ┌────────────┴────────────┐
         ▼                         ▼
   [Allow Once / Always]       [Deny / Reject]
```

### Supported Permission Types

Defined in `KromiumPermissionType`:
- `DEVICE_AUDIO_CAPTURE`: Microphone access.
- `DEVICE_VIDEO_CAPTURE`: Webcam access.
- `GEOLOCATION`: Precise GPS / network location.
- `DESKTOP_AUDIO_CAPTURE` / `DESKTOP_VIDEO_CAPTURE`: Screen sharing / desktop capture.
- `PROTECTED_MEDIA_IDENTIFIER`: DRM / Encrypted Media Extensions (EME).

### Session Caching & Memory Protection

By default, permission decisions are remembered per origin for the duration of the active browser session (`rememberPermissions = true`). You can revoke these permissions at any time:

```kotlin
// Compose Desktop
val state = rememberKromiumViewState("https://meet.jit.si")
state.clearPermissionCache()
```

```java
// Pure Java
client.clearPermissionCache();
```

---

## 🔐 SSL/TLS Certificate Verification & Policies

Kromium provides fine-grained control over SSL/TLS certificate handling via the `SslErrorPolicy` sealed class hierarchy:

```kotlin
sealed class SslErrorPolicy {
    /** Reject all certificate errors (Default, strongly recommended for production). */
    data object Strict : SslErrorPolicy()

    /** Allow certificate errors exclusively for specified domains and subdomains (e.g. "*.corp.internal", "localhost"). */
    data class AllowDomains(val domains: Set<String>) : SslErrorPolicy()

    /** ⚠️ DANGEROUS: Allow ALL certificate errors for ALL domains (Development/testing only). */
    data object AllowAll : SslErrorPolicy()
}
```

### Configuring SSL Error Handling (Kotlin & Java)

Configure globally on `KromiumConfig` or per-browser instance:

```kotlin
// 1. Globally at engine initialization:
val config = KromiumConfig().apply {
    sslErrorPolicy = SslErrorPolicy.AllowDomains("*.internal.corp", "localhost")
}
Kromium.initialize(config)

// 2. Or dynamically per browser:
browser.sslErrorPolicy = SslErrorPolicy.Strict
```

```java
// Pure Java with fluent helpers:
KromiumConfig config = KromiumConfig.builder()
    .sslErrorPolicy(SslErrorPolicy.allowDomains("*.internal.corp", "localhost"))
    .build();
Kromium.initialize(config);

// Dynamically per browser:
browser.setSslErrorPolicy(SslErrorPolicy.strict());
```

---

## 🌐 Host Locking & Subresource Isolation

Enterprise kiosk, healthcare, and POS applications often need to strictly restrict the browser to authorized corporate domains.

Kromium provides turnkey host locking that enforces origin boundaries on both top-level navigations (`onBeforeBrowse`) and asynchronous background network requests (`onBeforeResourceLoad` for scripts, xhr, and fetch):

### Kotlin DSL (Compose Desktop & Core)

```kotlin
// Restrict top-level navigations AND background subresources to authorized domains:
browser.setHostLock("app.internal.corp", "auth.internal.corp", lockSubresources = true)

// Or remove restrictions when exiting kiosk mode:
browser.clearHostLock()
```

### Pure Java / Swing Host-Locking

```java
// Turnkey host locking with subresource enforcement:
browser.setHostLock(java.util.Set.of("app.internal.corp", "auth.internal.corp"), true);

// Or clear lock:
browser.clearHostLock();
```

---

## 🐞 Remote Debugging Port Security

Chromium allows attaching Chrome DevTools (`chrome://inspect`) over a TCP socket via `--remote-debugging-port`.

> [!CAUTION]
> **Never open a remote debugging port in production builds accessible on public network interfaces (`0.0.0.0`).**
> Any local process could connect to this port and execute arbitrary JavaScript with full document access.

```kotlin
val config = KromiumConfig(
    // Disabled (0) by default for production safety:
    remoteDebuggingPort = if (BuildConfig.DEBUG) 9222 else 0
)
```

To inspect your app during development:
1. Set `remoteDebuggingPort = 9222`.
2. Open Google Chrome or Microsoft Edge and navigate to `http://localhost:9222`.
3. Inspect DOM elements, network waterfalls, and console logs live.
