# Error Handling & Logging (`KromiumException`)

[Documentation Hub](../README.md) &bull; **API Reference** &bull; Exceptions & Logging

---

## 🚨 Sealed Exception Hierarchy (`KromiumException`)

All errors thrown by Kromium derive from `KromiumException`. This enables exhaustive `when` pattern matching without parsing generic string messages.

```kotlin
sealed class KromiumException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
```

### Exception Catalog

| Exception Class | Description | Recovery Strategy |
|:---|:---|:---|
| `NotInitialized` | Attempted to create a client/browser before calling `Kromium.initialize()`. | Ensure `Kromium.initialize()` runs during application startup. |
| `Disposed` | Attempted to use the engine after calling `Kromium.dispose()`. | Re-initialize engine or create fresh application instance. |
| `UnsupportedPlatform` | OS or CPU architecture is not supported (e.g. 32-bit x86). | Verify host OS meets system requirements. |
| `InstallationFailed` | Could not create or write to `installDir`. | Check file write permissions for user directory. |
| `DownloadFailed` | Network failure while downloading JCEF runtime archive. | Verify internet connectivity or configure `customBundleUrl`. |
| `ChecksumMismatch` | Downloaded archive hash did not match expected SHA-256. | Re-attempt download; check for proxy tampering. |
| `ExtractionFailed` | Archive decompression failed. | Check available disk space and filesystem permissions. |
| `MaliciousArchiveEntry` | Zip-Slip path traversal attempt detected in archive. | Ensure archive source is legitimate and untampered. |
| `BootstrapFailed` | Native JNI library linking or `CefApp.startup()` failed. | Verify VC++ Redistributable (Windows) or native libraries (Linux). |
| `InstallationCorrupted` | Installed bundle files are damaged or missing. | Clear engine directory via `EngineRegistry.clearInstallation()`. |
| `JsEvaluationTimeout` | JavaScript execution exceeded configured `timeoutMs`. | Check for infinite loops or increase timeout threshold. |
| `InvalidConfig` | Configuration validation rejected an invalid property. | Correct port numbers, URLs, or directory paths. |
| `ProxyError` | Dynamic runtime proxy update failed natively in CEF. | Verify proxy format and preference schema. |

---

## 📝 Pluggable Logging (`KromiumLogger`)

Kromium features a decoupled logging abstraction with zero external logging framework dependencies.

### Custom Logger Registration
Redirect internal Kromium log statements to SLF4J, Logback, Log4j2, or Kermit by assigning `KromiumLogger.instance`:

```kotlin
import dev.daviante.kromium.core.logging.KromiumLogger

KromiumLogger.instance = object : KromiumLogger {
    override fun debug(tag: String, message: String) = log.debug("[$tag] $message")
    override fun info(tag: String, message: String) = log.info("[$tag] $message")
    override fun warn(tag: String, message: String, throwable: Throwable?) = log.warn("[$tag] $message", throwable)
    override fun error(tag: String, message: String, throwable: Throwable?) = log.error("[$tag] $message", throwable)
}
```

### Direct Logging Helpers
You can also emit log messages through Kromium's configured logger:

```kotlin
KromiumLogger.d("MyTag", "Debug message")
KromiumLogger.i("MyTag", "Info message")
KromiumLogger.w("MyTag", "Warning message", throwable)
KromiumLogger.e("MyTag", "Error message", throwable)
```
