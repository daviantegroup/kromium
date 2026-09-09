# Installation & Setup Guide

This guide walks you through adding **Kromium** to your desktop project, configuring dependency repositories, satisfying native operating system prerequisites, and setting up the engine runtime.

---

## 📦 Dependency Coordinates

Kromium artifacts are published to **Maven Central** under the group ID `dev.daviante`.

| Artifact | Purpose | Best For |
|:---|:---|:---|
| **`dev.daviante:kromium-compose:3.0.150-b11`** | `@Composable KromiumView`, reactive `KromiumViewState`, and Compose Multiplatform desktop integration. | Jetpack / JetBrains Compose Desktop applications. |
| **`dev.daviante:kromium-core:3.0.150-b11`** | Pure JVM engine, `KromiumClient`, `KromiumBrowser`, headless automation, and pure Java APIs (`CompletableFuture`, SAM callbacks). | Pure Java, Swing, Standard AWT, Eclipse SWT, JavaFX, CLI, and headless servers. |

---

## 🛠️ Build Tool Configuration

### 1. Gradle (Kotlin DSL) — `build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm") version "2.1.10"
    // Optional: Compose plugin if using Compose Desktop:
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // For Compose Desktop:
    implementation("dev.daviante:kromium-compose:3.0.150-b11")

    // Or for Universal Java Desktop (Swing, AWT, SWT, JavaFX, Headless):
    implementation("dev.daviante:kromium-core:3.0.150-b11")
}

kotlin {
    jvmToolchain(21) // Java 17 LTS minimum, Java 21 LTS recommended
}
```

### 2. Gradle (Groovy DSL) — `build.gradle`

```groovy
plugins {
    id 'org.jetbrains.kotlin.jvm' version '2.1.10'
    id 'org.jetbrains.compose' version '1.7.3'
}

repositories {
    mavenCentral()
    google()
    maven { url 'https://maven.pkg.jetbrains.space/public/p/compose/dev' }
}

