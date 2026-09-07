# Platform-Specific Considerations

[Documentation Hub](../README.md) &bull; **Deployment** &bull; Platform Specifics

---

## 🍎 macOS (Apple Silicon & Intel)

### Framework Symlinks
Chromium on macOS expects its helper app (`jcef Helper.app`) and framework bundle (`Chromium Embedded Framework.framework`) to be located inside a `Frameworks/` directory.

* **Automated Handling**: `OperatingSystem.MacOS.ensureMacFrameworkLinks` dynamically creates and verifies the required relative symlinks at runtime.
* **Architecture Support**: Native universal binaries for Apple Silicon (ARM64 M1/M2/M3/M4) and Intel (x86_64) are resolved and loaded transparently.

### Notarization & Code Signing
When distributing signed macOS `.app` or `.dmg` packages:
* Sign all embedded `.dylib` libraries and helper apps recursively.
* Ensure your entitlements file includes the `com.apple.security.cs.allow-jit` and `com.apple.security.cs.allow-unsigned-executable-memory` entitlements required by V8's JIT compiler.

---

## 🐧 Linux (Ubuntu, Debian, Fedora, Arch)

### Dynamic Library Dependencies
Linux requires standard desktop X11/Wayland and multimedia libraries:

```bash
# Verify shared library linkage
ldd ~/.kromium/jcef/lib/libcef.so | grep "not found"
```

If any dependency is missing, install the required packages using your package manager (see [Installation Prerequisites](../getting-started/installation.md#linux-prerequisites)).

### Wayland vs. X11
CEF connects to the display server via X11. Under Wayland, the system XWayland compatibility layer is used automatically. If hardware acceleration issues occur on specific Wayland compositors, pass `--ozone-platform=wayland` or fallback to software rendering.

---

## 🪟 Windows (10 & 11)

### Microsoft Visual C++ Runtime
Chromium and JCEF native binaries are compiled with MSVC and require the Visual C++ 2015–2022 Redistributable (`msvcp140.dll`, `vcruntime140.dll`). If missing, `CefBootstrapper` throws `KromiumException.BootstrapFailed`.
* Include the VC++ redistributable in your installer package (e.g. WiX, Inno Setup, or Conveyor).

### Windows Antivirus & SmartScreen
Because `jcef_helper.exe` is a child process spawned dynamically by the JVM, certain strict enterprise EDR / antivirus tools may flag newly downloaded helper executables. Signing your final application bundle with a trusted Authenticode code signing certificate prevents false positives.
