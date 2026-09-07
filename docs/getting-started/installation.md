# Installation & Requirements

[Documentation Hub](../README.md) &bull; **Getting Started** &bull; Installation

---

## 📦 Artifact Coordinates

Kromium is published to Maven Central under the `dev.daviante` namespace.

| Module | Description | Maven Central |
|---|---|---|
| **`dev.daviante:kromium-compose`** | Compose Multiplatform desktop UI bindings, `@Composable KromiumView`, and `KromiumViewState`. | [![Maven Central](https://img.shields.io/badge/Maven_Central-v2.0.150--b11-107c41?style=flat-square&logo=apachemaven)](https://central.sonatype.com/artifact/dev.daviante/kromium-compose) |
| **`dev.daviante:kromium-core`** | Core engine bootstrap, JCEF lifecycle manager, `KromiumClient`, and headless automation. | [![Maven Central](https://img.shields.io/badge/Maven_Central-v2.0.150--b11-107c41?style=flat-square&logo=apachemaven)](https://central.sonatype.com/artifact/dev.daviante/kromium-core) |

---

## 🛠️ Gradle Setup

### Compose Multiplatform Desktop

In your desktop application's `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // Kromium Compose Multiplatform bindings (includes kromium-core transitively)
                implementation("dev.daviante:kromium-compose:2.0.150-b11")
                
                // Kotlin Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            }
        }
    }
}
```

### Pure Kotlin JVM / Java Swing (Without Compose)

If building a command-line tool, backend service, web scraper, or Swing desktop application:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Core engine without Compose Desktop dependencies
    implementation("dev.daviante:kromium-core:2.0.150-b11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
```

---

## ☕ Runtime Requirements & Java Modules

### Supported JDKs
* **Java 17 LTS** (Minimum)
* **Java 21 LTS** (Recommended)
* **Java 23+**

### Dynamic Java Module Opening
Because JCEF hooks directly into the Java AWT native peer system (`sun.awt`, `java.desktop/sun.awt`), Java 17+ strong encapsulation requires these packages to be opened to unnamed modules.

> [!TIP]
> **Zero Configuration Required in Kromium**:  
> Kromium includes an internal `JvmModuleOpener` that **dynamically opens** the required `java.desktop` packages via reflection at engine startup. You do **not** need to manually add `--add-opens` flags for standard applications.

If running in heavily restricted security manager environments or modular Jigsaw apps, you can explicitly configure your Gradle run task or `application` block:

```kotlin
application {
    applicationDefaultJvmArgs = listOf(
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED", // Windows
        "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",        // macOS
        "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"      // Linux
    )
}
```

---

## 💻 Operating System Prerequisites

### Windows (x64)
* **Windows 10 / 11** or **Windows Server 2019+**
* [Microsoft Visual C++ 2015–2022 Redistributable (x64)](https://aka.ms/vs/17/release/vc_redist.x64.exe) (Installed by default on almost all consumer machines).

### macOS (Apple Silicon ARM64 & Intel x64)
* **macOS 11.0 (Big Sur)** or newer (macOS 12, 13, 14, 15 fully supported).
* Native Apple Silicon (M1/M2/M3/M4) and Intel binaries are resolved automatically by Kromium's platform detector.

<a id="linux-prerequisites"></a>
<a id="linux-x64-arm64"></a>
### Linux (x64 & ARM64)
Ensure standard graphical and audio libraries are installed on the host system:

```bash
# Ubuntu / Debian
sudo apt-get update && sudo apt-get install -y \
    libx11-6 libxcomposite1 libxcursor1 libxdamage1 libxext6 \
    libxfixes3 libxi6 libxrandr2 libxrender1 libxtst6 \
    libnss3 libasound2 libatk1.0-0 libcups2 libdrm2 libgbm1

# Fedora / RHEL
sudo dnf install -y \
    libX11 libXcomposite libXcursor libXdamage libXext \
    libXfixes libXi libXrandr libXrender libXtst \
    nss alsa-lib atk cups-libs libdrm mesa-libgbm
```
