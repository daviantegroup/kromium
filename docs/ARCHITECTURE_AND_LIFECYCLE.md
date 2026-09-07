# Architecture & Engine Lifecycle

[Kromium Documentation](README.md) &bull; [Interactive Web Portal](https://kromium.daviante.dev/#/docs/overview)

This document provides a comprehensive guide to Kromium's internal architecture, platform resolution, automated binary provisioning, engine bootstrapping, reactive lifecycle states, and configuration options.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Module Structure](#module-structure)
3. [Platform Detection](#platform-detection)
4. [Engine Provisioning (Download & Extraction)](#engine-provisioning-download--extraction)
   - [Automated Release Resolution](#automated-release-resolution)
   - [Checksum Verification](#checksum-verification)
   - [Zip-Slip Safe Extraction](#zip-slip-safe-extraction)
   - [macOS Quarantine Stripping](#macos-quarantine-stripping)
5. [Engine Bootstrap Process](#engine-bootstrap-process)
6. [Lifecycle State Machine (`KromiumState`)](#lifecycle-state-machine-kromiumstate)
7. [Engine Configuration (`KromiumConfig`)](#engine-configuration-kromiumconfig)
8. [Engine Information & Cache Management](#engine-information--cache-management)
9. [Disposal & Shutdown Hooks](#disposal--shutdown-hooks)

---

## Architecture Overview

Kromium bridges Kotlin and Compose Multiplatform Desktop with the native **Chromium Embedded Framework (CEF)** via the **Java Chromium Embedded Framework (JCEF)** runtime.

```
┌─────────────────────────────────────────────────────────────────┐
│                       Your Application                          │
│        (Compose Multiplatform Desktop / Kotlin JVM)             │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
       ┌──────────────────┐            ┌──────────────────┐
       │  kromium-compose │            │   kromium-core   │
       │  (KromiumView)   │            │ (KromiumClient,  │
       │(KromiumViewState)│            │  KromiumBrowser) │
       └─────────┬────────┘            └─────────┬────────┘
                 │                               │
                 └───────────────┬───────────────┘
                                 ▼
                     ┌───────────────────────┐
                     │   Kromium Singleton   │
                     │  (StateFlow lifecycle,│
                     │   Mutex-safe startup) │
                     └───────────┬───────────┘
                                 ▼
                     ┌───────────────────────┐
                     │    CefBootstrapper    │
                     │  (Native lib loading, │
                     │   JAWT, SwiftShader)  │
                     └───────────┬───────────┘
                                 ▼
                     ┌───────────────────────┐
                     │   Pure JCEF Runtime   │
                     │      (jcef.jar)       │
                     └───────────┬───────────┘
                                 ▼
         ┌───────────────────────────────────────────────┐
         │          Platform Native Binaries             │
         │  Windows (jcef.dll, libcef.dll)               │
         │  macOS (Chromium Embedded Framework.framework)│
         │  Linux (libcef.so, libjcef.so)                │
         └───────────────────────────────────────────────┘
```

Kromium is compiled against the pure standard `jcef.jar` API without vendor-specific proprietary extensions, ensuring maximum binary portability and compatibility.

---

## Module Structure

The project is divided into two modules:

| Module | Artifact ID | Description |
|---|---|---|
| **Core** | `dev.daviante:kromium-core` | Pure Kotlin/JVM engine. Handles runtime download, checksum verification, archive extraction, native bootstrap, cookie management, network interception, and low-level CEF wrapping. |
| **Compose** | `dev.daviante:kromium-compose` | Declarative UI layer for Compose Multiplatform Desktop. Provides `KromiumView`, `rememberKromiumState`, and bidirectional state synchronization. |

---

## Platform Detection

Before downloading or bootstrapping binaries, `PlatformDetector` resolves the host operating system and CPU architecture via JVM system properties (`os.name` and `os.arch`).

### Supported Operating Systems (`OperatingSystem`)
- **Windows**: Identified by `"win"` or `"windows"`. Native libraries: `jcef.dll`, `libcef.dll`. Executable helper: `jcef_helper.exe`.
- **macOS**: Identified by `"mac"`, `"darwin"`, or `"osx"`. Dynamically detects `Chromium Embedded Framework.framework` and `jcef Helper.app` within `Frameworks/cef_server.app/Contents/Frameworks/` (JetBrains Runtime 25 layout) while falling back to legacy `Frameworks/`. Dynamic library extension: `.dylib`.
- **Linux**: Identified by `"linux"`. Native libraries: `libcef.so`, `libjcef.so`. Executable helper: `jcef_helper`.

### Supported Architectures (`Architecture`)
- **x64**: Identified by `"x64"`, `"amd64"`, `"x86_64"`.
- **Arm64**: Identified by `"arm64"`, `"aarch64"`.

```kotlin
import dev.daviante.kromium.core.util.PlatformDetector

val platform = PlatformDetector.current()
println("OS: ${platform.os.name}, Arch: ${platform.arch.name}")
```

If an unsupported operating system or architecture is detected, `PlatformDetector` throws an `IllegalStateException`, which is converted by `Kromium` into `KromiumException.UnsupportedPlatform`.

---

## Engine Provisioning (Download & Extraction)

If JCEF binaries are not present in the designated install directory, Kromium automatically fetches and unpacks the official matching JetBrains Runtime (JBR) bundle.

### 1. Automated Release Resolution
`EngineDownloader` queries GitHub releases from `JetBrains/JetBrainsRuntime`:
- Endpoint: `https://api.github.com/repos/JetBrains/JetBrainsRuntime/releases/latest` (or a specific tag configured in `KromiumConfig.releaseTag`).
- URL resolution scans release markdown links and asset manifests for JCEF tarballs matching the current OS and architecture (preferring non-SDK runtime bundles and `.tar.gz` archives).
- Gracefully detects GitHub API rate limits (HTTP 403) and surfaces informative diagnostic messages.

### 2. Checksum Verification
When a matching package URL is discovered, `EngineDownloader` looks for a corresponding `.checksum` file.
- Downloads the SHA-256 hash file.
- Calculates the SHA-256 digest of the downloaded archive using a 256 KB memory buffer.
- Compares expected vs actual hashes. If they do not match, the downloaded archive is immediately deleted and `KromiumException.ChecksumMismatch` is thrown.

### 3. Zip-Slip Safe & Symlink Extraction
`EngineExtractor` unpacks `.tar.gz` bundles using Apache Commons Compress:
- **Directory Traversal Protection (Zip-Slip)**: Every entry's canonical path is validated against the destination directory. If an entry attempts to escape the root directory (e.g. `../../evil.sh`), extraction halts immediately with `KromiumException.MaliciousArchiveEntry`.
- **Tar Symbolic Links**: Unpacks symbolic links (`isSymbolicLink`) with full path validation to ensure target destinations cannot escape the sandbox. Creates native links via `Files.createSymbolicLink` with automated copy fallback on Windows.
- **Recursive Executable Permission Enforcement**: Archive file entry modes are inspected, and post-extraction permission enforcement (`ensureExecutablePermissions`) marks all helper executables (`jcef helper`, `jcef_helper`, `cef_server`), shared libraries (`.so`, `.dylib`), and shell scripts executable.
- **Subdirectory Flattening**: If the tarball contents are wrapped inside a single top-level folder, `EngineExtractor` automatically moves all children to the destination root using atomic rename fallback (`StandardCopyOption.REPLACE_EXISTING`) and cleans up the wrapper folder.

### 4. macOS Quarantine Stripping
On macOS, Gatekeeper applies the `com.apple.quarantine` extended attribute to downloaded archives and extracted bundles, preventing dynamic libraries from loading. `FileUtils.removeMacQuarantine()` executes:
```bash
xattr -d -r com.apple.quarantine <installDir>
```
This is executed automatically after extraction and before JCEF native bootstrap.

---

## Engine Bootstrap Process

Bootstrapping is performed by `CefBootstrapper.bootstrap()`:

1. **Runtime Module Opening (`JvmModuleOpener`)**: On Java 17 and 21+, dynamically opens `java.desktop/sun.awt` internal packages via Unsafe and Module reflection so AWT integration functions out of the box without requiring manual `--add-opens` flags.
2. **Thread-Safe Preinitialization**: Sets `System.setProperty("jcef_app_preinit_any", "true")` so native pre-initialization executes safely without blocking on the AWT Event Dispatch Thread (EDT).
3. **Local In-Process Enforcement**: Sets `CefApp.setIsRemoteEnabled(false)` to ensure local in-process CEF execution instead of attempting to connect to a remote `cef_server.exe` process.
4. **macOS Dynamic Framework Binding**: Resolves framework and helper bundles dynamically via `OperatingSystem.MacOS.resolveCefFrameworksDir(installDir)`. Passes `--framework-dir-path`, `--main-bundle-path`, and `--browser-subprocess-path` cleanly via launch arguments without mutating the JVM's `java.home`.
5. **Preloading JAWT**: Resolves and preloads Java AWT native bridge library (`jawt`) from the active JVM installation (`${java.home}/lib` and `${java.home}/bin`) across Windows, macOS, and Linux.
6. **Preloading GPU / Rendering Libraries**: On Windows, preloads graphics libraries (`chrome_elf.dll`, `d3dcompiler_47.dll`, `libEGL.dll`, `libGLESv2.dll`, `vk_swiftshader.dll`, `vulkan-1.dll`). On other platforms, preloads `EGL`, `GLESv2`, and `vk_swiftshader` unless `--disable-gpu` is configured.
7. **Dynamic Library Loader**: Registers a native loader callback via `SystemBootstrap.setLoader` that resolves `.dll`, `.dylib`, or `.so` libraries directly from the installation path.
8. **CefSettings Configuration**: Resolves and binds `locales_dir_path`, `resources_dir_path`, and `browser_subprocess_path` (`jcef_helper.exe` or OS equivalent), along with sandboxing settings.
9. **CEF Process Startup & Synchronization**: Invokes `CefApp.startup(args)`, loads `libcef`, instantiates `CefApp.getInstance(launchArgs, cefSettings, installDir)`, and synchronously latches until native CEF reaches `CefAppState.INITIALIZED`.
10. **Deferred Installation Marking**: In `Kromium.initialize()`, `EngineRegistry.markInstalled(installDir)` is executed strictly after bootstrap completes successfully. If any error occurs during extraction or bootstrap, `install.lock` and temporary files are cleaned up immediately.

---

## Lifecycle State Machine (`KromiumState`)

The engine lifecycle is exposed reactively as a `StateFlow<KromiumState>` on `Kromium.state`.

```
             ┌────────────────────────┐
             │   KromiumState.Idle    │
             └───────────┬────────────┘
                         │ Kromium.initialize()
                         ▼
             ┌────────────────────────┐
             │  KromiumState.Locating │
             └───────────┬────────────┘
                         │ If binaries not cached
                         ▼
       ┌────────────────────────────────────┐
       │ KromiumState.Downloading(progress) │◄── (Emits DownloadProgress:
       └─────────────────┬──────────────────┘     bytesRead, totalBytes, fraction)
                         │ Download complete
                         ▼
             ┌────────────────────────┐
             │ KromiumState.Extracting│
             └───────────┬────────────┘
                         │ Archive unpacked
                         ▼
            ┌──────────────────────────┐
            │ KromiumState.Initializing│
            └────────────┬─────────────┘
                         │ CefBootstrapper ready
                         ▼
             ┌────────────────────────┐
             │   KromiumState.Ready   │
             └───────────┬────────────┘
                         │ Kromium.dispose()
                         ▼
             ┌────────────────────────┐
             │  KromiumState.Disposed │
             └────────────────────────┘
```

If an error occurs at any point during locating, downloading, extracting, or bootstrapping, the state transitions to `KromiumState.Error(val cause: Throwable)`.

### Monitoring Progress in Compose

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import dev.daviante.kromium.presentation.browser.Kromium
import dev.daviante.kromium.domain.model.KromiumState

@Composable
fun EngineStatusOverlay() {
    val state by Kromium.state.collectAsState()

    when (val s = state) {
        is KromiumState.Idle -> Text("Idle")
        is KromiumState.Locating -> Text("Locating Chromium engine...")
        is KromiumState.Downloading -> {
            Text("Downloading engine: ${s.progress.percentage}% (${s.progress.bytesRead / 1024 / 1024} MB)")
            LinearProgressIndicator(progress = { s.progress.fraction })
        }
        is KromiumState.Extracting -> Text("Extracting engine binaries...")
        is KromiumState.Initializing -> Text("Bootstrapping Chromium runtime...")
        is KromiumState.Ready -> Text("Engine ready!")
        is KromiumState.Error -> Text("Failed to start engine: ${s.cause.message}")
        is KromiumState.Disposed -> Text("Engine disposed")
    }
}
```

---

## Engine Configuration (`KromiumConfig`)

Configuration is passed via a lambda to `Kromium.initialize`:

```kotlin
Kromium.initialize {
    // 1. Directory where JCEF native binaries are stored
    installDir = File(System.getProperty("user.home"), ".my-app/jcef")

    // 2. Cache path (cookies, localStorage, indexedDB). Set null for in-memory session
    cachePath = File(System.getProperty("user.home"), ".my-app/cache").absolutePath

    // 3. Global User-Agent for all browsers
    userAgent = "MyAppDesktop/1.0 (KHTML, like Gecko)"

    // 4. Windowless (off-screen) rendering. Defaults to false for standard hardware-accelerated SwingPanel windowed integration
    windowlessRendering = false

    // 5. Remote debugging port for Chrome DevTools (0 = disabled)
    remoteDebuggingPort = 9222

    // 6. Specific JetBrains Runtime release tag (null = latest)
    releaseTag = null

    // 7. Native CEF log severity level
    logSeverity = org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING

    // 8. Chromium sandbox for renderer and GPU sub-processes
    sandboxEnabled = true

    // 9. Network Proxy Configuration
    proxy = KromiumProxy.System

    // 10. Custom command-line arguments passed to Chromium
    addArgs("--disable-speech-api", "--disable-notifications")
}
```

### Configuration Properties Reference

| Property | Type | Default | Description |
|---|---|---|---|
| `installDir` | `File` | OS default folder (`EngineRegistry.defaultInstallDir()`) | Directory where JCEF binaries, helper apps, and frameworks reside. |
| `cachePath` | `String?` | `null` | Path for disk cache. When `null`, browser runs in incognito/in-memory mode. |
| `userAgent` | `String?` | `null` | Global User-Agent string. |
| `windowlessRendering` | `Boolean` | `false` | Off-screen rendering toggle. Defaults to `false` for standard, hardware-accelerated Compose Desktop `SwingPanel` windowed integration (`CefBrowserWr`). Set `true` if custom off-screen software frame consumption is needed. |
| `remoteDebuggingPort` | `Int` | `0` | Port for Chrome DevTools protocol debugging (0 = disabled, max = 65535). |
| `releaseTag` | `String?` | `null` | Pin to a specific JBR GitHub release tag (e.g. `"jbr-release-17.0.10b1087.23"`). |
| `logSeverity` | `CefSettings.LogSeverity` | `LOGSEVERITY_DEFAULT` | Native logging verbosity. |
| `sandboxEnabled` | `Boolean` | `true` | When `false`, adds `--no-sandbox` to process arguments and sets `settings.no_sandbox = true`. |
| `proxy` | `KromiumProxy` | `KromiumProxy.System` | Proxy strategy (`System`, `Direct`, `Http`, or `Socks5`). |
| `commandLineArgs` | `MutableList<String>` | Security & render defaults | Arguments passed to CEF startup. |

### Default Command-Line Arguments
By default, Kromium includes:
- `--disable-gpu-compositing`
- `--enable-begin-frame-scheduling`
- `--disable-extensions` (disables untrusted browser extensions)
- `--disable-plugins` (disables NPAPI/PPAPI plugins)

---

## Engine Information & Cache Management

Use `KromiumEngine` to query installation status or clear downloaded binaries:

```kotlin
import dev.daviante.kromium.data.engine.KromiumEngine

// Check if engine is installed
val info = KromiumEngine.getInfo()
println("Installed: ${info.isInstalled}")
println("Path: ${info.installDir.absolutePath}")
println("JCEF Version: ${info.jcefVersion}")
println("CEF Version: ${info.cefVersion}")
println("Chromium Version: ${info.chromiumVersion}")

// Clear engine installation (forces re-download on next start)
KromiumEngine.clearInstallation()
```

---

## Disposal & Shutdown Hooks

Kromium automatically registers a JVM shutdown hook (`Runtime.getRuntime().addShutdownHook`) during `initialize()` to cleanly terminate the native CEF process when the JVM exits.

To manually trigger engine disposal:

```kotlin
Kromium.dispose()
```

> [!NOTE]
> Once `Kromium.dispose()` is called, the state transitions to `KromiumState.Disposed`. Attempting to call `newClient()` or `awaitClient()` will throw `KromiumException.Disposed`.

---

[← Return to Documentation Index](README.md) &bull; [Visit Online Documentation](https://kromium.daviante.dev/#/docs/overview)
