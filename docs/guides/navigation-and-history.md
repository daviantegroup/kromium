# Navigation & History Controls

This guide explains how to control browser navigation, inspect loading state, manage back/forward history stacks, and intercept URL requests before they load.

---

## 🧭 Navigation Operations

Both `KromiumViewState` (Compose) and `KromiumBrowser` (Core JVM) expose unified navigation methods:

| Action | Compose (`KromiumViewState`) | JVM / Swing (`KromiumBrowser`) |
|:---|:---|:---|
| **Load URL** | `state.loadUrl("https://...")` | `browser.loadUrl("https://...")` |
| **Load URL & Await** | `state.loadUrl(url, waitUntil)` | `browser.loadUrl(url, waitUntil)` |
| **Wait for Navigation** | `state.waitForNavigation(stage)` | `browser.waitForNavigation(stage)` |
| **Inspect Loading State** | `state.isLoading` | `browser.isLoading` |
| **Reload** | `state.reload()` | `browser.reload()` |
| **Force Reload** | `state.reload(ignoreCache = true)` | `browser.reloadIgnoreCache()` |
| **Go Back** | `state.goBack()` | `browser.goBack()` |
| **Go Forward** | `state.goForward()` | `browser.goForward()` |
| **Stop Loading** | `state.stopLoading()` | `browser.stopLoad()` |

---

## ⏳ Navigation Lifecycle Awaiters (`NavigationStage`)

When automating page flows or running headless browser tasks, execution often needs to suspend until the page has finished loading or dynamic network traffic has settled. Kromium provides coroutine-based suspending functions and Java `CompletableFuture` lifecycle awaiters.

### `NavigationStage` Enum

| Stage | Trigger Condition | Recommended Use Case |
|:---|:---|:---|
| `NavigationStage.STARTED` | Main frame `onLoadStart` event fires. | Fast redirect detection, early cancellation, or measuring Time to First Byte. |
| `NavigationStage.LOADED` | Main frame `onLoadEnd` event fires. | **Default**: DOM and static assets are fully loaded; safe for DOM queries. |
| `NavigationStage.NETWORK_IDLE` | Main frame loaded + zero in-flight network requests for 500ms. | Single Page Applications (SPAs) with asynchronous `fetch`/XHR hydration. |

### Loading and Awaiting in Kotlin Coroutines

```kotlin
// Suspend until the page finishes loading
val success = browser.loadUrl("https://example.com/login", waitUntil = NavigationStage.LOADED, timeoutMs = 10_000L)

// Trigger a form submit or link click and wait for navigation to complete
browser.click("#submit-btn")
val navigated = browser.waitForNavigation(NavigationStage.LOADED, timeoutMs = 5_000L)

// For SPAs: await main frame load + network silence
browser.loadUrl("https://example.com/dashboard", waitUntil = NavigationStage.NETWORK_IDLE)
```

### In Pure Java (`CompletableFuture`)

```java
browser.loadUrlAsync("https://example.com/login", NavigationStage.LOADED, 10_000L)
    .thenAccept(success -> {
        if (success) {
            System.out.println("Page navigation complete!");
        }
    });

browser.waitForNavigationAsync(NavigationStage.NETWORK_IDLE, 15_000L)
    .thenAccept(idle -> System.out.println("Network is idle: " + idle));
```

---

## 🎨 Compose Desktop Navigation Toolbar

A complete Compose Desktop toolbar with reactive back/forward buttons, stop/refresh toggle, and an address input bar:

```kotlin
package com.example.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumViewState

@Composable
fun BrowserWithToolbar() {
    val state = rememberKromiumViewState("https://en.wikipedia.org")
    var addressInput by remember(state.url) { mutableStateOf(state.url) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = { state.goBack() },
                enabled = state.canGoBack
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            // Forward Button
            IconButton(
                onClick = { state.goForward() },
                enabled = state.canGoForward
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }

            // Reload / Stop Button
            IconButton(onClick = {
                if (state.isLoading) state.stopLoading() else state.reload()
            }) {
                if (state.isLoading) {
                    Icon(Icons.Default.Close, contentDescription = "Stop")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Address Bar
            OutlinedTextField(
                value = addressInput,
                onValueChange = { addressInput = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val url = if (!addressInput.startsWith("http://") && !addressInput.startsWith("https://")) {
                        "https://$addressInput"
                    } else addressInput
                    state.loadUrl(url)
                })
            )
        }

        // Loading Progress Bar
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Active Web View
        KromiumView(state = state, modifier = Modifier.fillMaxSize())
    }
}
```

---

## ☕ Pure Java / Swing Navigation Toolbar

```java
package com.example.navigation;

import dev.daviante.kromium.KromiumBrowser;
import dev.daviante.kromium.KromiumClient;
import dev.daviante.kromium.KromiumConfig;
import dev.daviante.kromium.KromiumEngine;
import dev.daviante.kromium.presentation.listener.KromiumLoadListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class SwingNavigationDemo {
    public static void main(String[] args) {
        KromiumConfig config = new KromiumConfig();
        KromiumEngine.getInstance().initialize(config);

        SwingUtilities.invokeLater(() -> {
            KromiumClient client = KromiumEngine.getInstance().createClient();
            KromiumBrowser browser = client.createBrowser("https://en.wikipedia.org");

            JFrame frame = new JFrame("Kromium Navigation Controls");
            frame.setSize(1024, 768);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            // Build toolbar
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnBack = new JButton("◀");
            JButton btnForward = new JButton("▶");
            JButton btnReload = new JButton("⟳");
            JTextField urlField = new JTextField(40);

            btnBack.setEnabled(false);
            btnForward.setEnabled(false);

            btnBack.addActionListener(e -> browser.goBack());
            btnForward.addActionListener(e -> browser.goForward());
            btnReload.addActionListener(e -> browser.reload());
            urlField.addActionListener(e -> {
                String input = urlField.getText().trim();
                if (!input.startsWith("http://") && !input.startsWith("https://")) {
                    input = "https://" + input;
                }
                browser.loadUrl(input);
            });

            toolbar.add(btnBack);
            toolbar.add(btnForward);
            toolbar.add(btnReload);
            toolbar.add(urlField);

            // Listen to browser navigation changes
            browser.addLoadListener(new KromiumLoadListener() {
                @Override
                public void onLoadingStateChange(boolean isLoading, boolean canGoBack, boolean canGoForward) {
                    SwingUtilities.invokeLater(() -> {
                        btnBack.setEnabled(canGoBack);
                        btnForward.setEnabled(canGoForward);
                        btnReload.setText(isLoading ? "✕" : "⟳");
                    });
                }

                @Override
                public void onLoadStart(String url) {
                    SwingUtilities.invokeLater(() -> urlField.setText(url));
                }

                @Override public void onLoadEnd(String url, int httpStatusCode) {}
                @Override public void onLoadError(String failedUrl, int errorCode, String errorText) {}
            });

            frame.add(toolbar, BorderLayout.NORTH);
            frame.add(browser.getUIComponent(), BorderLayout.CENTER);
            frame.setVisible(true);
        });
    }
}
```

---

## 🚫 Intercepting & Overriding Navigation

You can intercept URL navigations before Chromium begins fetching content:

```kotlin
// Compose Desktop: Cancel navigation to social media and open externally
state.shouldOverrideUrlLoading = { targetUrl ->
    if (targetUrl.contains("facebook.com") || targetUrl.contains("twitter.com")) {
        java.awt.Desktop.getDesktop().browse(java.net.URI(targetUrl))
        true // Intercepted: cancel internal load
    } else {
        false // Allow internal navigation
    }
}
```
