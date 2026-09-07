# Core JVM & Swing Integration Guide

[Kromium Documentation](README.md) &bull; [Interactive Web Portal](https://kromium.daviante.dev/#/docs/swing-embedding)

This guide explains how to embed Kromium in pure Kotlin JVM or Java desktop applications using standard **Swing (`JFrame`, `JPanel`)** and **AWT** without requiring Jetpack Compose Multiplatform.

---

## Table of Contents

1. [Overview & Prerequisites](#overview--prerequisites)
2. [Gradle Dependency](#gradle-dependency)
3. [Lifecycle & Engine Initialization](#lifecycle--engine-initialization)
4. [Embedding in a `JFrame`](#embedding-in-a-jframe)
5. [Thread Safety (EDT vs CEF UI Loop)](#thread-safety-edt-vs-cef-ui-loop)
6. [Controlling Navigation & Zoom](#controlling-navigation--zoom)
7. [Dialog & Download Handling in Swing](#dialog--download-handling-in-swing)
8. [Clean Shutdown & Window Disposal](#clean-shutdown--window-disposal)
9. [Complete Swing Browser Example](#complete-swing-browser-example)

---

## Overview & Prerequisites

While Kromium offers a first-class declarative `@Composable KromiumView` for Compose Multiplatform Desktop, the core engine (`kromium-core`) is completely independent of Compose.

Under the hood, Chromium Embedded Framework provides heavy-weight AWT canvas surfaces via `JAWT`. `KromiumClient.createBrowser(url)` directly exposes a standard `java.awt.Component` (`browser.uiComponent`) that can be placed inside any Swing layout manager (`BorderLayout`, `GridBagLayout`, `BoxLayout`, or MigLayout).

---

## Gradle Dependency

To use Kromium without Compose Desktop dependencies, import `kromium-core`:

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.daviante:kromium-core:1.0.150")
}
```

---

## Lifecycle & Engine Initialization

Before creating any Swing components, initialize the Kromium singleton on a background coroutine:

```kotlin
import dev.daviante.kromium.presentation.browser.Kromium
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 1. Initialize engine
    Kromium.initialize {
        windowlessRendering = false
        remoteDebuggingPort = 9222
    }

    // 2. Launch Swing UI on the Event Dispatch Thread
    javax.swing.SwingUtilities.invokeLater {
        createAndShowGUI()
    }
}
```

---

## Embedding in a `JFrame`

```kotlin
import dev.daviante.kromium.presentation.browser.Kromium
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.JPanel

fun createAndShowGUI() {
    val frame = JFrame("Kromium Swing Desktop")
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    frame.setSize(1280, 800)
    frame.setLocationRelativeTo(null)

    // Acquire a new client
    val client = Kromium.newClient()
    
    // Create browser instance
    val browser = client.createBrowser(initialUrl = "https://github.com")

    // The browser's native UI component is a java.awt.Component
    val browserComponent = browser.uiComponent

    frame.layout = BorderLayout()
    frame.add(browserComponent, BorderLayout.CENTER)

    frame.isVisible = true
}
```

---

## Thread Safety (EDT vs CEF UI Loop)

Kromium coordinates two primary thread systems:
1. **Swing Event Dispatch Thread (EDT)**: Handles Swing UI clicks, layout recalculations, and component resizing.
2. **CEF UI Message Loop**: The native Chromium thread responsible for networking, V8 JavaScript execution, and GPU command buffer synchronization.

> [!IMPORTANT]
> - Never block the Swing EDT with heavy coroutine processing or synchronous I/O.
> - `browser.loadUrl()`, `browser.goBack()`, `browser.goForward()`, and `browser.reload()` are thread-safe and can be invoked from any thread.
> - Asynchronous methods such as `browser.evaluateJavaScript()` or `KromiumCookieManager.getCookies()` are suspend functions designed to run on `Dispatchers.IO` or `Dispatchers.Default`.

---

## Controlling Navigation & Zoom

```kotlin
// Programmatic Navigation
browser.loadUrl("https://example.com")
browser.goBack()
browser.goForward()
browser.reload()
browser.stop()

// In-Page Search
browser.find("Kotlin", forward = true, matchCase = false)

// Zoom Controls (0.0 = 100%, 1.0 = 120%, -1.0 = 80%)
browser.setZoom(1.5)
val currentZoom = browser.getZoom()

// Developer Tools
browser.openDevTools()
```

---

## Dialog & Download Handling in Swing

Attach Swing-friendly listeners directly to `client`:

```kotlin
import dev.daviante.kromium.presentation.handler.KromiumJsDialogListener
import dev.daviante.kromium.presentation.handler.KromiumDownloadListener
import javax.swing.JOptionPane

// 1. JavaScript modal alert/confirm dialogs
client.jsDialogListener = KromiumJsDialogListener { type, message, defaultValue, callback ->
    when (type) {
        JsDialogType.ALERT -> {
            JOptionPane.showMessageDialog(frame, message, "JavaScript Alert", JOptionPane.INFORMATION_MESSAGE)
            callback.confirm("")
            true
        }
        JsDialogType.CONFIRM -> {
            val res = JOptionPane.showConfirmDialog(frame, message, "Confirm", JOptionPane.YES_NO_OPTION)
            if (res == JOptionPane.YES_OPTION) callback.confirm("") else callback.cancel()
            true
        }
        JsDialogType.PROMPT -> {
            val input = JOptionPane.showInputDialog(frame, message, defaultValue)
            if (input != null) callback.confirm(input) else callback.cancel()
            true
        }
    }
}

// 2. Download monitoring
client.downloadListener = KromiumDownloadListener { downloadItem, callback ->
    println("Starting download: ${downloadItem.suggestedFileName} (${downloadItem.totalBytes} bytes)")
    callback.continueDownload()
}
```

---

## Clean Shutdown & Window Disposal

To prevent orphaned Chromium background helper processes (`Chromium Embedded Framework Helper`), register a window listener that disposes the browser upon frame closing:

```kotlin
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

frame.addWindowListener(object : WindowAdapter() {
    override fun windowClosing(e: WindowEvent) {
        // Dispose client and release native resources
        client.dispose()
    }

    override fun windowClosed(e: WindowEvent) {
        // Upon application termination, shut down the engine singleton
        Kromium.shutdown()
    }
})
```

---

## Complete Swing Browser Example

```kotlin
package com.example.swing

import dev.daviante.kromium.presentation.browser.Kromium
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

fun main() = runBlocking {
    Kromium.initialize()

    SwingUtilities.invokeLater {
        val frame = JFrame("Kromium Desktop Browser")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.size = Dimension(1280, 800)
        frame.setLocationRelativeTo(null)

        val client = Kromium.newClient()
        val browser = client.createBrowser("https://github.com/daviantegroup/kromium")

        // Top Navigation Toolbar
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        val backBtn = JButton("←").apply { addActionListener { browser.goBack() } }
        val forwardBtn = JButton("→").apply { addActionListener { browser.goForward() } }
        val reloadBtn = JButton("↻").apply { addActionListener { browser.reload() } }
        val urlField = JTextField("https://github.com/daviantegroup/kromium", 45).apply {
            addActionListener { browser.loadUrl(text) }
        }
        val devToolsBtn = JButton("DevTools").apply { addActionListener { browser.openDevTools() } }

        toolbar.add(backBtn)
        toolbar.add(forwardBtn)
        toolbar.add(reloadBtn)
        toolbar.add(urlField)
        toolbar.add(devToolsBtn)

        frame.layout = BorderLayout()
        frame.add(toolbar, BorderLayout.NORTH)
        frame.add(browser.uiComponent, BorderLayout.CENTER)

        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                client.dispose()
                Kromium.shutdown()
            }
        })

        frame.isVisible = true
    }
}
```

---

[← Return to Documentation Index](README.md) &bull; [Visit Online Documentation](https://kromium.daviante.dev)
