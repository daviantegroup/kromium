# State & Lifecycle Management

Managing state and native resource lifecycles properly is critical in desktop applications. Because Kromium coordinates between the JVM garbage collector and native C++ Chromium processes, understanding when and how instances are created, observed, and disposed prevents memory leaks and zombie processes.

---

## 🔄 The Engine & Browser Lifecycle

Kromium follows a hierarchical lifecycle:

```
┌────────────────────────────────────────────────────────┐
│ 1. KromiumEngine.getInstance().initialize(config)      │  (Application Startup / Once per JVM)
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 2. KromiumClient client = engine.createClient()        │  (Window / Workspace Scope)
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 3. KromiumBrowser browser = client.createBrowser(url)  │  (Tab / View Scope)
└───────────────────────────┬────────────────────────────┘
                            │
                     Active Browsing
              (Navigation, Events, Rendering)
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 4. browser.close(true)                                 │  (Tab Close / UI Disposal)
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 5. client.dispose()                                    │  (Window Close)
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ 6. KromiumEngine.getInstance().dispose()               │  (Application Exit / Process Teardown)
└────────────────────────────────────────────────────────┘
```

---

## 🎨 Compose Desktop Lifecycle (`rememberKromiumViewState`)

In Jetpack Compose Desktop, `KromiumViewState` is lifecycle-aware and integrates seamlessly with Compose's composition tree.

### Reactive State Properties

`KromiumViewState` exposes Compose `State<T>` properties that trigger automatic, efficient recompositions only when their values change:

| Property | Type | Description |
|:---|:---|:---|
| `url` | `String` | The current active URL (updates on navigation and redirects). |
| `title` | `String` | The active document `<title>` (empty until DOM parses title). |
| `isLoading` | `Boolean` | `true` while the page or subresources are loading; `false` when idle. |
| `canGoBack` | `Boolean` | `true` if back history entries exist. |
| `canGoForward` | `Boolean` | `true` if forward history entries exist. |

### Composition Disposal

When a `@Composable KromiumView` leaves the composition tree (for example, when a user switches tabs or closes a dialog):
1. `DisposableEffect` fires automatically.
2. The native Chromium browser instance is closed safely.
3. Native window handles are freed.

```kotlin
package com.example.lifecycle

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumViewState

@Composable
fun TabContainer() {
    var isBrowserTabOpen by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isBrowserTabOpen) {
            // When isBrowserTabOpen becomes false, KromiumView automatically
            // cleans up native browser resources upon leaving composition.
            val state = rememberKromiumViewState("https://example.com")
            KromiumView(state = state, modifier = Modifier.fillMaxSize())
        }

        Button(onClick = { isBrowserTabOpen = !isBrowserTabOpen }) {
            Text(if (isBrowserTabOpen) "Close Tab" else "Open Tab")
        }
    }
}
```

---

## ☕ Pure Java / Swing Lifecycle Management

When using Kromium in Pure Java or Swing applications, lifecycle management is explicit and straightforward.

### Handling Window Closing

Always hook into `WindowListener.windowClosing` or `JFrame.addWindowListener` to ensure native CEF resources are properly torn down before the JVM terminates:

```java
package com.example.lifecycle;

import dev.daviante.kromium.KromiumBrowser;
import dev.daviante.kromium.KromiumClient;
import dev.daviante.kromium.KromiumConfig;
import dev.daviante.kromium.KromiumEngine;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class SwingLifecycleDemo {
    public static void main(String[] args) {
        // 1. Initialize Engine
        KromiumConfig config = new KromiumConfig();
        KromiumEngine.getInstance().initialize(config);

        SwingUtilities.invokeLater(() -> {
            KromiumClient client = KromiumEngine.getInstance().createClient();
            KromiumBrowser browser = client.createBrowser("https://example.com");

            JFrame frame = new JFrame("Kromium Swing Lifecycle");
            frame.setSize(1024, 768);
            frame.setLayout(new BorderLayout());
            frame.add(browser.getUIComponent(), BorderLayout.CENTER);

            // 2. Register explicit teardown listener
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.out.println("Tearing down native Chromium instances...");
                    
                    // Close the browser view
                    browser.close(true);
                    
                    // Dispose the client context
                    client.dispose();
                    
                    // Shut down the engine on final application window exit
                    KromiumEngine.getInstance().dispose();
                    
                    frame.dispose();
                    System.exit(0);
                }
            });

            frame.setVisible(true);
        });
    }
}
```

---

## 🧭 Navigation State Machine

A web page transition moves through well-defined lifecycle states:

```
[IDLE] ─── loadUrl() ───► [NAVIGATING] (isLoading = true)
                                │
                                ├─► [REDIRECT] (URL changes, onAddressChange)
                                │
                                ├─► [DOM READY] (Title updates, canGoBack updates)
                                │
                                ├─► [LOAD ERROR] (onLoadError, net errors like ERR_NAME_NOT_RESOLVED)
                                │
                                ▼
                       [PAGE COMPLETE] (isLoading = false)
```

### Observing Navigation Events in Pure Java

In pure Java, attach a `KromiumLoadListener` to track transitions:

```java
browser.addLoadListener(new dev.daviante.kromium.presentation.listener.KromiumLoadListener() {
    @Override
    public void onLoadingStateChange(boolean isLoading, boolean canGoBack, boolean canGoForward) {
        System.out.printf("Loading: %s | Back: %s | Forward: %s%n", isLoading, canGoBack, canGoForward);
    }

    @Override
    public void onLoadStart(String url) {
        System.out.println("Page load started: " + url);
    }

    @Override
    public void onLoadEnd(String url, int httpStatusCode) {
        System.out.printf("Page load ended: %s (Status: %d)%n", url, httpStatusCode);
    }

    @Override
    public void onLoadError(String failedUrl, int errorCode, String errorText) {
        System.err.printf("Load error on %s [%d]: %s%n", failedUrl, errorCode, errorText);
    }
});
```

---

## 🛑 Application Shutdown Hook

To guarantee that CEF background helper processes are never left orphaned if the user terminates the JVM via `Ctrl+C` or `kill`, you can register a standard JVM shutdown hook:

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    try {
        KromiumEngine.getInstance().dispose()
    } catch (e: Exception) {
        System.err.println("Error shutting down Kromium engine: ${e.message}")
    }
})
```
