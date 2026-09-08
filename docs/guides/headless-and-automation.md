# Headless Automation & PDF Generation

Kromium can execute headlessly without rendering a visible desktop window. This makes it ideal for background web scrapers, automated regression testing, scheduled report generation, and vector PDF printing in backend servers or CI/CD pipelines.

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

      # Run automated PDF/scraping tests under xvfb:
      - name: Run Headless Suite
        run: |
          xvfb-run --auto-servernum --server-args="-screen 0 1920x1080x24" ./gradlew test
```
