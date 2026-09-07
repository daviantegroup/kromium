# Engine Configuration (`KromiumConfig`)

[Documentation Hub](../README.md) &bull; **API Reference** &bull; Configuration

---

## ⚙️ Overview

Configuration is applied in the `Kromium.initialize { ... }` block before the native Chromium environment is initialized:

```kotlin
Kromium.initialize {
    installDir = File("/opt/kromium/engine")
    userAgent = "MyDesktopApp/2.0"
    remoteDebuggingPort = 9222
    sandboxEnabled = true
    blockRegistryAndTelemetry = true
    proxy = KromiumProxy.AutoDetect
}
```

---

## 📋 Property Reference

| Property | Type | Default | Description |
|:---|:---|:---|:---|
| `installDir` | `File` | `EngineRegistry.defaultInstallDir()` | Directory where JCEF runtime binaries and native libraries are extracted. |
| `cachePath` | `String?` | `null` *(Auto-resolved)* | Filesystem path for cookies, cache, and LocalStorage. `null` isolates cache to `installDir/cache`. |
| `userAgent` | `String?` | `null` *(Default Chromium)* | Global User-Agent string applied across all browser clients and requests. |
| `remoteDebuggingPort` | `Int` | `0` *(Disabled)* | Port for Chrome DevTools remote debugging inspector (`0` to `65535`). |
| `sandboxEnabled` | `Boolean` | `true` | Enables Chromium OS-level process sandboxing for renderers and GPU subprocesses. |
| `blockRegistryAndTelemetry` | `Boolean` | `true` | Suppresses Windows Registry modifications, Crashpad, Omaha updates, and telemetry. |
| `proxy` | `KromiumProxy` | `KromiumProxy.System` | Proxy strategy (`System`, `Direct`, `AutoDetect`, `Pac`, `Http`, `Socks5`, `MultiProtocol`). |
| `authServerAllowlist` | `List<String>` | `emptyList()` | Servers/proxies permitted for Integrated Windows Authentication (NTLM / Kerberos SSO). |
| `authNegotiateDelegateAllowlist` | `List<String>` | `emptyList()` | Servers permitted for Kerberos credential delegation. |
| `releaseTag` | `String?` | `null` *(Latest)* | Specific JetBrains Runtime release tag to target for engine download. |
| `customBundleUrl` | `String?` | `null` | Direct archive download URL for air-gapped or internal enterprise mirrors. |
| `customChecksumUrl` | `String?` | `null` | Checksum file URL to verify against a `customBundleUrl`. |
| `logSeverity` | `CefSettings.LogSeverity` | `LOGSEVERITY_DEFAULT` | Native CEF log verbosity (`DEFAULT`, `VERBOSE`, `INFO`, `WARNING`, `ERROR`, `DISABLE`). |
| `windowlessRendering` | `Boolean` | `false` | Enables CEF windowless off-screen rendering. Default `false` for Swing/Compose integration. |
| `commandLineArgs` | `MutableList<String>` | *(Pre-populated)* | Mutable list of raw command-line switches passed to the CEF subprocess. |

---

## 🛠️ Command-Line Switch Methods

```kotlin
// Append custom Chromium flags
Kromium.initialize {
    addArgs(
        "--enable-experimental-web-platform-features",
        "--autoplay-policy=no-user-gesture-required"
    )
}
```

### Pre-Configured Default Flags
By default, Kromium includes hardware optimization and security hardening switches:
* `--disable-gpu-compositing`
* `--enable-begin-frame-scheduling`
* `--disable-extensions`
* `--disable-plugins`
* Anti-telemetry flags when `blockRegistryAndTelemetry` is true
