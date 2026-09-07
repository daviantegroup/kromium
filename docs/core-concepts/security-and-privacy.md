# Security Hardening & Privacy

[Documentation Hub](../README.md) &bull; **Core Concepts** &bull; Security & Privacy

---

## 🛡️ Chromium Sandbox Protection

Chromium's multi-process architecture is built around the principle of least privilege. Rendering untrusted HTML, executing arbitrary JavaScript, and compiling WebAssembly takes place inside sandboxed child processes (`jcef_helper`).

```kotlin
Kromium.initialize {
    // Enabled by default. Strongly recommended for untrusted web content.
    sandboxEnabled = true
}
```

### What the Sandbox Restricts
* **Filesystem Access**: Renderers cannot read or write to arbitrary system files.
* **Network Sockets**: Renderers cannot open raw TCP/UDP sockets (all networking is proxied through the trusted browser broker process).
* **Process Spawning**: Renderers cannot execute system binaries or shell scripts.
* **Token Restrictions**: Runs with a restricted Windows SID token, Linux seccomp-bpf filters, and macOS Mach sandbox profiles.

---

## 🚫 Windows Registry Write Suppression

By default, standard Chromium writes user metrics, installation state, crash records, and toast registrations to the Windows Registry. In an embedded desktop application, polluting the user's system registry is undesirable.

Kromium features **automated Registry write suppression and anti-telemetry**, enabled by default via `blockRegistryAndTelemetry = true`.

```kotlin
Kromium.initialize {
    blockRegistryAndTelemetry = true // Enabled by default
}
```

### Suppressed Subsystems & Registry Keys

| Subsystem | Windows Registry Key | Suppressed By Switch |
|---|---|---|
| **Crashpad / Breakpad** | `HKCU\Software\Google\Chrome\UsageStats`<br/>`HKCU\Software\Chromium\UsageStats` | `--disable-breakpad`<br/>`--disable-crash-reporter` |
| **User Metrics (UMA)** | Metrics client GUIDs and consent | `--disable-metrics`<br/>`--disable-metrics-reporting` |
| **Component Updater** | `HKCU\Software\Google\Update\ClientState`<br/>`HKLM\Software\Google\Update\ClientState` | `--disable-component-update`<br/>`--disable-background-networking` |
| **Shell Integration** | `HKCU\Software\Clients\StartMenuInternet`<br/>`HKCR\http\shell\open\command` | `--no-default-browser-check`<br/>`--no-first-run` |
| **Action Center Toasts** | `HKCU\Software\Classes\AppUserModelId` | `--disable-features=WinNativeNotification` |
| **Background Autorun** | `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` | `--no-service-autorun`<br/>`--disable-background-mode` |

### Complete Cache & Profile Quarantining
In addition to flag injection, Kromium automatically isolates all profile state, cookies, and local storage to your application's private cache folder:
* `--root-cache-path=<your-app-cache>`
* `--user-data-dir=<your-app-cache>`

Chromium is blocked from creating profile directories under `%LOCALAPPDATA%\Chromium` or roaming profile hives.

---

## ⚠️ Dangerous Flag Detection

Chromium provides several powerful command-line flags intended strictly for internal testing. Using these flags in production applications completely breaks security boundaries:

```kotlin
// DANGEROUS FLAGS — NEVER USE IN PRODUCTION WITH UNTRUSTED WEBSITES
"--no-sandbox"                    // Strips OS-level process containment
"--disable-web-security"          // Bypasses Same-Origin Policy (SOP) & CORS
"--allow-running-insecure-content"// Executes HTTP scripts inside HTTPS pages
```

Kromium automatically inspects all arguments in `KromiumConfig.validate()`. If dangerous flags are detected, an explicit warning is logged to `KromiumLogger`:

```
[WARN] KromiumConfig: ⚠️ Dangerous command-line flag detected: --disable-web-security — This significantly reduces security.
```
