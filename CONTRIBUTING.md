# Contributing to Kromium

Thank you for your interest in contributing to **Kromium**! We welcome contributions from the community—whether it's fixing bugs, adding new features, optimizing performance, improving documentation, or creating showcase sample applications.

This document outlines the guidelines and best practices for developing, verifying, and submitting code to Kromium v3.

---

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to `code@daviante.dev`.

---

## Prerequisites & Development Setup

To build and run Kromium locally, you need:

1. **Java Development Kit (JDK)**: **JDK 21** or higher is required (Temurin / Eclipse Adoptium recommended). The project builds with a strict Gradle JVM Toolchain targeting Java 21.
2. **Git**: With proper CRLF/LF handling configured.
3. **Gradle**: The repository includes the Gradle wrapper (`./gradlew` or `gradlew.bat`), so you do not need to install Gradle globally.

### Initial Setup & Build

```bash
# 1. Clone the repository (or your fork)
git clone https://github.com/daviantegroup/kromium.git
cd kromium

# 2. Build the project and run all unit tests
./gradlew test

# 3. Verify all compilation targets and checks
./gradlew check
```

---

## Project & Multi-Module Architecture

Kromium v3 is organized as a modular Gradle workspace supporting both library artifacts and showcase applications across JVM UI ecosystems:

```
kromium/
├── kromium-core/               // Core Chromium Embedded Framework engine & API
│   ├── libs/                   // Standard jcef.jar Java API binding
│   └── src/
│       ├── main/kotlin/        // Dual-ergonomic engine: Kotlin DSL + Java CompletableFuture,
│       │                       // pure Java2D OSR (KromiumOSRPanel), runtime downloader,
│       │                       // JS/DOM bridge, cookies, network, virtual schemes, automation
│       └── test/kotlin/        // Unit and integration tests (MockK, Ktor mock, coroutines test)
├── kromium-compose/            // Compose Multiplatform Desktop UI integration
│   └── src/
│       └── main/kotlin/        // KromiumView, rememberKromiumState, shortcuts, lifecycle observers
├── kromium-sample-compose/     // Compose Desktop tabbed browser showcase application
├── kromium-sample-swing/       // Swing & FlatLaf showcase with pure Java2D OSR panel
├── kromium-sample-awt/         // Standard AWT Windowed native rendering showcase
├── kromium-sample-swt/         // Eclipse SWT native integration showcase (via SWT_AWT)
├── kromium-sample-javafx/      // JavaFX embedded browser showcase (via SwingNode OSR)
├── docs/                       // Markdown technical guides, architecture specifications, and API docs
└── .github/                    // CI/CD pipelines, PR templates, and automated JCEF release tracking
```

---

## Running Sample Showcase Applications

You can run any of the sample desktop applications locally to test your changes across different JVM UI frameworks:

```bash
# 1. Compose Multiplatform Desktop showcase
./gradlew :kromium-sample-compose:run

# 2. Java Swing & FlatLaf (Pure Java2D OSR)
./gradlew :kromium-sample-swing:run

# 3. Standard AWT Windowed native canvas
./gradlew :kromium-sample-awt:run

# 4. Eclipse SWT native embedding
./gradlew :kromium-sample-swt:run

# 5. JavaFX lightweight OSR via SwingNode
./gradlew :kromium-sample-javafx:run
```

---

## Development Workflow

### 1. Branch Naming
Create a feature branch from `master` following standard branch conventions:

- `feat/feature-name` — New features or API additions.
- `fix/bug-description` — Bug fixes or stability improvements.
- `docs/topic-name` — Documentation improvements or tutorials.
- `perf/optimization` — Performance enhancements or memory optimizations.
- `chore/task-name` — Maintenance, dependencies, or CI updates.

```bash
git checkout -b feat/web-automation-dsl
```

### 2. Architectural Principles & Coding Standards

When developing for Kromium v3, please adhere to these core principles:

- **Dual Ergonomics (Kotlin + Java)**:
  `kromium-core` serves both Kotlin and pure Java developers. Any new public API or feature must provide:
  - Idiomatic Kotlin support (coroutines, extension functions, default arguments, trailing lambdas).
  - First-class Java ergonomics (`CompletableFuture<T>` asynchronous methods, standard JavaBeans getters/setters, fluent builders, and SAM single-abstract-method interfaces). Pure Java consumers must not require the Kotlin standard library or coroutines on their classpath.
