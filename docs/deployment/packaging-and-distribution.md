# Packaging & Distribution

[Documentation Hub](../README.md) &bull; **Deployment** &bull; Packaging & Distribution

---

## 📦 Packaging Desktop Applications

When distributing your Compose Multiplatform or Kotlin JVM desktop application to end users, you have two primary distribution strategies:

### Strategy A: On-Demand Runtime Installation (Recommended)
* **Installer Size**: Tiny (~15MB to 30MB initial download).
* **Mechanism**: Your application ships without native Chromium binaries. When the user first launches the app, Kromium downloads the appropriate JCEF engine for their specific OS architecture and caches it in `~/.kromium/jcef`.
* **Advantage**: Single installer binary; zero bloat for unused OS platforms.

### Strategy B: Pre-Bundled Standalone Distribution
* **Installer Size**: Larger (~150MB to 220MB).
* **Mechanism**: You pre-extract the JCEF bundle into your application distribution folder during build time and point `installDir` to the local bundled directory:

```kotlin
Kromium.initialize {
    // Point to bundled JCEF folder inside your app installation
    installDir = File(System.getProperty("compose.application.resources.dir"), "jcef")
}
```

---

## 🚀 Packaging Your Application with Compose Desktop

> [!NOTE]
> **Library vs. Application**:  
> Kromium is a library dependency (`dev.daviante:kromium-compose`). The configuration below applies to **your own application's** `build.gradle.kts` (the application that embeds Kromium), replacing `YourAppName` and `com.yourcompany.yourapp` with your actual project details.

In your consumer desktop application's `build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        mainClass = "com.yourcompany.yourapp.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "YourAppName"       // User-facing application name
            packageVersion = "1.0.0"

            windows {
                menuGroup = "YourCompany"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }

            macOS {
                bundleID = "com.yourcompany.yourapp"
                iconFile.set(project.file("src/main/resources/icon.icns"))
            }

            linux {
                shortcut = true
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}
```

### Reference: How `kromium-sample-compose` is Packaged
For a real-world reference, see the included [`kromium-sample-compose/build.gradle.kts`](../../kromium-sample-compose/build.gradle.kts):

```kotlin
compose.desktop {
    application {
        mainClass = "dev.daviante.kromium.demo.MainKt"
        nativeDistributions {
            packageName = "Kromium Demo"
            packageVersion = "1.0.0"
            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}
```

### Build Commands
```bash
# Windows MSI Installer
./gradlew packageMsi

# macOS DMG Installer
./gradlew packageDmg

# Linux Debian Package
./gradlew packageDeb
```

---

## 🛡️ ProGuard / R8 Obfuscation Rules

If you obfuscate or minify your desktop distribution using ProGuard or R8, you must preserve JCEF native classes and JNI entry points:

```proguard
# Preserve Kromium Public API
-keep class dev.daviante.kromium.** { *; }

# Preserve JCEF Native Interface Classes
-keep class org.cef.** { *; }
-keepclassmembers class org.cef.** {
    native <methods>;
}

# Preserve AWT / JAWT Native Peers
-keep class sun.awt.** { *; }
-keep class java.desktop.** { *; }

# Suppress Kotlin reflection warnings
-dontwarn dev.daviante.kromium.**
-dontwarn org.cef.**
```
