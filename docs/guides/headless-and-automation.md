# Headless Automation & PDF Generation

Kromium can execute headlessly without rendering a visible desktop window. This makes it ideal for background content extraction, automated regression testing, synthetic monitoring, scheduled report generation, and vector PDF printing in backend servers or CI/CD pipelines.

---

## ⚙️ Configuring Headless Engine

Set `windowlessRendering = true` during engine configuration:

```kotlin
val config = KromiumConfig().apply {
    windowlessRendering = true // Headless off-screen rendering
    sandboxEnabled = true
}
KromiumEngine.getInstance().initialize(config)
```

---

## 🖨️ Automated Vector PDF Export

Kromium can render any web page directly to a crisp, high-resolution vector PDF with customizable page sizes, margins, headers, and footers.

### Configuring PDF Layout (`KromiumPdfSettings`)

| Setting | Type | Default | Description |
|:---|:---|:---|:---|
| `landscape` | `Boolean` | `false` | Page orientation (`true` = landscape, `false` = portrait). |
| `printBackgrounds` | `Boolean` | `true` | Renders CSS background colors and images. |
| `paperWidth` / `paperHeight` | `Double` | `8.5` x `11.0` | Paper dimension in inches. |
| `marginTop` / `marginBottom` | `Double` | `0.4` | Margins in inches. |
| `headerTemplate` / `footerTemplate` | `String?` | `null` | HTML templates for page numbers and document titles. |

### Kotlin Coroutines Example

```kotlin
package com.example.headless

import dev.daviante.kromium.KromiumEngine
import dev.daviante.kromium.presentation.browser.KromiumPdfSettings
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() = runBlocking {
    val engine = KromiumEngine.getInstance()
    val client = engine.createClient()
    val browser = client.createBrowser("https://en.wikipedia.org/wiki/Chromium_(web_browser)")

    // Wait for page load, then print to PDF:
    val outputFile = File("build/reports/chromium_article.pdf")
    val settings = KromiumPdfSettings(
        landscape = false,
        printBackgrounds = true,
        headerTemplate = "<span style='font-size: 8pt;'>Corporate Report</span>",
        footerTemplate = "<span style='font-size: 8pt;' class='pageNumber'></span>"
    )

    val generatedPdf = browser.printToPdf(outputFile, settings)
    println("Vector PDF generated at: ${generatedPdf.absolutePath}")

    browser.close(true)
    client.dispose()
    engine.dispose()
}
```

### Pure Java Example with CompletableFuture

```java
package com.example.headless;

import dev.daviante.kromium.KromiumBrowser;
import dev.daviante.kromium.KromiumClient;
import dev.daviante.kromium.KromiumConfig;
import dev.daviante.kromium.KromiumEngine;
import dev.daviante.kromium.presentation.browser.KromiumPdfSettings;
import java.io.File;
import java.util.concurrent.CompletableFuture;

public final class HeadlessPdfJavaDemo {
    public static void main(String[] args) {
        KromiumConfig config = new KromiumConfig();
        config.setWindowlessRendering(true);
        KromiumEngine.getInstance().initialize(config);

        KromiumClient client = KromiumEngine.getInstance().createClient();
        KromiumBrowser browser = client.createBrowser("https://example.com");

        File targetPdf = new File("output_report.pdf");
        CompletableFuture<File> pdfFuture = browser.printToPdfAsync(targetPdf, KromiumPdfSettings.DEFAULT);

        pdfFuture.thenAccept(file -> {
            System.out.println("PDF generation completed: " + file.getAbsolutePath());
            browser.close(true);
            client.dispose();
            KromiumEngine.getInstance().dispose();
        }).exceptionally(ex -> {
            System.err.println("Failed to export PDF: " + ex.getMessage());
            return null;
        });
    }
}
```

---

## 📸 Automated Screenshots

Capture the full rendered viewport without opening a window:

```kotlin
val screenshot = browser.takeScreenshot()
if (screenshot != null) {
    javax.imageio.ImageIO.write(screenshot, "PNG", File("build/screenshot.png"))
}
```

---

## 🚀 High-Level Web Automation & Content Extraction DSL

Kromium provides a native, high-level interaction DSL designed for automated End-to-End (E2E) testing, robotic process automation (RPA), and reliable web data extraction.

### Key Capabilities

* **Auto-Waiting Execution**: All interaction methods (`click`, `fill`, `type`, `selectOption`) automatically poll the DOM until the target selector is attached and visible before dispatching events.
* **Modern Framework Compatibility**: `fill()` and `type()` properly invoke property descriptors (`HTMLInputElement.prototype`), simulate natural keystrokes, and dispatch `input`, `change`, and `keydown` events to trigger state updates in **React**, **Angular**, **Vue**, and **Svelte**.
* **Direct Access to `automation`**: Available directly on any `KromiumBrowser` instance via `browser.automation` or through top-level convenience forwarders (`browser.click(...)`, `browser.fill(...)`, `browser.waitForSelector(...)`).
* **Dual Kotlin Coroutine & Java CompletableFuture APIs**: Every method offers non-blocking suspending execution in Kotlin alongside standard `Async` variants returning `CompletableFuture` in Java.

