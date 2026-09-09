# Compose UI Integration & Window Chrome

This guide covers advanced techniques for embedding Kromium in **Compose Multiplatform Desktop** applications, including responsive layouts, loading placeholders, overlaying Compose UI, and integrating custom tab strips into native OS window titlebars.

---

## 🎨 The `@Composable KromiumView`

The core entry point for Compose Desktop is `KromiumView`:

```kotlin
@Composable
fun KromiumView(
    state: KromiumViewState,
    modifier: Modifier = Modifier,
    client: KromiumClient? = null,
    loadingContent: @Composable (BoxScope.() -> Unit)? = null
)
```

### Displaying Engine Initialization Placeholders

Because Chromium engine initialization takes ~100-300ms on initial cold start, provide a smooth user experience by rendering a `loadingContent` placeholder composable:

```kotlin
package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumViewState

@Composable
fun BrowserScreen() {
    val state = rememberKromiumViewState("https://github.com")

    KromiumView(
        state = state,
        modifier = Modifier.fillMaxSize(),
        loadingContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF64B5F6))
            }
        }
    )
}
```

---

## 🪟 Custom Window Chrome & Titlebar Tab Strips

Modern desktop browsers (Chrome, Edge, Arc) integrate tabs and search bars directly into the window titlebar area instead of wasting vertical space with standard OS titlebars.

Kromium provides `KromiumWindowChrome` to achieve this cleanly on macOS and Windows:

```
┌─── macOS Titlebar Integrated Window ──────────────────────────────────────┐
│ [●][●][●]  [Tab 1: Home]  [Tab 2: Docs]  [+ ]    [https://kromium.dev 🔍] │  <-- Inset by 76pt
├───────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│                        Active Web Page Content                            │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

### Compose Desktop Chrome Integration

```kotlin
package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumViewState
import dev.daviante.kromium.presentation.chrome.KromiumChromeConfig
import dev.daviante.kromium.presentation.chrome.KromiumWindowChrome

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kromium Custom Chrome",
        undecorated = false // Keep native window frame for proper OS shadow & resizing
    ) {
        // Step 1: Configure native window properties on window open
        val chromeConfig = KromiumChromeConfig(
            enabled = true,
            macTrafficLightsWidth = 76,
            macTrafficLightsHeight = 38,
            transparentTitleBar = true,
            hideWindowTitle = true
        )
        KromiumWindowChrome.applyToWindow(window, chromeConfig)

        // Step 2: Compose Custom Titlebar Tab Strip
        val state = rememberKromiumViewState("https://github.com/daviante/kromium")

        Column(modifier = Modifier.fillMaxSize()) {
            WindowDraggableArea(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(Color(0xFF2D2D2D))
                        // Apply padding on macOS so tabs don't overlap traffic lights:
                        .padding(start = 76.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tabs & Address Bar Here", color = Color.White)
                }
            }

            // Step 3: Web Content below titlebar
            KromiumView(state = state, modifier = Modifier.fillMaxSize())
        }
    }
}
```

---

## ⌨️ Handling Keyboard Shortcuts & macOS MenuBar Integration

When building desktop applications with embedded web views, keyboard shortcuts require deliberate architectural consideration—especially on **macOS**.

### The macOS Responder Chain & MenuBar Architecture

In macOS Cocoa, Command shortcuts (`⌘C`, `⌘V`, `⌘X`, `⌘A`, `⌘Z`, `⌘W`, `⌘R`, `⌘T`) are dispatched through the application **Main Menu (`NSMenu`)** responder chain, rather than raw window key down events. If a desktop app does not declare these items in its `MenuBar`, macOS intercepts Command combinations as unhandled system actions, plays an alert beep, and drops the event before it ever reaches the embedded Chromium view.

### Why `KromiumView` Avoids Fragile Global Hooks

> [!IMPORTANT]
> **Component Isolation Principle:** `KromiumView` intentionally **does not** register global AWT `KeyEventDispatcher` hooks on the window.
>
> If a library embeds global key dispatchers, it forcibly intercepts `Ctrl+C` / `⌘C` across the entire application, breaking outside UI elements such as:
> - Compose `BasicTextField` address bars and search inputs
> - Modal dialogs, drawer text fields, and inspector inputs
> - Other third-party desktop components
>
> Instead, `KromiumView` remains pure and non-invasive:
> 1. Native key events are processed directly by Chromium when the browser component has focus.
> 2. Direct manipulation APIs (`browser.copy()`, `browser.paste()`, `browser.cut()`, `browser.selectAll()`, `browser.undo()`, `browser.redo()`) are exposed for host UI orchestration.
> 3. Global application shortcuts belong at the **Window / MenuBar** layer of your Compose application.

### Recommended Compose Desktop `MenuBar` Setup

Use Compose Multiplatform's declarative `MenuBar` API in your root `Window`. This seamlessly mounts into the macOS top screen menu bar (`NSMenu`) on macOS, and renders native window menus on Windows/Linux:

```kotlin
package com.example.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.compose.rememberKromiumViewState
import dev.daviante.kromium.presentation.browser.KromiumBrowser
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

