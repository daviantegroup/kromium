<!--
Thank you for contributing to Kromium!
Please review our contribution guidelines in CONTRIBUTING.md before submitting your PR.
-->

## 🎯 Summary

<!-- A clear, concise description of what this PR accomplishes, why it is needed, and relevant architectural context. -->

Fixes # <!-- e.g., Fixes #123, or leave blank if there is no tracked issue -->

---

## 🔍 Key Changes

<!-- Provide a bulleted summary of key changes introduced by this Pull Request -->
- 

---

## 📦 Subsystems Affected

- [ ] `kromium-core` (CEF engine, JCEF lifecycle, runtime downloader, JS/DOM bridge, cookies, network)
- [ ] `kromium-compose` (`KromiumView`, Compose Desktop state bindings, lifecycle listeners)
- [ ] `kromium-demo` (Showcase browser application)
- [ ] `docs` / Portal (Developer documentation, architecture guides, API explorer)
- [ ] Build & CI/CD (`build.gradle.kts`, GitHub Actions workflows, publishing pipelines)

---

## 🧪 Testing & Verification

<!-- Describe how you verified your changes across target operating systems -->

- [ ] **Unit Tests**: Passed via `./gradlew test`
- [ ] **Showcase Demo**: Verified running via `./gradlew :kromium-demo:run`
- [ ] **Platforms Tested**:
  - [ ] Windows 11 / 10 (x64 / ARM64)
  - [ ] macOS (Apple Silicon / Intel)
  - [ ] Linux (Ubuntu / Debian / Arch / etc.)

---

## 📸 Screenshots / Demos (if applicable)

<!-- If this PR modifies Compose Desktop rendering, window behavior, or UI controls, include screenshots or a screen capture. -->

---

## 📋 Contributor Checklist

- [ ] My code conforms to the code style and conventions of this project.
- [ ] I have conducted a thorough self-review of my changes.
- [ ] I have added or updated documentation and KDoc comments where appropriate.
- [ ] I have added new automated tests proving the bug fix or feature works as intended.
- [ ] `./gradlew test` passes cleanly with zero failures or regressions.
- [ ] No new compiler warnings or build deprecations were introduced.
- [ ] If this introduces a breaking API change, it is clearly noted above with migration steps.
