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