### Automation DSL Method Reference

| Method (Kotlin / Java Async) | Parameters | Return Type | Description |
|:---|:---|:---|:---|
| `waitForSelector(selector, timeoutMs)`<br/>`waitForSelectorAsync(...)` | `selector: String`, `timeoutMs: Long = 10_000` | `Boolean` | Suspends until an element matching the CSS selector is present in the DOM. |
| `click(selector, timeoutMs)`<br/>`clickAsync(...)` | `selector: String`, `timeoutMs: Long = 10_000` | `Boolean` | Waits for the element to appear, scrolls it into view, and dispatches native-equivalent click events. |
| `fill(selector, text, timeoutMs)`<br/>`fillAsync(...)` | `selector: String`, `text: String`, `timeoutMs: Long = 10_000` | `Boolean` | Clears existing content and inputs text with full reactive framework event dispatching. |
| `type(selector, text, delayMs, timeoutMs)`<br/>`typeAsync(...)` | `selector: String`, `text: String`, `delayMs: Long = 50`, `timeoutMs: Long = 10_000` | `Boolean` | Types characters sequentially with a realistic configurable inter-keystroke delay. |
| `selectOption(selector, value, timeoutMs)`<br/>`selectOptionAsync(...)` | `selector: String`, `value: String`, `timeoutMs: Long = 10_000` | `Boolean` | Selects `<option>` elements matching value, text, or index, triggering `change` events. |
| `getTextContent(selector, timeoutMs)`<br/>`getTextContentAsync(...)` | `selector: String`, `timeoutMs: Long = 10_000` | `String?` | Extracts rendered `.innerText` or `.textContent` from the target element. |
| `getAttribute(selector, attribute, timeoutMs)`<br/>`getAttributeAsync(...)` | `selector: String`, `attribute: String`, `timeoutMs: Long = 10_000` | `String?` | Retrieves an HTML attribute or DOM property value. |
| `isVisible(selector)`<br/>`isVisibleAsync(...)` | `selector: String` | `Boolean` | Checks if element exists and is rendered (bounding rect dimensions > 0 and `visibility != hidden`). |
| `isChecked(selector)`<br/>`isCheckedAsync(...)` | `selector: String` | `Boolean` | Checks the `.checked` property of a checkbox or radio input. |
| `count(selector)`<br/>`countAsync(...)` | `selector: String` | `Int` | Returns the total count of matching DOM nodes currently attached. |
| `waitForUrl(urlOrPattern, timeoutMs)`<br/>`waitForUrlAsync(...)` | `urlOrPattern: String`, `timeoutMs: Long = 10_000` | `Boolean` | Suspends until current URL matches an exact string, substring, or regex pattern. |
| `waitForNetworkIdle(idleTimeMs, maxWaitMs)`<br/>`waitForNetworkIdleAsync(...)` | `idleTimeMs: Long = 500`, `maxWaitMs: Long = 10_000` | `Boolean` | Waits until in-flight HTTP(S) network requests settle and stay at zero for `idleTimeMs`. |

---

## 🌐 Network Lifecycle Synchronization

Single-Page Applications (SPAs) frequently perform asynchronous background fetches (`fetch`, `XMLHttpRequest`) after the initial HTML page load completes. To prevent race conditions in automated workflows, Kromium tracks in-flight network requests at the engine level.

```kotlin
// Navigate to an SPA dashboard:
browser.loadUrl("https://app.enterprise.internal/dashboard")

// Wait until all asynchronous GraphQL/REST requests settle:
val isNetworkIdle = browser.waitForNetworkIdle(idleTimeMs = 500, maxWaitMs = 15_000)

// Extract fully populated dashboard metric:
val revenue = browser.getTextContent(".stat-revenue-value")
println("Quarterly Revenue: $revenue")
```

---

## 🖥️ Headless Desktop Environment Normalization

When Chromium runs in headless or containerized environments, certain standard desktop browser characteristics (such as `navigator.webdriver`, desktop `navigator.plugins`, and standard audio/video hardware codecs) default to automated test configurations. Many modern enterprise web applications, single-sign-on (SSO) portals, and interactive media dashboards require consistent desktop environment fidelity to render full rich-client experiences.

Kromium's **Desktop Environment Normalization** configures Chromium's runtime to match standard interactive desktop browser profiles:

* **Prototype-Level Object Consistency**: Normalizes `navigator.webdriver` on `Navigator.prototype` while preserving prototype chains and native `toString()` outputs.
* **Standard Desktop Plugin Profiles**: Supplies mock desktop `PluginArray` and `MimeTypeArray` structures typical of desktop Chrome (PDF Viewer, Native Client).
* **Hardware API Consistency**: Configures realistic `navigator.hardwareConcurrency` and standard desktop screen geometries.
* **Subframe Isolation Fidelity**: Automatically propagates desktop environment properties across newly attached subframes (`HTMLIFrameElement.prototype.contentWindow`).

