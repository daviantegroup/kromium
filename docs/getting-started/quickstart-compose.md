# Compose Desktop Quickstart

This guide shows you how to build a fully functional, modern web browser using **Kotlin** and **Compose Multiplatform Desktop**.

---

## 1. Minimal Working Example

Create a new file `Main.kt` in your Compose Desktop project:

```kotlin
package com.example.browser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumViewState
import dev.daviante.kromium.presentation.browser.Kromium

fun main() = application {
    // 1. Asynchronously bootstrap the Chromium Embedded Framework engine
    LaunchedEffect(Unit) {
        if (!Kromium.isReady) {
            Kromium.initialize()
        }
    }

    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Kromium Compose Browser"
    ) {
        MaterialTheme {
            // 2. Remember state across recompositions
            val state = rememberKromiumViewState(initialUrl = "https://github.com/daviante/kromium")

            // 3. Render declarative Chromium view
            KromiumView(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
```

Run the application:
```bash
./gradlew run
```

On first launch, Kromium will automatically download the platform native JCEF binaries, extract them to user cache, and render the page with hardware-accelerated GPU graphics.

---

## 2. Understanding `KromiumViewState`

In Compose Desktop, all browser controls, navigation, and reactive state are managed via `KromiumViewState`.

### Observable State Properties

| Property | Type | Description |
|:---|:---|:---|
| `state.url` | `String` | Current page URL (updated automatically upon navigation). |
| `state.title` | `String` | Active document title tag (`<title>`). |
| `state.isLoading` | `Boolean` | True when a page or frame is actively transferring data. |
| `state.canGoBack` | `Boolean` | True if history contains previous navigation entries. |
| `state.canGoForward` | `Boolean` | True if history contains forward navigation entries. |
| `state.zoomLevel` | `Double` | Zoom level offset (`0.0` = 100%, `1.0` = 120%, `-1.0` = 80%). |

### Navigation Methods

```kotlin
// Navigate to a new address:
state.loadUrl("https://kotlinlang.org")

// History navigation:
if (state.canGoBack) state.goBack()
if (state.canGoForward) state.goForward()

// Reloading:
state.reload()        // standard cache reload
state.reloadIgnoreCache() // bypass HTTP cache
state.stopLoading()   // abort pending request

// Zoom control:
state.zoomIn()        // increment zoom by 0.5
state.zoomOut()       // decrement zoom by 0.5
state.resetZoom()     // reset to 100%
```

---

## 3. Adding a Navigation Toolbar

Let's assemble a complete browser window with back/forward buttons, a reload button, a reactive address bar, and a loading spinner:

```kotlin
package com.example.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.KromiumViewState
import dev.daviante.kromium.compose.rememberKromiumViewState
import dev.daviante.kromium.presentation.browser.Kromium

fun main() = application {
    LaunchedEffect(Unit) {
        if (!Kromium.isReady) Kromium.initialize()
    }

    Window(onCloseRequest = ::exitApplication, title = "Kromium Desktop") {
        MaterialTheme {
            val state = rememberKromiumViewState("https://kotlinlang.org")

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Navigation Bar
                BrowserNavBar(state = state)

                // Main Chromium Webview
                KromiumView(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun BrowserNavBar(state: KromiumViewState) {
    var inputUrl by remember(state.url) { mutableStateOf(state.url) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { state.goBack() },
            enabled = state.canGoBack
        ) {
            Text("◀")
        }

        Spacer(modifier = Modifier.width(4.dp))

        Button(
            onClick = { state.goForward() },
            enabled = state.canGoForward
        ) {
            Text("▶")
        }

        Spacer(modifier = Modifier.width(4.dp))

        Button(onClick = { if (state.isLoading) state.stopLoading() else state.reload() }) {
            Text(if (state.isLoading) "✕" else "↻")
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Enter web address...") }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(onClick = {
            val target = if (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")) {
                inputUrl
            } else {
                "https://$inputUrl"
            }
            state.loadUrl(target)
        }) {
            Text("Go")
        }

        if (state.isLoading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.width(20.dp))
        }
    }
}
```

---

## 4. Customizing the Right-Click Context Menu

By default, standard Chromium right-click menus are enabled. You can customize, filter, or completely replace them with custom actions using the declarative DSL:

```kotlin
state.setContextMenu { ctx ->
    clear() // Remove default browser items (View Source, etc.)

    if (ctx.params.isLink()) {
        copyLink("Copy Target Link")
        separator()
    }

    if (ctx.params.hasSelection()) {
        copy("Copy")
        searchWeb() // Turnkey: "Search Google for '%s'"
        separator()
    }

    item("Custom App Action") { context ->
        println("User clicked custom action on: ${context.params.pageUrl}")
    }

    subMenu("Developer Tools") {
        inspectElement() // Opens DevTools targeting the clicked coordinates
        viewSource()
    }
}
```

Or suppress right-click menus entirely for native-feeling kiosk/desktop applications:
```kotlin
state.enableContextMenus = false
```

---

## 5. WebRTC & Media Permissions

To allow web applications (e.g. Google Meet, Zoom, WebRTC video calling) to access the microphone or camera:

```kotlin
import dev.daviante.kromium.presentation.handler.KromiumPermissionDecision
import dev.daviante.kromium.presentation.handler.KromiumPermissionHandler
import dev.daviante.kromium.presentation.handler.KromiumPermissionType

state.permissionHandler = KromiumPermissionHandler { request ->
    when {
        // Whitelist trusted corporate meetings:
        request.origin == "https://meet.corp.internal" -> {
            KromiumPermissionDecision.grant(KromiumPermissionType.AUDIO_CAPTURE)
        }
        // Grant all permissions for custom local protocol:
        request.origin.startsWith("app://") -> KromiumPermissionDecision.GRANT
        // Securely deny all other sites:
        else -> KromiumPermissionDecision.DENY
    }
}

// Or turnkey origin preset:
state.permissionHandler = KromiumPermissionHandler.forOrigins("meet.google.com", "zoom.us")
```

---

## 6. Exporting to Vector PDF

To save the active page as a clean, vector PDF document:

```kotlin
import dev.daviante.kromium.domain.model.KromiumPaperSize
import dev.daviante.kromium.domain.model.KromiumPdfMargins
import dev.daviante.kromium.domain.model.KromiumPdfSettings
import java.io.File

val exportedPdf: File = state.printToPdf(
    targetFile = File("exports/page.pdf"),
    settings = KromiumPdfSettings(
        paperSize = KromiumPaperSize.A4,
        printBackground = true,
        margins = KromiumPdfMargins.fromMillimeters(10.0, 10.0, 10.0, 10.0)
    )
)
```

Or trigger the operating system's native print preview dialog:
```kotlin
state.print()
```

---

## 7. Clean Shutdown

When closing your desktop application, register a clean disposal hook:

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    Kromium.dispose()
})
```

---

## ⏭️ Next Steps

* **[Compose UI In-Depth Guide](../guides/compose-ui.md)**: Tabbed browsing, offscreen surfaces, and rendering optimization.
* **[JavaScript & DOM Guide](../guides/javascript-and-dom.md)**: Two-way IPC routing with `@JavascriptInterface` and coroutine script evaluation.
* **[Asset Filtering & Virtual Schemes](../guides/asset-filtering-and-security.md)**: Bundling single-page web apps with `app://` protocols.
