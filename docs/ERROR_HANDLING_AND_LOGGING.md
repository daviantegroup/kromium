# Error Handling & Logging Reference

This guide catalogs the complete `KromiumException` sealed error hierarchy, runtime diagnostic codes, and the pluggable `KromiumLogger` logging subsystem.

---

## Table of Contents

1. [Overview](#overview)
2. [Sealed Exception Catalog (`KromiumException`)](#sealed-exception-catalog-kromiumexception)
   - [Lifecycle & State Errors](#lifecycle--state-errors)
   - [Platform & Provisioning Errors](#platform--provisioning-errors)
   - [Archive & Security Errors](#archive--security-errors)
   - [Execution & Runtime Errors](#execution--runtime-errors)
3. [Exhaustive Pattern Matching Example](#exhaustive-pattern-matching-example)
4. [Logging Subsystem (`KromiumLogger`)](#logging-subsystem-kromiumlogger)
   - [Default Logger (`JulKromiumLogger`)](#default-logger-julkromiumlogger)
   - [Silent / Testing Mode (`NoOpKromiumLogger`)](#silent--testing-mode-noopkromiumlogger)
   - [Integrating with SLF4J, Logback, or Kermit](#integrating-with-slf4j-logback-or-kermit)
5. [CEF Native Log Severity](#cef-native-log-severity)

---

## Overview

Kromium models all engine-level and operational errors through a dedicated, type-safe sealed class: `KromiumException`. This avoids generic `IllegalStateException` or `RuntimeException` handling, allowing your code to respond specifically to network failures, corrupt caches, unsupported platforms, or security violations.

---

## Sealed Exception Catalog (`KromiumException`)

All Kromium exceptions inherit from `KromiumException`, which extends `java.lang.Exception`.

### Lifecycle & State Errors

| Exception | Parameters | Trigger Condition | Recommended Recovery |
|---|---|---|---|
| `NotInitialized` | None | `Kromium.newClient()` was called before `Kromium.initialize()` completed. | Call `Kromium.initialize()` before instantiating clients or browsers. |
| `Disposed` | None | An operation was invoked after `Kromium.dispose()` shut down the engine. | Prevent creating clients after app teardown. |
| `InvalidConfig` | `detail: String` | `KromiumConfig.validate()` failed (e.g. `remoteDebuggingPort < 0 || > 65535`). | Fix configuration parameters in the `initialize` lambda. |

---

### Platform & Provisioning Errors

| Exception | Parameters | Trigger Condition | Recommended Recovery |
|---|---|---|---|
| `UnsupportedPlatform` | `os: String?`, `arch: String?` | Detected OS or CPU is not supported by JCEF. | Notify user that their system architecture is unsupported. |
| `InstallationFailed` | `directory: String`, `cause: Throwable?` | Unable to create or write to the engine install directory. | Check user directory write permissions or supply a custom `installDir`. |
| `NoBundleAvailable` | `platform: String`, `releaseTag: String?` | GitHub release metadata contains no matching JCEF bundle for the host system. | Specify an explicit valid `releaseTag` in `KromiumConfig`. |
| `DownloadFailed` | `url: String`, `cause: Throwable?` | HTTP request to download the engine bundle failed (e.g. offline, timeout, HTTP 404/500). | Prompt user to check their internet connection and retry. |

---

### Archive & Security Errors

| Exception | Parameters | Trigger Condition | Recommended Recovery |
|---|---|---|---|
| `ChecksumMismatch` | `expected: String`, `actual: String` | SHA-256 hash of downloaded `.tar.gz` does not match the published `.checksum` file. | The archive is automatically deleted. Retry the download. |
| `ExtractionFailed` | `archivePath: String`, `cause: Throwable?` | Apache Commons Compress encountered corrupt data while extracting the tarball. | Clear installation and re-download. |
| `MaliciousArchiveEntry` | `entryName: String` | A Zip-Slip directory traversal path (e.g. `../../bin/sh`) was detected in the archive. | Critical security alert: abort immediately; do not run untrusted bundles. |

---

### Execution & Runtime Errors

| Exception | Parameters | Trigger Condition | Recommended Recovery |
|---|---|---|---|
| `BootstrapFailed` | `cause: Throwable?` | CEF native bootstrap failed during `CefApp.startup()` or dynamic library loading. | Ensure necessary OS dependencies (e.g. VC++ Redistributable on Windows, standard C runtime on Linux) are installed. |
| `InstallationCorrupted`| `directory: String` | The local JCEF installation files are missing or incomplete. | Call `KromiumEngine.clearInstallation()` and re-initialize. |
| `JsEvaluationTimeout` | `timeoutMs: Long` | JavaScript execution did not return within the specified timeout. | Check for infinite loops or hanging async operations in the injected script. |

---

## Exhaustive Pattern Matching Example

```kotlin
import dev.daviante.kromium.domain.exception.KromiumException
import dev.daviante.kromium.presentation.browser.Kromium
import dev.daviante.kromium.data.engine.KromiumEngine

suspend fun safeEngineStartup() {
    try {
        Kromium.initialize()
    } catch (e: KromiumException) {
        when (e) {
            is KromiumException.DownloadFailed -> {
                println("Network error: Could not download engine. Check internet connection.")
            }
            is KromiumException.ChecksumMismatch -> {
                println("Security warning: Checksum mismatch. Download was discarded.")
            }
            is KromiumException.MaliciousArchiveEntry -> {
                System.err.println("Security alert! Malicious archive detected: ${e.entryName}")
            }
            is KromiumException.UnsupportedPlatform -> {
                println("Platform not supported: OS=${e.os}, Arch=${e.arch}")
            }
            is KromiumException.InstallationCorrupted -> {
                println("Corrupted engine bundle. Wiping directory and reinstalling...")
                KromiumEngine.clearInstallation()
            }
            is KromiumException.BootstrapFailed -> {
                println("Native CEF bootstrap failed: ${e.cause?.message}")
            }
            is KromiumException.InvalidConfig -> {
                println("Configuration error: ${e.detail}")
            }
            else -> {
                println("Unhandled Kromium error: ${e.message}")
            }
        }
    }
}
```

---

## Logging Subsystem (`KromiumLogger`)

All internal logging (archive extraction, download percentages, CEF bootstrap diagnostics, warnings) flows through the `KromiumLogger` interface:

```kotlin
interface KromiumLogger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}
```

### Default Logger (`JulKromiumLogger`)
By default, Kromium outputs through standard Java Unified Logging (`java.util.logging.Logger`).

### Silent / Testing Mode (`NoOpKromiumLogger`)
To completely silence all Kromium logs (ideal for automated unit tests or clean CLI tools):

```kotlin
import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.logging.NoOpKromiumLogger

KromiumLogger.instance = NoOpKromiumLogger
```

### Integrating with SLF4J, Logback, or Kermit
You can redirect Kromium logs to any logging framework by implementing `KromiumLogger`:

```kotlin
import dev.daviante.kromium.core.logging.KromiumLogger
import org.slf4j.LoggerFactory

class Slf4jKromiumLogger : KromiumLogger {
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

// In your application startup:
KromiumLogger.instance = Slf4jKromiumLogger()
```

---

## CEF Native Log Severity

In addition to Kotlin-side logging, you can configure the verbosity of Chromium's internal native logging engine via `KromiumConfig.logSeverity`:

```kotlin
import org.cef.CefSettings

Kromium.initialize {
    // Options:
    // LOGSEVERITY_DEFAULT, LOGSEVERITY_VERBOSE, LOGSEVERITY_INFO,
    // LOGSEVERITY_WARNING, LOGSEVERITY_ERROR, LOGSEVERITY_DISABLE
    logSeverity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
}
```
