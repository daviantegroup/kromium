# Contributing to Kromium

Thank you for your interest in contributing to **Kromium**! We welcome contributions from the community—whether it's fixing bugs, adding new features, improving documentation, or optimizing performance.

This document outlines the guidelines and best practices for developing and submitting code.

---

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to `code@daviante.dev`.

---

## Prerequisites & Development Setup

To build and run Kromium locally, you need:

1. **Java Development Kit (JDK)**: JDK 17 or JDK 21 (Temurin / Eclipse Adoptium recommended).
2. **Git**: With CRLF/LF handling configured.
3. **Gradle**: The project includes the Gradle wrapper (`./gradlew` or `gradlew.bat`), so you do not need to install Gradle globally.

### Initial Setup

```bash
# 1. Clone the repository (or your fork)
git clone https://github.com/daviantegroup/kromium.git
cd kromium

# 2. Build the project and run all unit tests
./gradlew test
```

---

## Project Structure

Kromium is organized as a multi-module Gradle project:

```
kromium/
├── docs/                     // Exhaustive markdown documentation guides
├── kromium-core/             // Pure Kotlin/JVM engine, downloader, JCEF lifecycle, cookies, and network
│   ├── libs/                 // Pure jcef.jar Java API binding
│   └── src/
│       ├── main/kotlin/      // Engine, handlers, and presentation logic
│       └── test/kotlin/      // Unit and integration tests
├── kromium-compose/          // Compose Multiplatform Desktop UI layer
│   └── src/
│       └── main/kotlin/      // KromiumView, KromiumViewState
└── .github/                  // CI/CD workflows and automated update pipeline
```

---

## Development Workflow

### 1. Branch Naming
Create a feature branch from `master` following standard branch conventions:

- `feat/feature-name` — New features or API additions.
- `fix/bug-description` — Bug fixes.
- `docs/topic-name` — Documentation improvements.
- `perf/optimization` — Performance enhancements.
- `chore/task-name` — Maintenance, dependencies, or CI updates.

```bash
git checkout -b feat/custom-scheme-handler
```

### 2. Coding Conventions & Standards
- **Standard Kotlin**: Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Pure JCEF Compliance**: Do not introduce dependencies on vendor-specific JCEF wrappers (e.g. DatL4g or friwi). All browser code must compile against standard `jcef.jar`.
- **Zero-Assumptions Documentation**: Any public API function, property, or sealed class must have complete KDoc comments describing its exact behavior, parameters, return types, and exceptions.
- **Coroutines & Thread Safety**: Asynchronous operations must be suspendable, cancel-safe, and leak-free. Clean up pending callbacks via `continuation.invokeOnCancellation`.

### 3. Running Verification Locally
Before committing or submitting a pull request, ensure that all tests compile and pass:

```bash
# Run unit tests
./gradlew test

# Compile all modules including Compose Desktop
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
- `feat`: A new feature (e.g. `feat(compose): add full-screen toggle support`).
- `fix`: A bug fix (e.g. `fix(cookies): resolve timeout on zero cookie domains`).
- `docs`: Documentation updates (e.g. `docs(network): add proxy authentication examples`).
- `test`: Adding or modifying unit tests (e.g. `test(engine): add downloader mock tests`).
- `ci`: CI/CD workflow changes (e.g. `ci: refine automated JCEF update checks`).
- `refactor`: Code changes that neither fix bugs nor add features.

---

## Submitting a Pull Request (PR)

1. Push your branch to your GitHub fork:
   ```bash
   git push origin feat/my-new-feature
   ```
2. Open a Pull Request against the `master` branch of `daviantegroup/kromium`.
3. Provide a clear PR description detailing:
   - What changed and why.
   - Any API modifications or breaking changes.
   - Verification steps taken.
4. Ensure all automated CI checks pass. Maintainers will review your PR and provide feedback promptly.

Thank you for contributing to Kromium!
