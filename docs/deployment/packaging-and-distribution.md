# Packaging & Native Distribution

Distributing desktop applications with an embedded Chromium engine requires packaging native binaries (`.dylib`, `.dll`, `.so`) and helper processes alongside the Java runtime.

This guide details best practices for packaging Kromium apps into native desktop installers: **DMG / PKG (macOS)**, **MSI / EXE (Windows)**, and **DEB / RPM (Linux)**.

---

## 📦 Strategy: Pre-bundled vs. Runtime Download

Kromium supports two deployment models:

| Strategy | Advantages | Trade-offs | Best For |
|:---|:---|:---|:---|
| **1. Runtime Auto-Download (Default)** | Installer download is small (~30-50MB). CEF engine downloads on first app launch. | Requires initial internet connection on first start. | Open-source apps, quick demos, consumer tools. |
| **2. Pre-bundled Offline Binaries** | **Zero network requests** on startup. Immediate offline launch. | Installer bundle is larger (~120-150MB per OS). | **Enterprise, air-gapped, healthcare, and POS applications.** |

---

## 🏢 Enterprise Pre-Bundling (`autoDownload = false`)

To create an air-gapped offline installer:

### 1. Configure Engine to Use Pre-Bundled Directory

Point `installDir` to a relative subfolder inside your packaged application:

```kotlin
val config = KromiumConfig().apply {
    // Locate native binaries relative to the app installation directory:
    installDir = File(System.getProperty("app.dir", "."), "jcef")
    autoDownload = false // Prohibit network calls
}
KromiumEngine.getInstance().initialize(config)
```

```java
KromiumConfig config = KromiumConfig.builder()
    .setInstallDir(new File(System.getProperty("app.dir", "."), "jcef"))
    .setAutoDownload(false)
    .build();
KromiumEngine.getInstance().initialize(config);
```

---

## 🛠️ Packaging with Compose Gradle Plugin

If using Compose Multiplatform, use the built-in `compose.desktop.nativeDistributions` DSL:

```kotlin
// build.gradle.kts
compose.desktop {
    application {
        mainClass = "com.example.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "EnterpriseBrowser"
            packageVersion = "2.1.0"
            vendor = "Daviante Group"

            // JVM Module openings required by native AWT/CEF bridges:
            jvmArgs(
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED"
            )

            macOS {
                bundleID = "dev.daviante.enterprisebrowser"
                iconFile.set(project.file("src/main/resources/icons/mac.icns"))
                entitlementsFile.set(project.file("entitlements.plist"))
            }

            windows {
                iconFile.set(project.file("src/main/resources/icons/win.ico"))
                menuGroup = "Enterprise"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            linux {
                iconFile.set(project.file("src/main/resources/icons/linux.png"))
            }
        }
    }
}
```

### Build Commands

```bash
# Package macOS DMG:
./gradlew packageDmg

# Package Windows MSI:
./gradlew packageMsi

# Package Linux DEB:
./gradlew packageDeb
```

---

## 🚀 Packaging with Conveyor

[Conveyor](https://conveyor.hydraulic.dev/) is a popular modern cross-platform packaging tool for desktop JVM apps. It can generate signed, auto-updating native installers for macOS, Windows, and Linux from any host OS:

```hocon
# conveyor.conf
include required("/stdlib/jvm/enhancements/client/v1.conf")

app {
  display-name = "Enterprise Browser"
  fsname = "enterprise-browser"
  version = 2.1.0
  vendor = "Daviante Group"

  jvm {
    gui.main-class = "com.example.MainKt"
    options += [
      "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED"
    ]
  }

  mac {
    info-plist {
      NSCameraUsageDescription = "Used for WebRTC video conferencing"
      NSMicrophoneUsageDescription = "Used for WebRTC audio calls"
    }
  }
}
```
