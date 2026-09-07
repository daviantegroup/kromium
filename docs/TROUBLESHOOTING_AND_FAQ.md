# Troubleshooting, Distribution & FAQ

[Kromium Documentation](README.md) &bull; [Interactive Web Portal](https://kromium.daviante.dev/#/docs/troubleshooting)

This guide covers common cross-platform issues, packaging and distribution strategies for production desktop apps, ProGuard rules, and answers to frequently asked questions.

---

## Table of Contents

1. [Common Issues & Solutions](#common-issues--solutions)
   - [Black Screen or Rendering Artifacts](#black-screen-or-rendering-artifacts)
   - [Missing Linux Dependencies](#missing-linux-dependencies)
   - [macOS Gatekeeper Quarantine](#macos-gatekeeper-quarantine)
   - [Windows Missing MSVC Runtime](#windows-missing-msvc-runtime)
   - [UnsatisfiedLinkError (Missing Native Libraries)](#unsatisfiedlinkerror-missing-native-libraries)
   - [Z-Ordering & Airspace Issues in Compose Desktop](#z-ordering--airspace-issues-in-compose-desktop)
2. [Packaging & Distribution](#packaging--distribution)
   - [Packaging with Compose Desktop Gradle Plugin](#packaging-with-compose-desktop-gradle-plugin)
   - [Packaging with Hydraulic Conveyor](#packaging-with-hydraulic-conveyor)
   - [ProGuard & R8 Configuration](#proguard--r8-configuration)
3. [Frequently Asked Questions (FAQ)](#frequently-asked-questions-faq)
   - [How does Kromium compare to JavaFX WebView?](#how-does-kromium-compare-to-javafx-webview)
   - [Does Kromium collect any user analytics or telemetry?](#does-kromium-collect-any-user-analytics-or-telemetry)
   - [How are native CEF binaries updated?](#how-are-native-cef-binaries-updated)
   - [Can I use Kromium in commercial closed-source software?](#can-i-use-kromium-in-commercial-closed-source-software)
   - [How much memory does Kromium consume?](#how-much-memory-does-kromium-consume)

---

## Common Issues & Solutions

### Black Screen or Rendering Artifacts

**Symptom**: The browser surface mounts but remains solid black, or flickers violently during window resizing.

**Root Causes & Mitigations**:
1. **GPU Acceleration Conflicts**:
   Certain integrated GPUs or virtual machine display drivers encounter OpenGL/DirectX context sharing conflicts with Compose Multiplatform's Skiko rendering pipeline.
   
   *Fix*: Force software rendering (SwiftShader) or disable GPU acceleration during initialization:
   ```kotlin
   Kromium.initialize {
       // Disable hardware acceleration fallback
       additionalArguments = listOf(
           "--disable-gpu",
           "--disable-gpu-compositing",
           "--disable-software-rasterizer"
       )
   }
   ```

2. **Windowless vs Windowed Rendering**:
   By default, `windowlessRendering = false` uses hardware-accelerated OS-native window surfaces (`JAWT`). If your app requires alpha transparency or overlays directly across the browser surface, switch to windowless rendering:
   ```kotlin
   Kromium.initialize {
       windowlessRendering = true
   }
   ```

> [!TIP]
> On Windows, ensure high-performance GPU switching does not force an unsupported driver mode. Passing `--enable-features=UseSkiaRenderer` often stabilizes rendering on older Intel HD Graphics.

---

### Missing Linux Dependencies

**Symptom**: `Kromium.initialize()` throws `KromiumException.InitializationFailed` or process exits with error code 127 on Linux distributions.

**Root Cause**: Native Chromium binaries depend on standard X11, audio, and crypto libraries that may not be pre-installed on minimal distributions (Ubuntu Server, Alpine, minimal Debian).

**Fix**: Ensure the host environment has the required packages installed:

```bash
# Ubuntu / Debian
sudo apt-get update && sudo apt-get install -y \
    libnss3 \
    libnspr4 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libasound2 \
    libpango-1.0-0

# Fedora / RHEL
sudo dnf install -y \
    nss \
    nspr \
    atk \
    at-spi2-atk \
    cups-libs \
    libdrm \
    libxkbcommon \
    libXcomposite \
    libXdamage \
    libXfixes \
    libXrandr \
    mesa-libgbm \
    alsa-lib \
    pango
```

---

### macOS Gatekeeper Quarantine

**Symptom**: macOS refuses to launch native binaries or logs `code signature invalid` / `damaged and can't be opened`.

**Root Cause**: When runtime binaries are downloaded from GitHub releases over HTTP/HTTPS, macOS applies the `com.apple.quarantine` extended attribute.

**How Kromium Handles This**:
Kromium automatically executes `xattr -dr com.apple.quarantine <extractDir>` immediately following archive extraction during `EngineExtractor.extract()`.

If you are bundling binaries manually or running in a restricted sandbox:
```bash
# Manually strip the quarantine flag from the Kromium cache:
xattr -dr com.apple.quarantine ~/Library/Caches/dev.daviante.kromium/
```

> [!IMPORTANT]
> When notarizing your production macOS application for distribution outside the Mac App Store, ensure your entitlements file includes `com.apple.security.cs.allow-jit` and `com.apple.security.cs.allow-unsigned-executable-memory` to allow Chromium's V8 JIT compiler to execute.

---

### Windows Missing MSVC Runtime

**Symptom**: `UnsatisfiedLinkError: jcef.dll: Can't find dependent libraries`.

**Root Cause**: Chromium on Windows is compiled against Microsoft Visual C++ 2015–2022. If the user machine does not have the VC++ Redistributable installed, Windows fails to link `jcef.dll`.

**Fix**: Bundle the [Microsoft Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist) installer (`vc_redist.x64.exe`) in your Windows installer, or invoke it silently during installation:

```powershell
vc_redist.x64.exe /install /passive /norestart
```

---

### UnsatisfiedLinkError (Missing Native Libraries)

**Symptom**: `java.lang.UnsatisfiedLinkError: no jcef in java.library.path`.

**Root Cause**: The JVM cannot find `jcef.dll`, `libjcef.so`, or `libjcef.dylib` because `java.library.path` was either overwritten or the native binaries were extracted to an unexpected directory.

**Fix**:
1. Do not overwrite `java.library.path` manually with `-Djava.library.path=...` unless you include Kromium's cache directory.
2. Verify that `Kromium.initialize()` runs **before** any attempt to load JCEF classes.
3. Check `Kromium.cacheDirectory` to ensure write permissions exist:
   ```kotlin
   println("Kromium Cache: ${Kromium.cacheDirectory.absolutePath}")
   ```

---

### Z-Ordering & Airspace Issues in Compose Desktop

**Symptom**: Compose `@Composable` popups, dropdowns, or snackbars render **behind** the `KromiumView` instead of on top.

**Root Cause**: `KromiumView` renders on a heavy-weight OS native window surface (`AWT Canvas` / `SwingPanel`). In standard AWT/Swing mixing, heavy-weight components always draw above light-weight Compose elements within the same Z-plane ("Airspace problem").

**Fixes**:
1. **Windowless Rendering**: Enable `windowlessRendering = true` during `Kromium.initialize()`. This directs Chromium to paint frames into off-screen shared memory buffers that Compose renders as standard light-weight Skia textures.
2. **Dialog Windows**: For modal menus, use Compose `Dialog` or `Popup` with `usePlatformDefaultWidth = false` to create an independent top-level OS window that natively floats above the browser.

---

### Java 17/21+ Module Opening (`java.desktop/sun.awt`)

**Symptom**: `InaccessibleObjectException: Unable to make field accessible` on newer JDK versions.

**How Kromium Handles This**:
Kromium automatically executes `JvmModuleOpener` at startup. Using low-level Unsafe and Module reflection, it opens `java.desktop/sun.awt` to all unnamed modules dynamically without requiring command-line flags.

If your runtime environment prohibits dynamic module mutation via a custom SecurityManager:
```kotlin
// In build.gradle.kts or launcher scripts, add:
application {
    applicationDefaultJvmArgs += listOf(
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED", // On Windows
        "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",       // On macOS
        "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"      // On Linux
    )
}
```

---

### GitHub API Rate Limiting (HTTP 403)

**Symptom**: `KromiumException.DownloadFailed: GitHub API rate limit exceeded (HTTP 403)`.

**Root Cause**: GitHub limits unauthenticated REST API requests to 60 per hour per IP. On shared CI runners or offices with shared public IPs, querying the latest release can trigger rate limits.

**Fixes**:
1. **Pin Release Tag**: Explicitly configure `releaseTag` in `KromiumConfig` so `EngineDownloader` can resolve assets directly from known release tags:
   ```kotlin
   Kromium.initialize {
       releaseTag = "150.0.14-g7c1aa68-chromium-150.0.7871.129-api-1.21-263-b11"
   }
   ```
2. **Pre-populate Cache**: Pre-download the matching JBR bundle during CI setup into the cache directory so engine downloading is skipped entirely.

---

## Packaging & Distribution

### Packaging with Compose Desktop Gradle Plugin

The official Compose Multiplatform Gradle plugin provides automated distribution packaging for Windows (`.msi`, `.exe`), macOS (`.dmg`, `.pkg`), and Linux (`.deb`, `.rpm`).

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

compose.desktop {
    application {
        mainClass = "dev.daviante.kromium.demo.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "KromiumBrowser"
            packageVersion = "1.2.150"

            windows {
                menuGroup = "Kromium"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                iconFile.set(project.file("assets/app-icon.ico"))
            }

            macOS {
                bundleID = "dev.daviante.kromium.browser"
                iconFile.set(project.file("assets/app-icon.icns"))
                entitlementsFile.set(project.file("entitlements.plist"))
            }

            linux {
                iconFile.set(project.file("assets/app-icon.png"))
            }
        }
    }
}
```

Run the distribution task:
```bash
# Package for current host OS:
./gradlew packageDistributionForCurrentOS
```

---

### Packaging with Hydraulic Conveyor

For production cross-compilation (building Windows, macOS, and Linux packages from a single machine without Docker or VM overhead), **[Hydraulic Conveyor](https://conveyor.hydraulic.dev/)** is highly recommended:

```hocon
// conveyor.conf
app {
  display-name = "Kromium Browser"
  fsname = "kromium-browser"
  version = 1.2.150
  rdns-name = dev.daviante.kromium

  jvm {
    gui.main-class = dev.daviante.kromium.demo.MainKt
    options = [
      "-Dfile.encoding=UTF-8"
    ]
  }

  mac {
    entitlements = [
      "com.apple.security.cs.allow-jit",
      "com.apple.security.cs.allow-unsigned-executable-memory"
    ]
  }
}
```

---

### ProGuard & R8 Configuration

If you enable code shrinking or obfuscation (`proguard-rules.pro`), preserve JCEF native bindings and Kromium reflection hooks:

```proguard
# Preserve JCEF native Java bindings and callback interfaces
-keep class org.cef.** { *; }
-keep interface org.cef.** { *; }

# Preserve Kromium public API and data models
-keep class dev.daviante.kromium.** { *; }
-keep interface dev.daviante.kromium.** { *; }

# JNI callback method preservation
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve serialization models for JS Evaluation
-keepclassmembers class * {
    @dev.daviante.kromium.core.** <fields>;
}
```

---

## Frequently Asked Questions (FAQ)

### How does Kromium compare to JavaFX WebView?

| Feature | Kromium (CEF 150) | JavaFX WebView (WebKit) |
|---|---|---|
| **Underlying Engine** | Google Chromium (CEF) | Apple WebKit (Older Fork) |
| **Modern Web Standards** | Latest ECMAScript, WebGL2, WebAssembly, WebRTC | Incomplete WebGL, older JS engines |
| **Rendering Performance** | Up to 120 FPS hardware-accelerated Skia/JAWT | Software rasterization overhead |
| **Compose Integration** | Native `@Composable KromiumView` | Requires `SwingPanel(JFXPanel)` bridging |
| **DevTools** | Full Chrome DevTools window & remote debugging | Basic WebKit inspector |
| **Binary Size** | ~120 MB (Automated JBR download) | Bundled in OpenJFX |

### Does Kromium collect any user analytics or telemetry?

**No.** Kromium has a strict **Zero Telemetry Guarantee**.
- All Chromium default tracking metrics, user metric services (`metrics_service`), crash reporting pings (`crashpad`), and search suggestions are permanently disabled in Kromium's native configuration.
- The library does not phone home, does not log user navigation, and does not inject advertisements.

### How are native CEF binaries updated?

Kromium downloads standard JetBrains Runtime JCEF bundles. When a new version of Kromium is released (e.g. `1.0.151`), it pins the new upstream release tag with updated SHA-256 checksums. Upon updating your `build.gradle.kts` dependency version, Kromium automatically downloads the updated native cache on next startup.

### Can I use Kromium in commercial closed-source software?

**Yes.** Kromium is distributed under the **Apache License, Version 2.0**.
- You may use Kromium in personal, commercial, closed-source, or internal enterprise applications without paying royalties.
- You do not need to open-source your application's source code.

### How much memory does Kromium consume?

Chromium runs a multi-process architecture (Browser Process, GPU Process, Renderer Processes).
- **Baseline Overhead**: A single idle browser instance consumes approximately **90 MB to 150 MB** of RAM.
- **Cache Management**: Kromium isolates disk cache into the configured cache directory (`KromiumConfig.cachePath`). You can wipe this directory programmatically via `Kromium.clearCache()` or configure memory-only cache by leaving `cachePath = null`.

---

[← Return to Documentation Index](README.md) &bull; [Visit Online Documentation](https://kromium.daviante.dev)