// Helper to determine if the active AWT focus resides within the Chromium web view
fun isBrowserFocused(browser: KromiumBrowser?): Boolean {
    val comp = browser?.uiComponent ?: return false
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
    return focusOwner === comp || SwingUtilities.isDescendingFrom(focusOwner, comp)
}

fun main() = application {
    val isMac = System.getProperty("os.name", "").lowercase().contains("mac")

    Window(onCloseRequest = ::exitApplication, title = "Kromium App") {
        val state = rememberKromiumViewState("https://github.com/daviante/kromium")

        // Declarative desktop menu bar providing native macOS & cross-platform shortcuts
        MenuBar {
            Menu("File", mnemonic = 'F') {
                Item("Close Window", shortcut = KeyShortcut(Key.W, meta = isMac, ctrl = !isMac)) {
                    exitApplication()
                }
            }

            Menu("Edit", mnemonic = 'E') {
                // Focus-safe clipboard routing:
                // If the webview is focused, route to Chromium; otherwise, allow
                // Compose TextFields (e.g. address bar) to handle editing natively.
                Item("Undo", shortcut = KeyShortcut(Key.Z, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(state.browser)) state.browser?.undo()
                }
                Item("Redo", shortcut = KeyShortcut(Key.Z, meta = isMac, ctrl = !isMac, shift = true)) {
                    if (isBrowserFocused(state.browser)) state.browser?.redo()
                }
                Separator()
                Item("Cut", shortcut = KeyShortcut(Key.X, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(state.browser)) state.browser?.cut()
                }
                Item("Copy", shortcut = KeyShortcut(Key.C, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(state.browser)) state.browser?.copy()
                }
                Item("Paste", shortcut = KeyShortcut(Key.V, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(state.browser)) state.browser?.paste()
                }
                Item("Select All", shortcut = KeyShortcut(Key.A, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(state.browser)) state.browser?.selectAll()
                }
            }

            Menu("View", mnemonic = 'V') {
                Item("Reload", shortcut = KeyShortcut(Key.R, meta = isMac, ctrl = !isMac)) {
                    state.reload()
                }
                Item("Force Reload", shortcut = KeyShortcut(Key.R, meta = isMac, ctrl = !isMac, shift = true)) {
                    state.reloadIgnoreCache()
                }
                Separator()
                Item("Zoom In", shortcut = KeyShortcut(Key.Equals, meta = isMac, ctrl = !isMac)) {
                    state.zoomIn()
                }
                Item("Zoom Out", shortcut = KeyShortcut(Key.Minus, meta = isMac, ctrl = !isMac)) {
                    state.zoomOut()
                }
                Item("Actual Size", shortcut = KeyShortcut(Key.Zero, meta = isMac, ctrl = !isMac)) {
                    state.resetZoom()
                }
            }
        }

        KromiumView(state = state)
    }
}
```

---

## ☕ Pure Java / Swing Window Integration

In pure Java Swing applications, mount the browser directly inside a `JFrame` with custom titlebar styling:

```java
package com.example.ui;

import dev.daviante.kromium.KromiumBrowser;
import dev.daviante.kromium.KromiumClient;
import dev.daviante.kromium.KromiumConfig;
import dev.daviante.kromium.KromiumEngine;
import dev.daviante.kromium.presentation.chrome.KromiumChromeConfig;
import dev.daviante.kromium.presentation.chrome.KromiumWindowChrome;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class SwingWindowChromeDemo {
    public static void main(String[] args) {
        KromiumConfig config = new KromiumConfig();
        KromiumEngine.getInstance().initialize(config);

        SwingUtilities.invokeLater(() -> {
            KromiumClient client = KromiumEngine.getInstance().createClient();
            KromiumBrowser browser = client.createBrowser("https://example.com");

            JFrame frame = new JFrame("Kromium Enterprise Window");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 800);
            frame.setLayout(new BorderLayout());

            // Configure macOS traffic light insets
            KromiumChromeConfig chromeConfig = KromiumChromeConfig.builder()
                .enabled(true)
                .macTrafficLightsWidth(76)
                .macTrafficLightsHeight(38)
                .transparentTitleBar(true)
                .hideWindowTitle(true)
                .build();
            KromiumWindowChrome.applyToWindow(frame, chromeConfig);

            // Custom header component
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(new Color(0x2D, 0x2D, 0x2D));
            headerPanel.setPreferredSize(new Dimension(1200, 38));
            headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 76, 0, 10)); // Inset past traffic lights

            JLabel label = new JLabel("Enterprise Workspace Browser");
            label.setForeground(Color.WHITE);
            headerPanel.add(label, BorderLayout.WEST);

            // Assemble UI
            frame.add(headerPanel, BorderLayout.NORTH);
            frame.add(browser.getUIComponent(), BorderLayout.CENTER);
            frame.setVisible(true);
        });
    }
}
```
