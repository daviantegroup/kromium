# Navigation, History & Page Controls

[Documentation Hub](../README.md) &bull; **Guides** &bull; Navigation & Controls

---

## 🧭 Page Navigation

Navigate to web destinations, local HTML assets, or load raw HTML strings directly:

```kotlin
// Navigate to external HTTPS URL
browser.loadUrl("https://example.com")

// Navigate to local resource bundled in your app
browser.loadUrl("file:///path/to/local/app.html")

// Load raw HTML content directly with a base URL
browser.loadHtml(
    html = "<h1>Hello from Kromium</h1><p>Embedded desktop browser</p>",
    baseUrl = "http://kromium.local/"
)
```

---

## 📜 Session History

Inspect and control navigation history:

```kotlin
// Check back/forward availability
if (browser.canGoBack()) {
    browser.goBack()
}

if (browser.canGoForward()) {
    browser.goForward()
}

// Reload current page
browser.reload()

// Reload bypassing local HTTP cache
browser.reloadIgnoreCache()

// Halt in-flight network navigation
browser.stopLoad() // On Compose KromiumViewState: state.stopLoading()
```

---

## 🔍 In-Page Text Search

Perform search queries across the rendered DOM with real-time match highlighting:

```kotlin
// Find initial match
browser.find(
    searchText = "Compose",
    forward = true,
    matchCase = false,
    findNext = false
)

// Find next match
browser.find(
    searchText = "Compose",
    forward = true,
    matchCase = false,
    findNext = true
)

// Clear active search highlights
browser.stopFinding(clearSelection = true)
```

---

## 🔍 Zoom Level Control

Adjust zoom scaling factors:

```kotlin
// Get current zoom level (0.0 = 100%)
val currentZoom: Double = browser.getZoom()

// Set zoom (1.0 = 120%, 2.0 = 144%, -1.0 = 80%)
browser.setZoom(1.2)

// Reset to default 100%
browser.setZoom(0.0)
```

---

## 📸 Screenshots & PDF Printing

### Capture Rendered Viewport
Capture a pixel-perfect `BufferedImage` of the currently visible rendering surface:

```kotlin
val screenshot: java.awt.image.BufferedImage? = browser.takeScreenshot()

if (screenshot != null) {
    // Save to disk as PNG
    javax.imageio.ImageIO.write(screenshot, "PNG", java.io.File("screenshot.png"))
}
```

### Export Page to PDF
Save full-length vectorized PDF documents with print stylesheets applied:

```kotlin
val targetPdfPath = "documentation.pdf"
browser.printToPdf(targetPdfPath)
println("Saved PDF to: $targetPdfPath")
```