- **Pure Java2D OSR (Zero JOGL / Zero OpenGL)**:
  Kromium's Off-Screen Rendering (OSR) is strictly built on pure Java2D rasterization backed by `BufferedImage.TYPE_INT_ARGB_PRE`. Do not introduce native OpenGL, JOGL, or heavyweight GPU surface dependencies into `kromium-core`.
- **Pure JCEF Compliance**:
  Do not introduce dependencies on vendor-specific JCEF wrappers (such as DatL4g or friwi). All browser code must compile against standard `jcef.jar`.
- **Thread Safety & Lifecycle Dispatching**:
  CEF has strict threading requirements (UI thread, IO thread, FILE thread). Always ensure that:
  - Swing/AWT UI updates occur on the Event Dispatch Thread (EDT) via `SwingUtilities.invokeLater` or `Dispatchers.Swing`.
  - Coroutine continuations are properly cleaned up on cancellation via `continuation.invokeOnCancellation`.
  - CEF lifecycle resources (`CefBrowser`, `CefClient`, `CefApp`) are cleanly disposed of during window disposal without causing native thread hangs.
- **KDoc & Documentation**:
  Every public class, interface, method, and property must have comprehensive KDoc comments describing behavior, threading constraints, parameter semantics, and return values.

### 3. Verification & Quality Assurance

Before committing or submitting a pull request, run the full test suite and compilation checks:

```bash
# Run unit tests across all modules
./gradlew test

# Run linting, Kotlin compilation, and verification checks
./gradlew check
```

---

## Commit Message Guidelines

We adhere to the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <short summary>

[optional body explaining context or rationale]
```

### Common Types:
- `feat`: A new feature (e.g. `feat(core): add fluent builder for Java KromiumBrowser`).
- `fix`: A bug fix (e.g. `fix(osr): resolve repaint clipping on HiDPI display scaling`).
- `docs`: Documentation updates (e.g. `docs(swing): add pure Java FlatLaf tutorial`).
- `test`: Adding or modifying tests (e.g. `test(engine): add downloader mock tests`).
- `perf`: Performance improvements (e.g. `perf(rasterizer): optimize dirty rect blitting`).
- `ci`: CI/CD workflow changes (e.g. `ci: update JCEF automated build matrix`).
- `refactor`: Code changes that neither fix bugs nor add features.

---

## Submitting a Pull Request (PR)

1. Push your branch to your GitHub fork:
   ```bash
   git push origin feat/my-new-feature
   ```
2. Open a Pull Request against the `master` branch of `daviantegroup/kromium`.
3. Provide a detailed PR description using our [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md):
   - Clear summary of what changed and why.
   - Subsystems affected (`kromium-core`, `kromium-compose`, samples, docs).
   - Test results and verification steps across target platforms.
4. Ensure all automated CI checks pass. Maintainers will review your PR and provide constructive feedback.

---

## Contributor License Agreement (CLA)

By submitting a Pull Request, code patch, documentation, or other material to Kromium (whether via GitHub or other means), you agree to the following contributor terms:

1. **Grant of Rights**: You grant to **Daviante Group** a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable copyright and patent license to use, reproduce, modify, display, sublicense, and distribute your contributions, in whole or in part, as part of Kromium or any derivative works.
2. **Commercial & Dual-Licensing Rights**: You acknowledge and agree that Daviante Group retains the unrestricted right to license, relicense, dual-license, or commercially offer Kromium and any derivative works (including your contributions) under open-source, source-available, and/or proprietary commercial licenses without requiring additional consent or royalty payments.
3. **Originality & Authorship**: You represent that your contributions are your original creation and that you have the legal right to grant the permissions above. If your employer has intellectual property rights in your contributions, you represent that you have received authorization to make contributions on behalf of that employer.
4. **Provided As-Is**: Your contributions are provided on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. Daviante Group is under no obligation to accept or merge any contribution.

Thank you for contributing to Kromium!