### Configuration Options

Desktop Environment Normalization can be enabled globally via `KromiumConfig` or per-browser instance:

#### Option A: Global Configuration in `KromiumConfig`

```kotlin
val config = KromiumConfig().apply {
    windowlessRendering = true
    emulateDesktopEnvironment = true // Enabled globally for all browser instances
}
Kromium.initialize(config)
```

#### Option B: Per-Browser Activation

```kotlin
val browser = client.createBrowser("https://portal.enterprise.internal")
browser.emulateDesktopEnvironment()
```

---

## 💡 Complete End-to-End Enterprise Automation Examples

### Kotlin Automation & Content Extraction Flow

```kotlin
package com.example.automation

import dev.daviante.kromium.domain.config.KromiumConfig
import dev.daviante.kromium.presentation.browser.Kromium
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 1. Initialize Headless Engine with Desktop Normalization:
    val config = KromiumConfig().apply {
        windowlessRendering = true
        emulateDesktopEnvironment = true
    }
    Kromium.initialize(config)

    val client = Kromium.newClient()
    val browser = client.createBrowser("https://example.com/login")

    // 2. Automate Login Form (React/Angular friendly):
    browser.waitForSelector("#username")
    browser.fill("#username", "enterprise-service-account")
    browser.fill("#password", "SecretToken123!")
    browser.click("button[type='submit']")

    // 3. Synchronize with background network activity:
    browser.waitForNetworkIdle(idleTimeMs = 500, maxWaitMs = 10_000)

    // 4. Extract dynamic data:
    val rowCount = browser.count(".data-table tbody tr")
    println("Successfully loaded $rowCount dashboard records.")

    for (i in 1..rowCount) {
        val rowTitle = browser.getTextContent(".data-table tbody tr:nth-child($i) .title")
        println("Record #$i: $rowTitle")
    }

    // 5. Clean teardown:
    browser.close(true)
    client.dispose()
    Kromium.dispose()
}
```

### Compose Multiplatform Automation (`KromiumViewState`)

In Compose Desktop, you can execute the exact same automation DSL directly through your reactive `KromiumViewState`:

```kotlin
val state = rememberKromiumState(initialUrl = "https://example.com/login")

LaunchedEffect(state) {
    // Automate directly through the state holder:
    state.waitForSelector("#username")
    state.fill("#username", "enterprise-service-account")
    state.fill("#password", "SecretToken123!")
    state.click("button[type='submit']")
    state.waitForNetworkIdle()

    val count = state.count(".data-table tbody tr")
    println("Loaded $count records inside Compose Desktop")
}

KromiumView(state = state, modifier = Modifier.fillMaxSize())
```

### Pure Java Automation Flow (`CompletableFuture`)

```java
package com.example.automation;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;
import dev.daviante.kromium.presentation.browser.KromiumClient;

public final class JavaAutomationDemo {
    public static void main(String[] args) {
        KromiumConfig config = KromiumConfig.builder()
            .windowlessRendering(true)
            .emulateDesktopEnvironment(true)
            .build();

        Kromium.initialize(config);

        KromiumClient client = Kromium.newClient();
        KromiumBrowser browser = client.createBrowser("https://example.com/portal");

        // Chain asynchronous automation steps with CompletableFuture:
        browser.waitForSelectorAsync("#search-input", 5_000)
            .thenCompose(found -> browser.fillAsync("#search-input", "Chromium Enterprise", 5_000))
            .thenCompose(clicked -> browser.clickAsync("#btn-search", 5_000))
            .thenCompose(idle -> browser.waitForNetworkIdleAsync(500, 10_000))
            .thenCompose(extracted -> browser.getTextContentAsync(".result-header", 5_000))
            .thenAccept(headerText -> {
                System.out.println("Search Result Header: " + headerText);
                browser.close(true);
                client.dispose();
                Kromium.dispose();
            })
            .exceptionally(ex -> {
                System.err.println("Automation pipeline failed: " + ex.getMessage());
                browser.close(true);
                client.dispose();
                Kromium.dispose();
                return null;
            });
    }
}
```

---

## 🤖 CI/CD Linux Environment Setup (GitHub Actions)

On Linux servers without a physical monitor, run with a virtual framebuffer (`xvfb`):

```yaml
# .github/workflows/headless-tests.yml
name: Headless Automated Browser Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      # Install Linux native dependencies:
      - name: Install Native Prerequisites
        run: |
          sudo apt-get update
          sudo apt-get install -y libgtk-3-0 libasound2 libnss3 libxss1 xvfb

      # Run automated PDF and content extraction tests under xvfb:
      - name: Run Headless Suite
        run: |
          xvfb-run --auto-servernum --server-args="-screen 0 1920x1080x24" ./gradlew test
```
