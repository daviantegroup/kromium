# Security Policy

The Kromium maintainers take the security of our library, its users, and the host environments in which it runs extremely seriously. This document outlines our security policies, supported versions, and the procedure for responsibly disclosing potential vulnerabilities.

---

## Supported Versions

Security fixes are actively backported to the following releases:

| Version | Supported | Notes |
|---|---|---|
| `1.x` | ✅ Yes | Current stable release line. |
| `< 1.0` | ❌ No | Pre-release versions are not supported. |

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

- **Component / Subsystem**: (e.g. `EngineExtractor`, `KromiumCookieManager`, `JsEvaluator`, `KromiumRequestInterceptor`).
- **Severity & Impact**: What an attacker could achieve (e.g. Remote Code Execution, Sandbox Escape, Information Disclosure, Directory Traversal, Man-in-the-Middle).
- **Environment Details**:
  - Operating System & Architecture (e.g. Windows 11 x64, macOS 14.5 arm64, Ubuntu 24.04 x64).
  - Kromium version and JCEF runtime version.
  - JDK version.
- **Proof of Concept (PoC)**: A minimal reproducible example (Kotlin code snippet, malicious URL, or HTML payload).
- **Proposed Fix (Optional)**: If you have identified a mitigation or patch, please include it.

---

## Vulnerability Handling Timeline & SLAs

- **Initial Acknowledgment**: Within **48 hours** of receiving your report.
- **Triage & Reproduction**: Within **5 business days**, we will confirm reproduction and assign a preliminary CVSS score.
- **Remediation & Patching**: We will work on a patch in a private fork and coordinate release dates with the reporter.
- **Public Disclosure**: A public GitHub Security Advisory (GHSA) and CVE identifier (if applicable) will be published simultaneously with the release of the patched version.

---

## Security Architecture & Threat Model

Kromium enforces several defense-in-depth mechanisms designed to protect desktop hosts from untrusted web content:

### 1. Archive Extraction & Directory Traversal Protection
- `EngineExtractor` enforces canonical path boundary verification on all archive entries during native bundle unpacking.
- Any archive entry attempting path traversal (Zip-Slip) outside the target directory throws `KromiumException.MaliciousArchiveEntry` and aborts extraction immediately.

### 2. Checksum Verification
- Downloaded runtime bundles are validated against official published SHA-256 digests (`.checksum`) using a 256 KB buffered digest stream before extraction. Corrupted or tampered archives are deleted immediately.

### 3. Chromium Process Sandboxing
- Kromium defaults `sandboxEnabled = true` in `KromiumConfig`, isolating renderer and GPU sub-processes from the host operating system.
- Disabling the sandbox or passing `--no-sandbox`, `--disable-web-security`, or `--allow-running-insecure-content` triggers high-severity runtime warnings.

### 4. SSL/TLS Certificate Policy
- Defaults to `SslErrorPolicy.Strict`, rejecting all invalid, self-signed, or expired certificates.
- Domain whitelisting (`SslErrorPolicy.AllowDomains`) restricts certificate error bypass to explicitly declared development hostnames.

### 5. JavaScript Bridge Sanitization
- While Kromium generates sanitized, alphanumeric query identifiers for internal IPC (`window.kromiumQuery`), developers must exercise caution when interpolating untrusted input into scripts passed to `evaluateJavaScript()`. Always encode arguments via JSON serialization (`kotlinx.serialization`).

---

Thank you for helping us keep Kromium and its users safe!
