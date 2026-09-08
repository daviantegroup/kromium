# Exceptions & Diagnostic Logging

Kromium features a strongly-typed, sealed exception hierarchy and a pluggable diagnostic logging pipeline that bridges into your application's existing logging framework (SLF4J, Logback, Log4j2, or java.util.logging).

---

## 🛑 Exception Hierarchy

Package: `dev.daviante.kromium.domain.exception.KromiumException`

All errors raised by Kromium inherit from the sealed class `KromiumException` (which extends `java.lang.RuntimeException`). This eliminates brittle string matching and allows programmatic handling of specific error cases.

```
KromiumException (abstract RuntimeException)
├── NotInitialized
├── Disposed
├── UnsupportedPlatform
├── InstallationFailed
├── AutoDownloadDisabled
├── NoBundleAvailable
├── DownloadFailed
├── ChecksumMismatch
├── ExtractionFailed
├── MaliciousArchiveEntry
├── BootstrapFailed
├── InstallationCorrupted
├── JsEvaluationTimeout
├── InvalidConfig
├── ProxyError
└── PdfPrintFailed
```

### Exception Catalog

| Exception | Thrown When | Recommended Action |
|:---|:---|:---|
| `NotInitialized` | An operation is attempted before `KromiumEngine.getInstance().initialize()` is called. | Initialize engine in `main()` before opening windows. |
| `Disposed` | A method is called on a browser or engine that has already been shut down. | Check lifecycle; don't reuse closed browser instances. |
| `AutoDownloadDisabled` | Native CEF binaries are missing from `installDir` and `autoDownload = false`. | Bundle JCEF binaries in your application installer or enable `autoDownload`. |
| `ChecksumMismatch` | Downloaded native bundle archive fails SHA256 checksum validation. | Verify network integrity or update `customChecksumUrl`. |
| `JsEvaluationTimeout` | JavaScript execution exceeds specified timeout (e.g. infinite loops in web page). | Check web script or increase timeout duration. |
| `ProxyError` | Dynamic proxy switching fails or proxy credentials are invalid. | Verify proxy host, port, and network reachability. |
| `PdfPrintFailed` | Async PDF export fails to render or write to the target file. | Check file write permissions or destination disk space. |

### Pattern-Matching Errors (Kotlin)

```kotlin
try {
    val pdf = browser.printToPdf(File("/protected/output.pdf"))
} catch (e: KromiumException) {
    when (e) {
        is KromiumException.PdfPrintFailed -> {
            println("Failed to write PDF to path: ${e.path}")
        }
        is KromiumException.Disposed -> {
            println("Cannot print: browser was already disposed.")
        }
        else -> println("Kromium error: ${e.message}")
    }
}
```

### Typed Handling (Pure Java)

```java
import dev.daviante.kromium.domain.exception.KromiumException;
import java.io.File;

browser.printToPdfAsync(new File("output.pdf")).exceptionally(throwable -> {
    Throwable cause = throwable.getCause();
    if (cause instanceof KromiumException.PdfPrintFailed pdfErr) {
        System.err.println("PDF generation failed on path: " + pdfErr.getPath());
    } else if (cause instanceof KromiumException.ProxyError proxyErr) {
        System.err.println("Proxy issue: " + proxyErr.getDetail());
    } else {
        System.err.println("Unexpected error: " + throwable.getMessage());
    }
    return null;
});
```

---

## 🪵 Pluggable Logging Pipeline

Package: `dev.daviante.kromium.core.logging.KromiumLogger`

By default, Kromium outputs diagnostic logs to `java.util.logging` via `JulKromiumLogger`. You can replace this default logger with your own adapter to route Kromium internal logs through SLF4J, Log4j2, or disable them entirely.

### Interface Definition

```kotlin
interface KromiumLogger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}
```

### Integrating with SLF4J / Logback (Kotlin & Java)

```kotlin
// In Kotlin:
import dev.daviante.kromium.core.logging.KromiumLogger
import org.slf4j.LoggerFactory

class Slf4jKromiumAdapter : KromiumLogger {
    override fun debug(tag: String, message: String) {
        LoggerFactory.getLogger(tag).debug(message)
    }
    override fun info(tag: String, message: String) {
        LoggerFactory.getLogger(tag).info(message)
    }
    override fun warn(tag: String, message: String, throwable: Throwable?) {
        LoggerFactory.getLogger(tag).warn(message, throwable)
    }
    override fun error(tag: String, message: String, throwable: Throwable?) {
        LoggerFactory.getLogger(tag).error(message, throwable)
    }
}

// Attach during startup:
KromiumLogger.instance = Slf4jKromiumAdapter()
```

```java
// In Pure Java:
import dev.daviante.kromium.core.logging.KromiumLogger;
import org.slf4j.LoggerFactory;

public final class Slf4jLoggerBridge implements KromiumLogger {
    @Override
    public void debug(String tag, String message) {
        LoggerFactory.getLogger(tag).debug(message);
    }

    @Override
    public void info(String tag, String message) {
        LoggerFactory.getLogger(tag).info(message);
    }

    @Override
    public void warn(String tag, String message, Throwable throwable) {
        LoggerFactory.getLogger(tag).warn(message, throwable);
    }

    @Override
    public void error(String tag, String message, Throwable throwable) {
        LoggerFactory.getLogger(tag).error(message, throwable);
    }
}

// Attach during application startup:
KromiumLogger.setInstance(new Slf4jLoggerBridge());
```

### Disabling Logs Completely

For automated test suites or production silence:

```kotlin
import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.logging.NoOpKromiumLogger

KromiumLogger.instance = NoOpKromiumLogger
```
