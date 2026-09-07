# Asset Filtering & Scoped SSL Policies

[Documentation Hub](../README.md) &bull; **Guides** &bull; Asset Filtering & SSL Policies

---

## 🛑 Resource Asset Filtering (`KromiumAssetFilter`)

In embedded web views, kiosk applications, or performance-critical dashboards, you may want to block heavy media assets, external fonts, or third-party stylesheets:

```kotlin
import dev.daviante.kromium.presentation.network.KromiumAssetFilter
import dev.daviante.kromium.presentation.network.KromiumRequestInterceptor

// 1. Type-safe asset filter configuration
client.assetFilter = KromiumAssetFilter(
    blockImages = true,
    blockMedia = true,
    blockFonts = true,
    blockStylesheets = false
)

// Or attach to Compose state:
state.assetFilter = KromiumAssetFilter(blockImages = true, blockMedia = true)

// Or use the Compose convenience helper:
state.blockMediaAssets(images = true, media = true, fonts = true)
```

### Pre-Built Convenience Presets

```kotlin
// Block all images, media, fonts, and stylesheets for maximum performance
client.assetFilter = KromiumAssetFilter.ALL_BLOCKED

// Block images, media, and fonts while keeping stylesheets intact
client.assetFilter = KromiumAssetFilter.MEDIA_ONLY
```

### Custom URL & Script Blocking (`KromiumRequestInterceptor`)

To filter custom ad scripts, tracking beacons, or untrusted URLs:

```kotlin
client.requestInterceptor = KromiumRequestInterceptor { request ->
    // Block third-party tracking scripts and ad networks
    if (request.url.contains("adservice") || request.url.contains("doubleclick.net")) {
        return@KromiumRequestInterceptor true // Cancel request immediately
    }
    false // Allow resource
}
```

---

## 🔒 Strict Host-Locking

For secure kiosk apps, authentication windows, or dedicated tools, restrict browsing strictly to approved domains:

```kotlin
// Only allow navigation to myapp.com and its subdomains
client.hostLock = setOf("myapp.com", "auth.myapp.com")

// Also enforce host-lock on subresources (scripts, iframes, styles)
client.hostLockSubresources = true
```

Any navigation to an unapproved domain is blocked automatically before hitting the network.

---

## 🔐 Scoped SSL/TLS Certificate Policies (`SslErrorPolicy`)

By default, Kromium operates in `Strict` mode, rejecting any invalid, expired, self-signed, or untrusted TLS certificates.

For internal corporate deployments or staging environments using private self-signed certificates:

```kotlin
// 1. Strict Mode (Default - Recommended for Production)
client.sslErrorPolicy = SslErrorPolicy.Strict

// 2. Scoped Domain Whitelist (Permits self-signed certs ONLY on designated domains)
client.sslErrorPolicy = SslErrorPolicy.AllowDomains(
    "localhost",
    "127.0.0.1",
    "staging.internal.company.com"
)

// 3. Permissive Development Mode (NEVER ship to production)
client.sslErrorPolicy = SslErrorPolicy.AllowAll
```

> [!CAUTION]
> `SslErrorPolicy.AllowAll` completely disables TLS identity validation and exposes traffic to Man-in-the-Middle (MitM) attacks. Only use `AllowDomains` for staging servers.
