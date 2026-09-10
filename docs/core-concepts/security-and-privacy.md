# Security & Privacy by Design

Modern desktop applications must meet stringent enterprise security requirements. Kromium provides a hardened runtime environment engineered for zero-telemetry, sandbox enforcement, and strict origin isolation.

---

## 🛡️ Zero-Telemetry by Default

Standard Google Chrome builds contain numerous telemetry probes, Google account sync hooks, crash reporters, and usage metrics. Kromium disables these background phone-home mechanisms out of the box.

### Built-in Hardening Flags
Kromium automatically injects the following security and privacy switches into the native Chromium bootstrap arguments:
- `--disable-metrics` / `--disable-metrics-reporter`: Disables all Google UMA metrics collection.
- `--disable-breakpad` / `--disable-crash-reporter`: Prevents automatic crash dump transmissions to external servers.
- `--no-default-browser-check`: Disables system default browser prompts.
- `--disable-sync`: Disables Google Cloud account profile synchronization.
- `--disable-background-networking`: Suppresses background speculative DNS prefetching and auto-updates.
- `--enable-do-not-track`: Transmits Do Not Track (`DNT: 1`) signal on all outbound requests.
- `--webrtc-ip-handling-policy=default_public_interface_only`: Prevents binding to private LAN interfaces during WebRTC ICE candidate discovery.

---

## 🔒 WebRTC & Hardware Device Permission Model

WebRTC audio/video capture, system audio, and desktop media streams cannot be accessed by web pages without explicit application consent.

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
- `AUDIO_CAPTURE`: Microphone / audio input device access (`DEVICE_AUDIO_CAPTURE`).
- `VIDEO_CAPTURE`: Camera / webcam video input device access (`DEVICE_VIDEO_CAPTURE`).
- `DESKTOP_AUDIO`: System / desktop audio capture access (`DESKTOP_AUDIO_CAPTURE`).
- `DESKTOP_VIDEO`: Screen sharing / desktop video capture access (`DESKTOP_VIDEO_CAPTURE`).

### Origin Normalization & Domain Whitelisting

Kromium provides a turnkey origin-based handler `KromiumPermissionHandler.forOrigins` that automatically handles schemes, non-standard ports (e.g. `https://meet.corp.internal:8443`), and wildcards (`*.corp.internal`):

```kotlin
// Automatically permit trusted origins and reject all others:
client.permissionHandler = KromiumPermissionHandler.forOrigins(
    "meet.google.com",
    "*.corp.internal",
    "localhost"
)
```

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

## 🛡️ WebRTC IP Leak Defense & Handling Policies

Standard Chromium gathers ICE candidates across all network interfaces (including private LAN IPs like `192.168.x.x` and VPN adapters) via STUN/TURN over UDP. Even when an application routes through an authenticated corporate proxy, standard WebRTC can bypass the proxy and expose client IP addresses.

Kromium neutralizes this vulnerability by applying strict IP handling policies:

| Policy Value | Behavior | Default When |
|:---|:---|:---|
| `default_public_interface_only` | Restricts candidate discovery to public interfaces; prevents LAN IP exposure. | Default when `blockRegistryAndTelemetry = true` |
| `disable_non_proxied_udp` | Drops all non-proxied UDP traffic; forces WebRTC through configured proxy. | Default when custom proxy is active |
| `default` | Standard Chromium candidate discovery across all local interfaces. | Explicit opt-in |

### Configuring WebRTC IP Policy

```kotlin
// Kotlin DSL
val config = KromiumConfig().apply {
    webrtcIpHandlingPolicy = KromiumConfig.WEBRTC_POLICY_DISABLE_NON_PROXIED_UDP
}
```

```java
// Pure Java Fluent Builder
KromiumConfig config = KromiumConfig.builder()
    .webrtcIpHandlingPolicy(KromiumConfig.WEBRTC_POLICY_DISABLE_NON_PROXIED_UDP)
    .build();
```

---

## 📡 Tracking Protection: DNT & Global Privacy Control (GPC)

Kromium asserts tracking protections across both Chromium's native network stack and Kromium's request pipeline out of the box:
- `--enable-do-not-track` Chromium engine flag.
- Automatic injection of `DNT: 1` and `Sec-GPC: 1` headers on all outbound HTTP requests.

```kotlin
// Enabled by default; can be adjusted in config or client:
config.doNotTrack = true
client.doNotTrack = true
```

---

## 🧹 Purging Cookies & Web Storage (Privacy Reset)

For multi-tenant environments, kiosk resets, or secure sign-out flows, Kromium allows clearing both persistent cookies and HTML5 web storage (`localStorage` and `sessionStorage`):

```kotlin
// Clear cookies and web storage simultaneously:
browser.clearBrowsingData(clearCookies = true, clearStorage = true)

// Or clear storage independently:
browser.clearWebStorage()
```

```java
// Pure Java asynchronous purge:
browser.clearBrowsingDataAsync(true, true).thenAccept(success -> {
    System.out.println("Session data purged: " + success);
});
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

Kromium provides turnkey host locking that enforces origin boundaries on both top-level navigations (`onBeforeBrowse`) and asynchronous background network requests (`onBeforeResourceLoad` for scripts, xhr, and fetch).

### Frame Awareness & Third-Party Verification Widgets
By default, `setHostLock` only restricts main-frame navigations (`frame.isMain == true`). Embedded verification challenges (such as **Cloudflare Turnstile**, **Google reCAPTCHA**, or OAuth provider widgets) running in subframes/iframes continue to function seamlessly without violating your domain whitelist. If you need strict iframe restriction as well, set `lockSubframes = true`.

### Kotlin DSL (Compose Desktop & Core)

```kotlin
// Restrict top-level navigations (embedded iframes allowed by default):
browser.setHostLock("app.internal.corp", "auth.internal.corp")

// Restrict top-level navigations AND background subresources (scripts, fetch):
browser.setHostLock("app.internal.corp", "auth.internal.corp", lockSubresources = true)

// Fully lock top-level navigations, subresources, AND subframes/iframes:
browser.setHostLock("app.internal.corp", lockSubresources = true, lockSubframes = true)

// Or remove restrictions when exiting kiosk mode:
browser.clearHostLock()
```

### Pure Java / Swing Host-Locking

```java
// Turnkey host locking with subresource and subframe enforcement options:
browser.setHostLock(java.util.Set.of("app.internal.corp", "auth.internal.corp"), true, false);

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
