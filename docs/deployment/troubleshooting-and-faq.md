# Troubleshooting & FAQ

[Documentation Hub](../README.md) &bull; **Deployment** &bull; Troubleshooting & FAQ

---

## ❓ Frequently Asked Questions

### 1. The browser window displays a black or white blank screen.
**Cause**: GPU driver incompatibility or hardware acceleration compositing conflicts with the Swing `SwingPanel`.  
**Solution**: By default, Kromium includes `--disable-gpu-compositing` to ensure smooth Swing compositing. If issues persist on specific hardware, completely disable GPU rendering:
```kotlin
Kromium.initialize {
    addArgs("--disable-gpu", "--disable-software-rasterizer")
}
```

---

### 2. `KromiumException.NotInitialized` is thrown when calling `newClient()` or `setProxy()`.
**Cause**: You attempted to create browser instances before `Kromium.initialize()` finished bootstrapping.  
**Solution**: Always wait for the engine state to transition to `KromiumState.Ready`:
```kotlin
val client = Kromium.awaitClient()
```

---

### 3. `BootstrapFailed: java.lang.UnsatisfiedLinkError: no jawt in java.library.path`
**Cause**: Java AWT native peer library (`jawt`) could not be resolved from `java.home`.  
**Solution**: Ensure you are running on a standard JDK (Java 17 or 21 LTS from Temurin, Zulu, or Corretto). Stripped minimal JREs created with `jlink` must include the `java.desktop` module:
```bash
jlink --add-modules java.desktop,java.base ...
```

---

### 4. `BootstrapFailed: libcef.so: cannot open shared object file` (Linux)
**Cause**: A required system shared library (e.g. `libnss3` or `libasound`) is missing on the host Linux distribution.  
**Solution**: Install the required dependencies listed in the [Linux Prerequisites](../getting-started/installation.md#linux-prerequisites).

---

### 5. What if our enterprise environment is air-gapped without internet access?
**Solution**: Pre-download the JCEF bundle for your target platform and host it on an internal server, or pre-bundle it inside your app installation:
```kotlin
Kromium.initialize {
    customBundleUrl = "https://internal-artifactory.corp/jcef-bundle.tar.gz"
    customChecksumUrl = "https://internal-artifactory.corp/jcef-bundle.sha256"
}
```

---

### 6. How do I clear the local engine cache to force a fresh re-download?
**Solution**:
```kotlin
EngineRegistry.clearInstallation(EngineRegistry.defaultInstallDir())
```
Or manually delete the `~/.kromium/jcef` directory.
