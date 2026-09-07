# Compose Multiplatform Desktop Integration

This guide details how to integrate Kromium into Compose Multiplatform Desktop applications using `KromiumView`, `rememberKromiumState`, and `KromiumViewState`.

---

## Table of Contents

1. [Quickstart](#quickstart)
2. [Component Architecture (`KromiumView`)](#component-architecture-kromiumview)
3. [State Management (`KromiumViewState`)](#state-management-kromiumviewstate)
   - [Observable Reactive Properties](#observable-reactive-properties)
   - [Navigation & Control Functions](#navigation--control-functions)
4. [Customizing Browser Behavior via State](#customizing-browser-behavior-via-state)
   - [Custom User-Agent](#custom-user-agent)
   - [Intercepting Requests](#intercepting-requests)
   - [Handling Navigation Overrides (OAuth / Deep Links)](#handling-navigation-overrides-oauth--deep-links)
   - [SSL Error Policy](#ssl-error-policy)
   - [Disabling Native Context Menus](#disabling-native-context-menus)
5. [Event Callbacks in Compose](#event-callbacks-in-compose)
   - [Download Events](#download-events)
   - [Console Messages](#console-messages)
   - [JavaScript Dialogs (Alert / Confirm / Prompt)](#javascript-dialogs-alert--confirm--prompt)
   - [HTTP Authentication Requests](#http-authentication-requests)
   - [Page Load Errors](#page-load-errors)
   - [Popups and Permissions](#popups-and-permissions)
6. [Comprehensive Example: Multi-Tab Browser UI](#comprehensive-example-multi-tab-browser-ui)

---

## Quickstart

Add the Compose module dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.daviante:kromium-compose:1.0.150")
}
```

Ensure Kromium is initialized once before rendering the view:

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumState

@Composable
fun SimpleBrowserScreen() {
    val state = rememberKromiumState(initialUrl = "https://github.com")

    KromiumView(
        state = state,
        modifier = Modifier.fillMaxSize()
    )
}
```

---

## Component Architecture (`KromiumView`)

`KromiumView` is an idiomatic Compose `@Composable` function that wraps the native CEF browser component inside an AWT `SwingPanel`:

```kotlin
@Composable
fun KromiumView(
    state: KromiumViewState,
    modifier: Modifier = Modifier,
    client: KromiumClient = remember(state) { Kromium.newClient() }
)
```

### How It Works Internally:
1. **Client Provisioning**: Automatically acquires a fresh `KromiumClient` via `Kromium.newClient()` for the given state.
2. **State Synchronization (`SideEffect`)**: On every composition, updates the `client` properties (User-Agent, request interceptor, listeners, SSL policy) to match the current `state`.
3. **Pending URL Consumption (`LaunchedEffect`)**: If `state.loadUrl()` is called before the native browser instance is initialized, the URL is buffered as a pending URL and flushed as soon as the browser surface mounts.
4. **CEF Event Bridges (`DisposableEffect`)**:
   - Registers a `CefLoadHandler` to update `state.isLoading`, `state.canGoBack`, and `state.canGoForward`.
   - Registers a `CefDisplayHandler` to update `state.url` and `state.title`.
   - Cleans up on disposal: shuts down the browser and disposes the native `CefClient`.
5. **AWT Rendering Surface**: Bridges CEF's native rendering component (`CefBrowserWr`) directly into Compose using a Swing `JPanel(BorderLayout())` container inside `SwingPanel`, utilizing native windowed HWND/NSView/X11 rendering for 60+ FPS hardware-accelerated Direct3D / Metal / OpenGL output without requiring external OpenGL/JOGL dependencies.

---

## State Management (`KromiumViewState`)

State is retained and remembered using:
```kotlin
val state = rememberKromiumState(key = "tab-1", initialUrl = "https://example.com")
```

### Observable Reactive Properties

All of the following properties are backed by Compose `mutableStateOf` and trigger recomposition when updated:

| Property | Type | Access | Description |
|---|---|---|---|
| `url` | `String` | Read-only | Current page URL. Automatically tracks redirects and address bar changes. |
| `title` | `String` | Read-only | Current page title (`<title>`). |
| `isLoading` | `Boolean` | Read-only | `true` while page resources are actively loading; `false` on `onLoadEnd`. |
| `canGoBack` | `Boolean` | Read-only | `true` if browser history allows navigating back. |
| `canGoForward` | `Boolean` | Read-only | `true` if browser history allows navigating forward. |
| `browser` | `KromiumBrowser?` | Read-only | Direct reference to the active underlying `KromiumBrowser` instance. |

### Navigation & Control Functions

| Function | Signature | Description |
|---|---|---|
| `loadUrl` | `loadUrl(newUrl: String)` | Navigates to a new web address. Safe to call before the view is mounted. |
| `loadHtml` | `loadHtml(html: String, baseUrl: String = "about:blank")` | Renders an in-memory HTML string via Kromium's custom resource handler. |
| `reload` | `reload(ignoreCache: Boolean = false)` | Reloads current page. Set `ignoreCache = true` for hard reload. |
| `stopLoading` | `stopLoading()` | Stops ongoing page network requests. |
| `goBack` | `goBack()` | Navigates back in history if `canGoBack == true`. |
| `goForward` | `goForward()` | Navigates forward in history if `canGoForward == true`. |
| `setZoom` | `setZoom(level: Double)` | Sets page zoom factor (`0.0` = 100%, `1.0` = ~120%, `-1.0` = ~80%). |
| `getZoom` | `getZoom(): Double` | Returns the current zoom level. |
| `openDevTools` | `openDevTools()` | Opens the Chromium Developer Tools inspection window. |
| `closeDevTools`| `closeDevTools()` | Closes DevTools if open. |
| `simulateClick`| `simulateClick(x: Int, y: Int)` | Dispatches mouse press/release/click events at coordinates `(x, y)`. |
| `evaluateJavaScript` | `suspend evaluateJavaScript(script: String): String?` | Executes JavaScript asynchronously and returns the result. |
| `getHtml` | `suspend getHtml(): String` | Extracts full document HTML (`outerHTML`). |
| `getText` | `suspend getText(): String` | Extracts document visible text (`innerText`). |

---

## Customizing Browser Behavior via State

Configure behavior directly on the `KromiumViewState` object:

### Custom User-Agent
```kotlin
state.userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
```

### Intercepting Requests
```kotlin
state.requestInterceptor = KromiumRequestInterceptor { request ->
    if (request.url.contains("google-analytics.com") || request.url.contains("doubleclick.net")) {
        return@KromiumRequestInterceptor true // Block analytics/ad trackers
    }
    // Inject auth token or custom headers
    request.headers["X-Application-Client"] = "ComposeDesktop"
    false // Proceed
}
```

### Handling Navigation Overrides (OAuth / Deep Links)
```kotlin
state.shouldOverrideUrlLoading = { url ->
    if (url.startsWith("myapp://auth/callback")) {
        val token = url.substringAfter("token=")
        println("OAuth Callback Token: $token")
        true // Intercept and cancel browser navigation
    } else {
        false // Allow normal navigation
    }
}
```

### SSL Error Policy
```kotlin
// Production default (strict rejection)
state.sslErrorPolicy = SslErrorPolicy.Strict

// Or allow self-signed certificates for local development domains:
state.sslErrorPolicy = SslErrorPolicy.AllowDomains("localhost", "127.0.0.1", "dev.internal")
```

### Disabling Native Context Menus
```kotlin
// Disables the default Chromium right-click context menu
state.enableContextMenus = false
```

---

## Event Callbacks in Compose

### Download Events
```kotlin
state.onDownload = { item ->
    println("File: ${item.suggestedFileName} | Progress: ${item.percentComplete}% | Speed: ${item.speed / 1024} KB/s")
    if (item.isComplete) {
        println("Download finished: ${item.suggestedFileName}")
    }
}
```

### Console Messages
```kotlin
state.onConsoleMessage = { message ->
    println("[${message.level}] ${message.source}:${message.line} -> ${message.message}")
}
```

### JavaScript Dialogs (Alert / Confirm / Prompt)
```kotlin
state.onJsDialog = { dialog ->
    when (dialog.type) {
        KromiumJsDialogType.ALERT -> {
            showComposeAlert(dialog.message) { dialog.confirm() }
            true // We handled the dialog
        }
        KromiumJsDialogType.CONFIRM -> {
            showComposeConfirm(dialog.message, onYes = { dialog.confirm() }, onNo = { dialog.cancel() })
            true
        }
        KromiumJsDialogType.PROMPT -> {
            showComposePrompt(dialog.message, default = dialog.defaultPromptText) { input ->
                dialog.confirm(input)
            }
            true
        }
    }
}
```

### HTTP Authentication Requests
```kotlin
state.onAuthRequired = { req ->
    if (req.host == "secure.internal") {
        KromiumAuthResponse.Proceed(username = "admin", password = "secretPassword")
    } else {
        KromiumAuthResponse.Cancel
    }
}
```

### Page Load Errors
```kotlin
state.onLoadError = { error ->
    println("Error ${error.errorCode} loading ${error.failedUrl}: ${error.errorText}")
}
```

### Popups and Permissions
```kotlin
// Block popups or handle them within your tab system
state.onPopup = { url ->
    openNewTab(url)
    true // Consumed popup
}

// Intercept geolocation / camera / microphone permission prompts
state.onPermissionRequest = { url ->
    url.startsWith("https://trusted-domain.com")
}
```

---

## Comprehensive Example: Multi-Tab Browser UI

Here is a full Compose Desktop browser window featuring a navigation bar, loading indicator, title tracking, address bar, zoom controls, DevTools button, and DOM inspection:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumState
import kotlinx.coroutines.launch

@Composable
fun FullBrowserApp() {
    val state = rememberKromiumState(initialUrl = "https://kotlinlang.org")
    var urlInput by remember { mutableStateOf("https://kotlinlang.org") }
    val scope = rememberCoroutineScope()

    // Keep the address bar input synchronized with page navigation
    LaunchedEffect(state.url) {
        if (state.url.isNotBlank() && state.url != "about:blank") {
            urlInput = state.url
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Navigation Toolbar
        Surface(shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back Button
                IconButton(onClick = { state.goBack() }, enabled = state.canGoBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                // Forward Button
                IconButton(onClick = { state.goForward() }, enabled = state.canGoForward) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                }

                // Reload Button
                IconButton(onClick = { state.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }

                // Address Bar
                TextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(if (state.title.isNotBlank()) state.title else "Address") }
                )

                Button(onClick = { state.loadUrl(urlInput) }) {
                    Text("Go")
                }

                // DevTools
                IconButton(onClick = { state.openDevTools() }) {
                    Icon(Icons.Default.Build, contentDescription = "DevTools")
                }

                // Extract Page Title via JavaScript
                Button(onClick = {
                    scope.launch {
                        val title = state.evaluateJavaScript("document.title")
                        println("Extracted title via JS: $title")
                    }
                }) {
                    Text("JS Title")
                }
            }
        }

        // 2. Loading Progress Indicator
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // 3. Web View Surface
        KromiumView(
            state = state,
            modifier = Modifier.fillMaxSize()
        )
    }
}
```
