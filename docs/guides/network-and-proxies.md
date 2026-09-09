# Network Configuration & Proxy Switching

Kromium allows dynamic network configuration, runtime proxy switching without browser restarts, authenticated proxy support, and custom HTTP request interception.

---

## 🌐 Dynamic Runtime Proxy Switching

Unlike standard browser engines that require restarting the entire JVM process to change proxy settings, Kromium can change proxies on the fly per client context or globally.

### Available Proxy Types

Defined in `KromiumProxy`:
- `KromiumProxy.system()`: Uses the operating system's configured network proxy.
- `KromiumProxy.direct()`: Bypasses all proxies and connects directly.
- `KromiumProxy.http(host, port)`: HTTP proxy.
- `KromiumProxy.https(host, port)`: HTTPS proxy.
- `KromiumProxy.socks5(host, port)`: SOCKS5 proxy (ideal for SSH tunneling, Tor, and internal socks gateways).
- `KromiumProxy.pac(pacScriptUrl)`: Proxy Auto-Configuration via PAC script.

### Switching Proxies at Runtime (Kotlin)

```kotlin
import dev.daviante.kromium.domain.config.KromiumProxy

// Switch to a corporate SOCKS5 proxy on the fly:
val result = client.setProxy(KromiumProxy.socks5("127.0.0.1", 1080))

if (result.isSuccess) {
    println("Switched proxy successfully!")
} else {
    println("Failed to update proxy: ${result.exceptionOrNull()?.message}")
}
```

### Switching Proxies at Runtime (Pure Java)

In Java, `updateProxy` provides clean boolean ergonomics bypassing Kotlin Result class mangling:

```java
import dev.daviante.kromium.KromiumClient;
import dev.daviante.kromium.domain.config.KromiumProxy;

public final class ProxySwitchDemo {
    public static void rotateProxy(KromiumClient client, String host, int port) {
        boolean success = client.updateProxy(KromiumProxy.http(host, port));
        if (success) {
            System.out.println("Rotated proxy to: " + host + ":" + port);
        } else {
            System.err.println("Failed to update proxy.");
        }
    }
}
```

### Direct Browser Forwarder

You can also update the proxy directly via any `KromiumBrowser` instance:

```kotlin
browser.setProxy(KromiumProxy.socks5("127.0.0.1", 1080))
```

---

## 🔒 Isolated Client Sessions & Multi-Proxy Routing

By default, creating a client via `Kromium.newClient()` inherits the global request context. When you require **independent, concurrent proxies across different tabs or web crawlers**, instantiate an **isolated client**:

```kotlin
// Create an isolated client with a dedicated CefRequestContext:
val isolatedClient = Kromium.newIsolatedClient()
// Or: Kromium.newClient(isolated = true)

// Any proxy update here affects ONLY this client and its browsers:
isolatedClient.setProxy(KromiumProxy.http("proxy-node-1.corp", 8080))

val browser1 = isolatedClient.createBrowser("https://checkip.amazonaws.com")
```

---

## 🔑 Authenticated Proxies & HTTP Basic Auth

When a proxy server or web page returns an `HTTP 407 Proxy Authentication Required` or `HTTP 401 Unauthorized` challenge, handle credentials using `onAuthRequired` / `KromiumAuthListener`:

### Compose Desktop Auth Handler

```kotlin
val state = rememberKromiumViewState("https://internal-proxy.corp")

state.onAuthRequired = { authRequest ->
    if (authRequest.isProxy) {
        // Supply credentials for authenticated corporate proxy
        KromiumAuthResponse(username = "corp_user", password = "SecretPassword123")
    } else {
        // Handle standard website HTTP Basic Auth
        KromiumAuthResponse(username = "admin", password = "adminPassword")
    }
}
```

### Pure Java Auth Listener

```java
import dev.daviante.kromium.KromiumClient;
import dev.daviante.kromium.presentation.handler.KromiumAuthResponse;

client.setAuthListener(request -> {
    if (request.isProxy()) {
        return new KromiumAuthResponse("proxy_user", "proxy_pass");
    }
    return null; // Prompt user or cancel
});
```

---

## 🛰️ Request Headers & Custom Interception

Intercept, inspect, or modify outbound HTTP requests using `KromiumRequestInterceptor`:

```kotlin
client.requestInterceptor = KromiumRequestInterceptor { request ->
    // Inject corporate authorization or tracking headers:
    request.setHeader("X-Client-Version", "2.1.150")
    request.setHeader("X-Custom-Tenant-ID", "tenant-alpha-9")

    // Or block requests to tracking domains:
    if (request.url.contains("google-analytics.com")) {
        request.cancel()
    }
}
```
