# Security Policy

The Kromium maintainers take the security of our library, its users, and the host environments in which it runs extremely seriously. This document outlines our security policies, supported versions, threat model, and the procedure for responsibly disclosing potential vulnerabilities.

---

## Supported Versions

Security fixes and patches are actively maintained for the following release lines:

| Version | Supported | Status & Notes |
|:---|:---:|:---|
| `3.0.x` | ✅ Yes | **Active / Current Stable** (CEF 150, Universal Java Desktop, Pure Java2D OSR, Automation DSL) |
| `2.1.x` | ✅ Yes | **Previous Stable** (CEF 150, CodeQL CWE-022/CWE-073 path containment) |
| `2.0.x` | ⚠️ Critical Only | **Legacy Maintenance** |
| `< 2.0` | ❌ No | End of Life (EOL). Upgrade to `3.0.x` recommended. |

---

## Reporting a Vulnerability

> [!IMPORTANT]
> **Please do not report security vulnerabilities through public GitHub issues, pull requests, or public forums.**

If you discover or suspect a security vulnerability in Kromium, please report it privately through one of the following channels:

1. **GitHub Private Vulnerability Reporting (Preferred)**:
   - Navigate to the [Security Advisories tab](https://github.com/daviantegroup/kromium/security/advisories/new) on the repository and submit an advisory draft.
2. **Security Email**:
   - Send an encrypted or confidential email to: **`code@daviante.dev`**
   - Use the subject line: `[SECURITY] Kromium Vulnerability Report - <Brief Summary>`

---

## Information to Include in Your Report

To help us investigate, triage, and resolve the issue quickly, please provide as much of the following detail as possible:

- **Component / Subsystem**: (e.g. `FileUtils`, `EngineExtractor`, `EngineRegistry`, `KromiumCookieManager`, `JsEvaluator`, `KromiumRequestInterceptor`).
- **Severity & Impact**: What an attacker could achieve (e.g. Remote Code Execution, Sandbox Escape, Information Disclosure, Directory Traversal, Man-in-the-Middle, Memory Exhaustion).
- **Environment Details**:
  - Operating System & Architecture (e.g. Windows 11 x64, macOS 15.0 arm64, Ubuntu 24.04 x64).
  - Kromium version and JCEF runtime version.
  - JDK version (17 / 21 LTS).
- **Proof of Concept (PoC)**: A minimal reproducible example (Kotlin/Java code snippet, malicious URL, or HTML payload).
- **Proposed Fix (Optional)**: If you have identified a mitigation or patch, please include it.

---

## Vulnerability Handling Timeline & SLAs

- **Initial Acknowledgment**: Within **48 hours** of receiving your report.
- **Triage & Reproduction**: Within **5 business days**, we will confirm reproduction and assign a preliminary CVSS score.
- **Remediation & Patching**: We will work on a patch in a private fork and coordinate release dates with the reporter.
- **Public Disclosure**: A public GitHub Security Advisory (GHSA) and CVE identifier (if applicable) will be published simultaneously with the release of the patched version.

---

## Security Architecture & Defense-in-Depth Model

Kromium enforces multi-layered defense-in-depth mechanisms designed to protect desktop hosts and applications from untrusted web content:

### 1. Filesystem Boundary & Path Traversal Defense (CWE-022 / CWE-073)
- `FileUtils.resolveChild` verifies canonical path boundaries across all disk operations, strictly confining child path resolution within allowed root directories (`user.home` or application base).
- Disallows parent traversal escape sequences (`..`), null byte injections, and symlink redirection attacks outside designated application directories.
- Installation directory origins are strictly sanitized and validated before any engine files are registered.

### 2. Archive Extraction & Zip-Slip Protection
- `EngineExtractor` enforces canonical path boundary verification on all archive entries during native bundle unpacking.
- Any archive entry attempting path traversal (Zip-Slip) outside the target directory throws `KromiumException.MaliciousArchiveEntry` and aborts extraction immediately.

### 3. Checksum Verification
- Downloaded runtime bundles are validated against official published SHA-256 digests (`.checksum`) using a 256 KB buffered digest stream before extraction. Corrupted or tampered archives are deleted immediately.

### 4. Dual-Binary Engine Validation
- `EngineRegistry` mandates the concurrent existence and integrity of both core binaries (`jcef` and `libcef`) on the target platform. Partial installations or missing native libraries are rejected at startup to prevent hijacking or incomplete state execution.

### 5. Chromium Process Sandboxing
- Kromium defaults `sandboxEnabled = true` in `KromiumConfig`, isolating renderer and GPU sub-processes from the host operating system.
- Disabling the sandbox or passing `--no-sandbox`, `--disable-web-security`, or `--allow-running-insecure-content` triggers high-severity runtime warnings.

### 6. WebRTC IP Leak Defense & Media Permission Model
- Neutralizes WebRTC ICE candidate discovery IP leaks over UDP through strict IP handling policies (`default_public_interface_only` by default, or `disable_non_proxied_udp` when routing through proxies).
- WebRTC hardware device access (Camera, Microphone, Screen Sharing, Desktop Audio) requires explicit permission via `KromiumPermissionHandler` with origin-normalized domain whitelisting and session-isolated revocation.

### 7. Zero-Telemetry & Anti-Tracking by Default
- Suppresses external Google telemetry, UMA metrics collection, crash dump reporting, and background networking probes.
- Injects Do Not Track (`DNT: 1`) and Global Privacy Control (`Sec-GPC: 1`) headers across all outbound requests.

### 8. SSL/TLS Certificate Verification & Policies
- Defaults to `SslErrorPolicy.Strict`, rejecting all invalid, self-signed, or expired certificates.
- Domain whitelisting (`SslErrorPolicy.AllowDomains`) restricts certificate error bypass exclusively to declared development or internal hostnames.

### 9. Host Locking & Subresource Isolation
- Enforces strict origin boundaries across both top-level window navigations and asynchronous background network requests (scripts, XHR, fetch) for kiosk and POS environments via `browser.setHostLock(domains, lockSubresources = true)`.

### 10. Memory Exhaustion Mitigation (HTML Payloads)
- Dynamic in-memory HTML payloads (`registerHtmlPayload`) are managed via an LRU size-capped cache, preventing unbounded memory growth or denial-of-service from high-frequency synthetic payload generation.

### 11. Resource Lifecycle & Isolated Disposal
- `AutoCloseable` lifecycle design guarantees deterministic cleanup of Chromium browser surfaces, focus listeners, and native bridge resources without process leaks or zombie renderer instances.

---

Thank you for helping us keep Kromium and its users safe!
