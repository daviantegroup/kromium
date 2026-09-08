# Quickstart with Compose Desktop

[Documentation Hub](../README.md) &bull; **Getting Started** &bull; Compose Desktop Quickstart

---

## 🎯 Objective

This tutorial guides you through building your first Compose Multiplatform desktop application with an embedded Kromium web browser, complete with a reactive address bar, loading progress, and back/forward navigation.

---

## 1. Engine Initialization

The Kromium engine should be initialized during application startup. The `Kromium.initialize` function downloads the required JCEF runtime binaries on demand (if not already cached locally) and bootstraps the native Chromium environment.

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.daviante.kromium.presentation.browser.Kromium
import kotlinx.coroutines.launch

fun main() = application {
    val coroutineScope = rememberCoroutineScope()
    val engineState by Kromium.state.collectAsState()

    // Initialize the engine once when the application launches
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            Kromium.initialize {
                // Optional configuration:
                remoteDebuggingPort = 9222
                sandboxEnabled = true
                blockRegistryAndTelemetry = true // Suppresses Windows registry writes & telemetry
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Kromium Browser Example"
    ) {
        BrowserApp(engineState)
    }
}
```

---

## 2. Handling Initialization States

Because the Chromium engine may take a few moments to unpack or initialize on the very first run, use `Kromium.state` to provide seamless feedback to the user:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.daviante.kromium.domain.model.KromiumState

@Composable
fun BrowserApp(state: KromiumState) {
    when (state) {
        is KromiumState.Idle,
        is KromiumState.Locating,
        is KromiumState.Extracting,
        is KromiumState.Initializing -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Starting Chromium engine...")
                }
            }
        }
        is KromiumState.Downloading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = state.progress.percent?.div(100f) ?: 0f,
                        modifier = Modifier.width(280.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Downloading browser runtime: ${state.progress.percent ?: 0}%")
                }
            }
        }
        is KromiumState.Ready -> {
            // Engine is fully initialized; display browser UI
            WebBrowserScreen()
        }
        is KromiumState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Failed to initialize engine: ${state.cause.message}", color = MaterialTheme.colors.error)
            }
        }
        is KromiumState.Disposed -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Browser engine was disposed.")
            }
        }
    }
}
```

---

## 3. Embedding `KromiumView`

Once the engine is in the `Ready` state, instantiate a `rememberKromiumState` and embed `@Composable KromiumView`:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumState

@Composable
fun WebBrowserScreen() {
    val browserState = rememberKromiumState("https://github.com")
    var inputUrl by remember { mutableStateOf(browserState.url) }

    // Keep address bar synced when navigation occurs inside the web page
    LaunchedEffect(browserState.url) {
        inputUrl = browserState.url
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation Toolbar
        TopAppBar(
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 2.dp
        ) {
            IconButton(
                onClick = { browserState.goBack() },
                enabled = browserState.canGoBack
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            IconButton(
                onClick = { browserState.goForward() },
                enabled = browserState.canGoForward
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
            }

            IconButton(onClick = { browserState.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }

            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                singleLine = true,
                placeholder = { Text("Enter URL...") }
            )

            Button(
                onClick = {
                    val formatted = if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
                        "https://$inputUrl"
                    } else inputUrl
                    browserState.loadUrl(formatted)
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Go")
            }
        }

        // Loading Progress Bar
        if (browserState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // The Embedded Chromium View
        KromiumView(
            state = browserState,
            modifier = Modifier.fillMaxSize(),
            loadingContent = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        )
    }
}
```

---

## 4. Next Steps

* Learn more about reactive browser controls in the [**Compose UI Guide**](../guides/compose-ui.md).
* Explore intercepting network requests and injecting headers in the [**Network & Proxies Guide**](../guides/network-and-proxies.md).
* Execute JavaScript and inspect the DOM in the [**JavaScript & DOM Guide**](../guides/javascript-and-dom.md).
