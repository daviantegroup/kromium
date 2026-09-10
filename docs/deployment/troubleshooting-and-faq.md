# Troubleshooting & Frequently Asked Questions

This guide diagnoses common runtime errors, native crashes, missing library issues, and answers frequently asked architecture questions.

---

## 🔧 Common Errors & Solutions

### 1. `InaccessibleObjectException: module java.desktop does not "opens sun.awt"`

**Symptom:**
```
java.lang.reflect.InaccessibleObjectException: Unable to make field private long sun.awt.X11ComponentPeer.window accessible: 
module java.desktop does not "opens sun.awt" to unnamed module
```

**Cause:**
Modern Java (Java 17, 21, 25) strictly encapsulates internal JDK packages (`sun.awt`). The JCEF native bridge requires access to native window pointers.

**Solution:**
Add the `--add-opens` flags to your JVM execution arguments:
```
--add-opens=java.desktop/sun.awt=ALL-UNNAMED
--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED
```

In Gradle (`build.gradle.kts`):
```kotlin
tasks.withType<JavaExec> {
    jvmArgs(
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED"
    )
}
```

---

### 2. Windows: `UnsatisfiedLinkError: jcef.dll: Can't find dependent libraries`

**Symptom:**
Application crashes immediately upon startup on a fresh Windows machine.

**Cause:**
The target Windows installation is missing the **Microsoft Visual C++ 2015-2022 Redistributable**.

**Solution:**
Install the official [Visual C++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist) or bundle `vc_redist.x64.exe` inside your application installer.

---

### 3. Linux: `UnsatisfiedLinkError: libcef.so: cannot open shared object file: No such file or directory`

**Symptom:**
Engine fails to load on Ubuntu/Debian/Fedora.

**Cause:**
Missing system multimedia or X11 shared libraries.

**Solution:**
Install the prerequisite packages:
```bash
sudo apt-get update && sudo apt-get install -y libgtk-3-0 libasound2 libnss3 libxss1 libgbm1
```

---

### 4. Blank White or Black Screen on Older GPUs

**Symptom:**
The browser component mounts but remains blank or black.

**Cause:**
Outdated GPU drivers or hardware acceleration conflicts with virtual machine display adapters.

**Solution:**
Configure software rendering via `KromiumGpuMode`:
```kotlin
val config = KromiumConfig().apply {
    gpuMode = KromiumGpuMode.SOFTWARE
}
```

---

**Solution 2 (Software / WARP Rendering):**
Bypass physical GPU hardware drivers and Direct3D contention entirely using software rasterization:
```kotlin
val config = KromiumConfig().apply {
    gpuMode = KromiumGpuMode.SOFTWARE // Pure CPU SwiftShader pipeline
    // or: gpuMode = KromiumGpuMode.ANGLE_WARP // Microsoft WARP Direct3D 11 software adapter
}
```

---

### 5. WebRTC Microphone or Camera Not Activating

**Symptom:**
WebRTC applications (Google Meet, Jitsi) report "No camera/microphone found" or fail silently.

**Solution Checklist:**
1. **Permission Handler**: Ensure your `permissionHandler` is registered and calls `request.allow()`:
   ```kotlin
   state.permissionHandler = KromiumPermissionHandler { it.allow() }
   ```
2. **macOS Entitlements**: Ensure your app bundle includes `NSCameraUsageDescription` and `NSMicrophoneUsageDescription` in `Info.plist`.
3. **Session Cache**: If a user previously denied permission, call `state.clearPermissionCache()`.

---

### 6. Compose Desktop Crash: EXCEPTION_ACCESS_VIOLATION (0xc0000005) in `skiko-windows-x64.dll`

**Symptom:**
In Compose Desktop on Windows, the application crashes immediately upon mounting a browser view:
```
# A fatal error has been detected by the Java Runtime Environment:
#  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x..., pid=..., tid=...
# Problematic frame:
# C  [skiko-windows-x64.dll+0x...]
```

**Cause:**
Jetpack Compose Desktop uses **Skiko** for GPU rasterization. On Windows, Skiko defaults to Direct3D (`skiko.renderApi=DIRECT3D`). When embedding heavyweight native AWT/CEF child HWNDs (`SwingPanel`), Skiko's Direct3D swapchain collides with the embedded window's surface, or ANGLE's Direct3D hooks intercept the device context, causing a native null pointer dereference in `skiko-windows-x64.dll`.

**Solution 1 (Automatic Protection in Kromium):**
Kromium automatically sets `System.setProperty("skiko.renderApi", "OPENGL")` when initialized on Windows if `skiko.renderApi` is not already configured, which cleanly separates Skiko's drawing pipeline from the native child HWND.

**Solution 2 (Explicit Gradle JVM Arguments):**
In your Compose Desktop Gradle build script, pass the OpenGL or Software rendering backend to your run task:
```kotlin
tasks.withType<JavaExec>().configureEach {
    systemProperty("skiko.renderApi", "OPENGL") // or "SOFTWARE"
}
```

---

## ❓ Frequently Asked Questions (FAQ)

### Can Kromium run completely offline without internet access?
**Yes.** By bundling the pre-compiled JCEF binaries in your application installer and setting `autoDownload = false` in `KromiumConfig`, Kromium runs in air-gapped, zero-internet environments. You can serve full Single Page Apps locally using custom schemes (`app://`).

### What is the difference between `kromium-compose` and `kromium-core`?
- **`kromium-compose`**: Contains the `@Composable KromiumView` and reactive `KromiumViewState` for Jetpack / JetBrains Compose Desktop.
- **`kromium-core`**: A pure JVM library with zero Compose runtime dependencies. It exposes `KromiumBrowser`, `KromiumClient`, and `KromiumEngine` with 100% Java-idiomatic APIs (`CompletableFuture`, SAM listeners) for Swing, JavaFX, and headless backend servers.

### How do I inspect DOM elements with Chrome DevTools?
Call `browser.openDevTools()` in your code, or enable remote debugging during development:
```kotlin
val config = KromiumConfig(remoteDebuggingPort = 9222)
```
Then navigate to `http://localhost:9222` in any external Chrome or Edge browser.

### Is Kromium thread-safe?
**Yes.** UI component mounting occurs on the Java AWT Event Dispatch Thread (EDT), Chromium executes in isolated native processes, and all heavy evaluations (JavaScript, PDF printing, downloads) return asynchronous `CompletableFuture` objects or suspending Kotlin coroutines.

### Why are macOS Command shortcuts (⌘C, ⌘V, ⌘W, ⌘R) not firing in my Compose Desktop app?
On macOS Cocoa, Command key combinations (`⌘`) are intercepted by `NSApplication` and routed exclusively through the top screen **Main Menu (`NSMenu`)** responder chain. If your Compose application does not declare a `MenuBar` with standard `KeyShortcut` items (e.g., `KeyShortcut(Key.C, meta = true)`), macOS drops the event with a system beep before it ever reaches the embedded Chromium view.

Declare a declarative `MenuBar` on your root `Window` to enable native macOS shortcuts cleanly without hijacking outside Compose text inputs. See the full architectural guide and code example in [Compose UI Integration & Window Chrome](../guides/compose-ui.md#️-handling-keyboard-shortcuts--macos-menubar-integration).
