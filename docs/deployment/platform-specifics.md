# Platform-Specific Considerations

Each desktop operating system has unique graphic compositing models, security entitlements, and system library dependencies. This guide provides instructions for **macOS**, **Windows**, and **Linux**.

---

## 🍏 macOS (Apple Silicon & Intel)

### Architectures
Kromium natively supports both **`aarch64` (M1/M2/M3/M4)** and **`x86_64` (Intel Macs)** without Rosetta translation.

### Camera & Microphone Entitlements (WebRTC)
If your application uses WebRTC audio or video calls, macOS Gatekeeper requires disclosure strings in your bundle's `Info.plist`:

```xml
<!-- Info.plist -->
<key>NSCameraUsageDescription</key>
<string>This application requires camera access for video conferencing.</string>
<key>NSMicrophoneUsageDescription</key>
<string>This application requires microphone access for voice calls.</string>
```

### Codesigning & Notarization
When distributing macOS apps outside the Mac App Store:
1. Sign all native binaries (including `libjcef.dylib`, `libcef.dylib`, and helper apps like `jcef_helper`) with a Developer ID Application certificate.
2. Enable the **Hardened Runtime** (`--options runtime`).
3. Include standard JIT entitlements for the Java Virtual Machine:

```xml
<!-- entitlements.plist -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
    <key>com.apple.security.device.audio-input</key>
    <true/>
    <key>com.apple.security.device.camera</key>
    <true/>
</dict>
</plist>
```

---

## 🪟 Windows (Windows 10 / 11 / ARM64)

### Visual C++ Redistributable
Chromium requires the **Microsoft Visual C++ 2015–2022 Redistributable** (`x64` or `arm64`). If users experience a crash during initial engine startup (`UnsatisfiedLinkError: jcef.dll: Can't find dependent libraries`), ensure the runtime is installed:

- Include the [VC++ Redistributable](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist) prerequisite in your WiX / Inno Setup / MSI installer:
  ```
  vc_redist.x64.exe /install /quiet /norestart
  ```

### Per-Monitor High DPI Scaling & Fractional Displays
Windows 10/11 multi-monitor setups frequently mix fractional scaling factors (e.g. 125% or 150% on laptop screens, 100% on external monitors, 200% on 4K displays).

* **Lightweight OSR Mode (Swing / JavaFX)**: Kromium's `CefBrowserOsr` inspects the active `AffineTransform` on each paint pass. When the window is moved across monitors with different scaling or the display scale changes, Kromium automatically notifies Chromium via `notifyScreenInfoChanged()` and resizes the internal raster buffer, preventing blurry bitmap interpolation.
* **Windowed Mode (Compose / AWT / SWT)**: CEF dynamically renders directly at the native OS window resolution.

To ensure Java doesn't apply artificial DPI virtualization:
```xml
<!-- application manifest or JVM flag -->
-Dsun.java2d.dpiaware=true
```

---

## 🐧 Linux (Ubuntu, Debian, Fedora, Arch)

### Shared Native Library Prerequisites
Unlike macOS and Windows which ship complete multimedia frameworks out of the box, Linux distributions require specific desktop packages:

```bash
# Debian / Ubuntu / Linux Mint:
sudo apt-get update
sudo apt-get install -y \
    libgtk-3-0 \
    libasound2 \
    libnss3 \
    libxss1 \
    libxtst6 \
    libxcursor1 \
    libxrandr2 \
    libgbm1

# Fedora / RHEL:
sudo dnf install -y \
    gtk3 \
    alsa-lib \
    nss \
    libXScrnSaver \
    libXtst \
    mesa-libgbm
```

### Wayland vs. X11
CEF Windowed rendering utilizes X11 window primitives. While modern Linux distributions use Wayland by default, XWayland handles this seamlessly. If you encounter rendering artifacts on Wayland, ensure the AWT toolkit runs under XWayland:

```bash
export GDK_BACKEND=x11
```

---

## 🪟 Toolkit-Specific Platform Notes

### Eclipse SWT Bridging (`SWT_AWT`)
* **macOS Event Loop Requirement**: On macOS, SWT requires the event loop to run on the very first thread of the application process. Add the `-XstartOnFirstThread` JVM argument when running SWT apps on macOS:
  ```bash
  java -XstartOnFirstThread -jar my-swt-app.jar
  ```
* **Windows HWND Parenting**: `SWT_AWT.new_Frame(composite)` smoothly parents the Chromium native HWND inside the SWT Win32 widget hierarchy.
* **Linux GTK Sockets**: SWT on Linux creates a `GtkSocket` to host the AWT XEmbed canvas. Ensure `libgtk-3-0` is installed.

### JavaFX `SwingNode` Embedding
* **Module Requirement**: When running JavaFX with modular Java, ensure the `javafx.swing` module is included:
  ```kotlin
  // build.gradle.kts
  javafx {
      version = "21.0.6"
      modules = listOf("javafx.controls", "javafx.swing")
  }
  ```
* **Threading Contract**: All calls modifying JavaFX scene nodes (like `swingNode.setContent(component)`) must execute on the JavaFX Application Thread via `Platform.runLater()`. All calls interacting with Swing/AWT components must execute on the AWT Event Dispatch Thread via `SwingUtilities.invokeLater()`.