dependencies {
    implementation 'dev.daviante:kromium-compose:3.0.150-b11'
    // or: implementation 'dev.daviante:kromium-core:3.0.150-b11'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

### 3. Apache Maven — `pom.xml` (Pure Java Applications)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.mycompany</groupId>
    <artifactId>my-desktop-browser</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <kromium.version>3.0.150-b11</kromium.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>dev.daviante</groupId>
            <artifactId>kromium-core</artifactId>
            <version>${kromium.version}</version>
        </dependency>
    </dependencies>
</project>
```

### 4. Toolkit UI Dependencies (SWT & JavaFX)

When pairing `kromium-core` with Eclipse SWT or JavaFX, add the corresponding framework libraries:

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.daviante:kromium-core:3.0.150-b11")

    // Eclipse SWT (select platform artifact or dynamic classifier):
    implementation("org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:3.128.0")

    // JavaFX (requires javafx-controls and javafx-swing for SwingNode):
    implementation("org.openjfx:javafx-controls:21.0.6")
    implementation("org.openjfx:javafx-swing:21.0.6")
}
```

---

## 💻 Operating System Prerequisites

Kromium targets 64-bit desktop operating systems. The native Chromium binaries are resolved automatically by Kromium's bootstrapper, but certain operating system runtime libraries must be present on the user's system:

### 🪟 Windows (x64)
* **Supported OS**: Windows 10 (Build 1809+), Windows 11, Windows Server 2019+
* **Prerequisites**: [Microsoft Visual C++ 2015–2022 Redistributable (x64)](https://aka.ms/vs/17/release/vc_redist.x64.exe).
  * *Note: Most Windows installations already have this pre-installed via other applications.*

### 🍎 macOS (Apple Silicon & Intel x64)
* **Supported OS**: macOS 11.0 (Big Sur), 12 (Monterey), 13 (Ventura), 14 (Sonoma), 15 (Sequoia)+
* **Architectures**: Universal support (native ARM64 on Apple Silicon M1/M2/M3/M4; native x86_64 on Intel).
* **Prerequisites**: None. Dynamic framework symlinking (`ensureMacFrameworkLinks`) is handled automatically at engine initialization.

### 🐧 Linux (x64 & ARM64)
* **Supported Distributions**: Ubuntu 20.04+, Debian 11+, Fedora 36+, Arch Linux, openSUSE.
* **Prerequisites**: Standard X11/GTK and multimedia runtime libraries.
  ```bash
  # Ubuntu / Debian
  sudo apt-get update && sudo apt-get install -y \
      libnss3 libasound2 libatk-bridge2.0-0 libdrm2 libgbm1 libxkbcommon0 libxcomposite1 libxdamage1 libxrandr2
  
  # Fedora / RHEL
  sudo dnf install -y \
      nss alsa-lib at-spi2-atk libdrm mesa-libgbm libxkbcommon libXcomposite libXdamage libXrandr
  ```

---

## ☕ Zero JVM Configuration (`--add-opens`)

Traditional JCEF setups require developers to configure complex JVM command-line flags (such as `--add-opens java.desktop/sun.awt=ALL-UNNAMED`) to prevent `InaccessibleObjectException` when accessing native AWT peers.

**Kromium eliminates this completely.**

Using Kromium's built-in `JvmModuleOpener`, internal JDK desktop modules (`sun.awt`, `sun.awt.X11`, `sun.lwawt.macosx`, `sun.awt.windows`) are dynamically opened in-memory via Unsafe/reflection during `Kromium.initialize()`.

> [!NOTE]
> You do **not** need to declare `--add-opens` in `build.gradle.kts`, Maven POMs, or launcher scripts on Java 17, 21, or 23+.

---

## 🚀 Engine Bootstrapping & Installation Lifecycle

When `Kromium.initialize()` is called for the first time on a machine:

```
┌─────────────────────────────────────────────────────────────┐
│                   Kromium.initialize()                      │
└──────────────────────────────┬──────────────────────────────┘
                               │
                Is local JCEF cached & valid?
                               │
               ┌───────────────┴───────────────┐
              YES                              NO
               │                               │
               │                   Download platform runtime
               │                   from JetBrains CDN / Mirror
               │                               │
               │                   Verify SHA-256 Checksum
               │                               │
               │                   Extract to ~/.kromium/cef-...
               │                               │
               └───────────────┬───────────────┘
                               │
                   Load Native Libraries & JNI
                               │
                 Initialize CEF Process Loop
                               │
                     KromiumState.Ready
```

### Cache Storage Locations
By default, Kromium stores verified native binaries in a version-isolated user cache directory:
* **Windows**: `%LOCALAPPDATA%\Kromium\cef-150-...\`
* **macOS**: `~/Library/Caches/dev.daviante.kromium/cef-150-.../`
* **Linux**: `~/.cache/kromium/cef-150-.../`

### Customizing Engine Installation Directory
For enterprise deployments, kiosks, or portable USB apps, you can customize the installation directory:

```kotlin
val config = KromiumConfig.builder()
    .installDir(File("/opt/mycompany/kromium-runtime"))
    .autoDownload(true)
    .build()

Kromium.initialize(config)
```

### Bundling Offline / Air-Gapped Runtimes
To distribute Kromium in restricted, internet-free enterprise environments:
1. Pre-download the JCEF runtime for your target operating system.
2. Bundle the files into your application installer.
3. Point `installDir` to the bundled folder and set `autoDownload(false)`:
   ```kotlin
   val config = KromiumConfig.builder()
       .installDir(File(System.getProperty("app.dir"), "runtime/cef"))
       .autoDownload(false)
       .build()
   ```

---

## ⏭️ Next Steps

* **[Compose Desktop Quickstart](quickstart-compose.md)**: Build your first browser UI in 5 minutes using Kotlin & Compose Multiplatform.
* **[Pure Java & Swing Quickstart](quickstart-jvm.md)**: Build a desktop browser using standard Java, Swing, and FlatLaf.
