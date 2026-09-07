# Headless Browsing & Automation

[Documentation Hub](../README.md) &bull; **Guides** &bull; Headless & Automation

---

## 🤖 Zero-Dependency Headless Browsing

Standard CEF off-screen rendering (OSR) requires linking third-party OpenGL bindings like JOGL (`com.jogamp.opengl`), which frequently cause JVM crashes on Linux Wayland or Apple Silicon when dependencies are missing.

Kromium solves this with **Zero-Dependency Headless Browsing**:
`Kromium.createHeadlessBrowser()` creates an embedded browser backed by an off-screen Swing native window peer (`JWindow` positioned outside desktop coordinates).

```kotlin
val headlessBrowser = Kromium.createHeadlessBrowser(
    url = "https://news.ycombinator.com",
    width = 1280,
    height = 800
)
```

### Key Advantages
* **100% Reliable**: Zero JOGL or OpenGL classpath dependencies required.
* **Full JavaScript & V8 Execution**: Single Page Applications (React, Angular, Vue), dynamic Canvas animations, and WebAssembly execute identically to a visible window.
* **Automated Peer Lifecycle**: Native Swing peer window is automatically torn down when `headlessBrowser.dispose()` is called.

---

## 🕸️ Background Web Scraping

Extract structured data from dynamic web pages without showing any UI:

```kotlin
import dev.daviante.kromium.presentation.browser.Kromium
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    Kromium.initialize()

    val browser = Kromium.createHeadlessBrowser("https://example.com")

    // Await page load and DOM readiness
    delay(2000)

    // 1. Extract raw visible text
    val text = browser.getText()
    println("Extracted text:\n$text")

    // 2. Query DOM elements via JavaScript
    val headingsJson = browser.evaluateJavaScript("""
        Array.from(document.querySelectorAll('h1, h2')).map(el => el.innerText)
    """.trimIndent())
    println("Headings: $headingsJson")

    // 3. Clean up native peer
    browser.dispose()
    Kromium.dispose()
}
```

---

## 📸 Automated Screenshot & PDF Generation

Generate reports or thumbnails headlessly:

```kotlin
val browser = Kromium.createHeadlessBrowser("https://github.com", width = 1920, height = 1080)
delay(3000)

// Capture high-resolution PNG screenshot
val screenshot = browser.takeScreenshot()
if (screenshot != null) {
    javax.imageio.ImageIO.write(screenshot, "PNG", java.io.File("github_desktop.png"))
}

// Generate full vector PDF
browser.printToPdf("github_page.pdf")

browser.dispose()
```
