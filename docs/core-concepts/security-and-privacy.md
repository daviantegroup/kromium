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

Kromium provides fine-grained control over SSL/TLS certificate handling via `SslErrorPolicy`:

```kotlin
enum class SslErrorPolicy {
    Strict, // Immediately aborts navigation on invalid or self-signed certs (Default)
    Allow,  // Bypasses certificate validation (Use ONLY in internal offline test environments)
    Ask     // Invokes custom callback to prompt user or inspect certificate chain
}
```

### Configuring SSL Error Handling (Compose & Java)

```kotlin
// Compose Desktop: Enforce strict certificate validation
val state = rememberKromiumViewState("https://internal.corp")
state.sslErrorPolicy = SslErrorPolicy.Strict
```

```java
// Pure Java: Configure custom SSL policy
browser.setSslErrorPolicy(SslErrorPolicy.Strict);
```

---

## 🌐 Host Locking & Navigation Interception

Enterprise kiosk, healthcare, and POS applications often need to lock down the browser to authorized corporate domains.

Kromium enables declarative URL interception:

### Compose Desktop Host-Locking

```kotlin
val state = rememberKromiumViewState("https://app.internal.corp")

state.shouldOverrideUrlLoading = { targetUrl ->
    val uri = java.net.URI(targetUrl)
    val allowedHost = "app.internal.corp"
    
    if (uri.host == allowedHost || uri.scheme == "app") {
        false // Allow internal navigation
    } else {
        println("Blocked unauthorized navigation attempt to: $targetUrl")
        true  // Cancel navigation
    }
}
```

### Pure Java / Swing Host-Locking

```java
browser.setNavigationFilter(targetUrl -> {
    try {
        java.net.URI uri = new java.net.URI(targetUrl);
        return "app.internal.corp".equalsIgnoreCase(uri.getHost());
    } catch (Exception e) {
        return false; // Reject malformed URLs
    }
});
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
