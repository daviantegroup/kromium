# Quickstart with Pure Kotlin JVM & Swing

[Documentation Hub](../README.md) &bull; **Getting Started** &bull; Pure Kotlin/Swing JVM Quickstart

---

## 🎯 Objective

If your project uses standard Java Swing, JavaFX, or runs as a headless server utility without Compose Desktop, this guide explains how to use `kromium-core` directly.

---

## 1. Engine Bootstrap

Initialize the engine using Kotlin Coroutines on a background dispatcher (`Dispatchers.IO`). Once ready, Chromium browser UI components can be attached to any Swing `JFrame` or `JPanel`.

```kotlin
import dev.daviante.kromium.presentation.browser.Kromium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() = runBlocking {
    println("Initializing Kromium engine...")

    // 1. Initialize engine on Dispatchers.IO
    withContext(Dispatchers.IO) {
        Kromium.initialize {
            userAgent = "MySwingApp/1.0"
            sandboxEnabled = true
        }
    }

    println("Engine ready! Launching Swing window on EDT...")

    // 2. Build Swing UI on the Event Dispatch Thread (EDT)
    SwingUtilities.invokeLater {
        createAndShowGUI()
    }
}
```

---

## 2. Attaching the Browser to a `JFrame`

`Kromium.createBrowser()` returns a `KromiumBrowser` instance. Its `uiComponent` property is a standard AWT/Swing `java.awt.Component` that can be added to any container:

```kotlin
private fun createAndShowGUI() {
    val frame = JFrame("Kromium Swing Desktop")
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    frame.setSize(1280, 800)
    frame.setLocationRelativeTo(null)

    // Create a new client and browser instance
    val client = Kromium.newClient()
    val browser = client.createBrowser("https://github.com")

    // Add browser component to center of the window
    frame.contentPane.add(browser.uiComponent, BorderLayout.CENTER)

    // Ensure clean disposal when the window is closed
    frame.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent) {
            println("Closing browser window and releasing native peer...")
            browser.dispose()
            client.dispose()
        }
    })

    frame.isVisible = true
}
```

---

## 3. Controlling the Browser Instance

With pure Kotlin/JVM, you have direct, imperative access to the `KromiumBrowser` API:

```kotlin
// Navigate
browser.loadUrl("https://example.com")

// Navigation history
if (browser.canGoBack()) {
    browser.goBack()
}
if (browser.canGoForward()) {
    browser.goForward()
}

// Reload
browser.reload()

// In-page search
browser.find(searchText = "Kotlin", forward = true, matchCase = false, findNext = false)

// Zoom control (0.0 = 100%, 1.0 = 120%, -1.0 = 80%)
browser.setZoom(1.5)

// Capture high-resolution screenshot
val image: java.awt.image.BufferedImage? = browser.takeScreenshot()

// Print to PDF
browser.printToPdf("report.pdf")
```

---

## 4. Next Steps

* Discover full browser and client APIs in the [**Browser & Client API Reference**](../reference/browser-and-client-api.md).
* Handle downloads, modal dialogs, and errors in the [**Downloads & Dialogs Guide**](../guides/downloads-and-dialogs.md).
* Run web automations in the background with zero visible UI in the [**Headless & Automation Guide**](../guides/headless-and-automation.md).
