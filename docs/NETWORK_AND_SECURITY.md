# Network Interception & Security Reference

[Kromium Documentation](README.md) &bull; [Interactive Web Portal](https://kromium.daviante.dev/#/docs/network-interception)

This document covers Kromium's network layer, request and header interception, navigation override handlers, SSL certificate policies, proxy routing, and security sandboxing.

---

## Table of Contents

1. [Network Request Interception](#network-request-interception)
   - [The `KromiumRequestInterceptor` Interface](#the-kromiumrequestinterceptor-interface)
   - [Blocking Requests (Ad & Tracker Blocking)](#blocking-requests-ad--tracker-blocking)
   - [Modifying and Injecting HTTP Headers](#modifying-and-injecting-http-headers)
   - [Custom User-Agent Injection](#custom-user-agent-injection)
2. [Navigation Overrides (`shouldOverrideUrlLoading`)](#navigation-overrides-shouldoverrideurlloading)
   - [Intercepting OAuth Redirects and Custom URI Schemes](#intercepting-oauth-redirects-and-custom-uri-schemes)
3. [SSL/TLS Certificate Error Handling (`SslErrorPolicy`)](#ssltls-certificate-error-handling-sslerrorpolicy)
   - [Strict Mode (Default)](#strict-mode-default)
   - [Domain Whitelist Mode (`AllowDomains`)](#domain-whitelist-mode-allowdomains)
   - [Permissive Development Mode (`AllowAll`)](#permissive-development-mode-allowall)
4. [Proxy Configuration (`KromiumProxy`)](#proxy-configuration-kromiumproxy)
   - [System Proxy](#system-proxy)
   - [Direct Connection](#direct-connection)
   - [HTTP/HTTPS Proxy](#httphttps-proxy)
   - [SOCKS5 Proxy](#socks5-proxy)
   - [Proxy Authentication](#proxy-authentication)
5. [Chromium Security Hardening & Sandboxing](#chromium-security-hardening--sandboxing)
   - [Sandbox Protection](#sandbox-protection)
   - [Built-in Default Security Flags](#built-in-default-security-flags)
   - [Dangerous Flag Validation](#dangerous-flag-validation)

---

## Network Request Interception

Kromium intercepts all network traffic flowing through CEF via an internal `CefResourceRequestHandlerAdapter`.

### The `KromiumRequestInterceptor` Interface

```kotlin
fun interface KromiumRequestInterceptor {
    fun intercept(request: KromiumWebResourceRequest): Boolean
}
```

The incoming `KromiumWebResourceRequest` provides:

| Property | Type | Description |
|---|---|---|
| `url` | `String` | Absolute destination URL of the requested resource. |
| `method` | `String` | HTTP method (e.g. `"GET"`, `"POST"`). |
| `headers` | `MutableMap<String, String>` | Editable map of request headers. Mutations are applied back to the native CEF request. |
| `isNavigation` | `Boolean` | `true` if this request corresponds to main-frame page navigation; `false` for sub-resources (images, scripts, stylesheets, iframes). |
| `isDownload` | `Boolean` | `true` if the request was initiated as a file download. |
| `requestInitiator` | `String?` | The origin that initiated the request. |

**Return value:**
- Return `true` to **block/cancel** the request immediately.
- Return `false` to **allow** the request to proceed.

---

### Blocking Requests (Ad & Tracker Blocking)

You can block specific resource domains, tracking pixels, or analytic beacons:

```kotlin
val trackerBlocker = KromiumRequestInterceptor { request ->
    val url = request.url.lowercase()
    
    // Check against known tracking domains
    val isBlocked = url.contains("doubleclick.net") ||
                    url.contains("google-analytics.com") ||
                    url.contains("facebook.com/tr") ||
                    url.endsWith(".bad-ad-network.com")

    if (isBlocked) {
        println("Blocked tracking request: ${request.url}")
        return@KromiumRequestInterceptor true // Cancel request
    }

    false // Allow request
}

// In Compose:
state.requestInterceptor = trackerBlocker

// In raw JVM:
client.requestInterceptor = trackerBlocker
```

---

### Modifying and Injecting HTTP Headers

Because `request.headers` is a `MutableMap<String, String>`, any modifications are applied back to the underlying `CefRequest` before the request hits the network:

```kotlin
val authHeaderInterceptor = KromiumRequestInterceptor { request ->
    // Inject Authorization header for internal API calls
    if (request.url.startsWith("https://api.mycompany.com/")) {
        request.headers["Authorization"] = "Bearer my-jwt-token-xyz"
        request.headers["X-Client-Version"] = "2.4.0"
    }

    // Strip Referer header for privacy
    request.headers.remove("Referer")

    false // Allow request to proceed with modified headers
}

state.requestInterceptor = authHeaderInterceptor
```

---

### Custom User-Agent Injection

You can configure a custom User-Agent in two ways:

#### 1. Globally at Engine Startup
```kotlin
Kromium.initialize {
    userAgent = "MyDesktopApp/1.0 (Windows NT 10.0; Win64; x64)"
}
```

#### 2. Per-Client or Per-State
Overrides the global setting for that specific browser:

```kotlin
// Compose:
state.userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"

// Raw JVM:
client.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
```

---

## Navigation Overrides (`shouldOverrideUrlLoading`)

To intercept top-level navigation before the browser commits to loading a new URL (equivalent to Android WebView's `shouldOverrideUrlLoading`), use `shouldOverrideUrlLoading`:

```kotlin
state.shouldOverrideUrlLoading = { url ->
    when {
        // 1. Handle custom deep link scheme
        url.startsWith("myapp://open-settings") -> {
            openApplicationSettingsWindow()
            true // Cancel browser navigation
        }

        // 2. Intercept OAuth 2.0 redirect
        url.startsWith("https://my-app.auth/callback") -> {
            val code = url.substringAfter("code=").substringBefore("&")
            processOAuthAuthorizationCode(code)
            true // Prevent browser from rendering the empty callback URL
        }

        // 3. Launch external links in the OS default browser
        url.startsWith("mailto:") || url.contains("twitter.com") -> {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
            true // Block internal navigation
        }

        else -> false // Let the browser load the page normally
    }
}
```

---

## SSL/TLS Certificate Error Handling (`SslErrorPolicy`)

Kromium manages SSL certificate verification through a sealed hierarchy: `SslErrorPolicy`.

### Strict Mode (Default)
Rejects all certificate validation errors (expired certificates, self-signed certificates, domain mismatches, untrusted root authorities).

```kotlin
state.sslErrorPolicy = SslErrorPolicy.Strict
```

### Domain Whitelist Mode (`AllowDomains`)
Permits SSL certificate errors **only** for specified hosts or wildcard subdomains (useful for internal staging servers or development VMs without compromising global security):

```kotlin
// Matches "localhost", "127.0.0.1", "staging.internal", and "*.staging.internal"
state.sslErrorPolicy = SslErrorPolicy.AllowDomains(
    "localhost",
    "127.0.0.1",
    "staging.internal"
)
```

### Permissive Development Mode (`AllowAll`)
Bypasses all certificate validation errors for all domains.

```kotlin
state.sslErrorPolicy = SslErrorPolicy.AllowAll
```

> [!CAUTION]
> **Security Warning**: `SslErrorPolicy.AllowAll` disables all TLS security and renders your application vulnerable to Man-in-the-Middle (MitM) attacks. Never ship this setting to production!

---

## Proxy Configuration (`KromiumProxy`)

Proxy settings are configured during `Kromium.initialize()` via `KromiumConfig.proxy`:

```kotlin
Kromium.initialize {
    proxy = KromiumProxy.Http(host = "10.0.0.1", port = 8080)
}
```

### Proxy Variants:

| Type | Syntax | Generated CEF Flag |
|---|---|---|
| **System** | `KromiumProxy.System` | *(Default CEF system proxy detection)* |
| **Direct** | `KromiumProxy.Direct` | `--no-proxy-server` |
| **HTTP** | `KromiumProxy.Http(host, port, username, password)` | `--proxy-server=http://host:port` |
| **SOCKS5** | `KromiumProxy.Socks5(host, port)` | `--proxy-server=socks5://host:port` |

### Proxy Authentication

If your HTTP or SOCKS proxy requires credentials, provide them using the authentication listener:

```kotlin
state.onAuthRequired = { req ->
    if (req.isProxy) {
        KromiumAuthResponse.Proceed(
            username = "proxy_user",
            password = "proxy_password_123"
        )
    } else {
        KromiumAuthResponse.Cancel
    }
}
```

---

## Chromium Security Hardening & Sandboxing

Kromium is engineered with default security configurations to protect desktop users from malicious web content.

### Sandbox Protection
The Chromium sandbox isolates renderer and GPU child processes from the operating system:

```kotlin
Kromium.initialize {
    // Enabled by default. Disabling this removes OS-level isolation.
    sandboxEnabled = true
}
```

### Built-in Default Security Flags
The following flags are automatically injected into CEF unless manually modified:
- `--disable-extensions`: Disables loading third-party browser extensions.
- `--disable-plugins`: Disables NPAPI and PPAPI plugins (Flash, Java plugins, etc.).
- `--disable-gpu-compositing`: Optimizes stability in off-screen rendering contexts.

### Dangerous Flag Validation
During `KromiumConfig.validate()`, Kromium inspects command-line arguments and logs high-priority warnings if dangerous flags are passed:
- `--no-sandbox`
- `--disable-web-security` (disables Same-Origin Policy)
- `--allow-running-insecure-content` (allows HTTP content on HTTPS pages)

```
⚠️ Dangerous command-line flag detected: --disable-web-security — This significantly reduces security.
```

### Input Validation & Path Traversal Protections (CWE-022 & CWE-078/088)

Kromium incorporates defensive programming across file I/O and process spawning:
- **Directory Traversal Validation**: `FileUtils.sanitizeDirectory()` and `FileUtils.resolveChild()` resolve real canonical paths and guarantee that operations on `installDir`, `cachePath`, and `downloadDirectory` remain strictly confined to their intended root boundaries, preventing CWE-022 path injection.
- **Archive Extraction & Symlink Sandboxing**: `EngineExtractor` validates canonical target paths for every entry and symbolic link during tarball extraction. Any entry attempting to navigate outside the extraction root (e.g. `../../bin/sh`) triggers `KromiumException.MaliciousArchiveEntry`.
- **Command-Line Injection Isolation**: Helper process invocations pass arguments as discrete, separate elements in `ProcessBuilder` without shell interpolation, guarding against CWE-078/088 argument injection attacks.

---

[← Return to Documentation Index](README.md) &bull; [Visit Online Documentation](https://kromium.daviante.dev/#/docs/network-interception)
