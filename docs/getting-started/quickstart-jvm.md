# Pure Java & Swing Quickstart

This guide shows you how to integrate **Kromium** into a standard **100% Pure Java** desktop application using Swing (or FlatLaf) with zero Kotlin runtime dependencies.

---

## 1. Minimal Working Java Example

Create a file `Main.java` in your Java project:

```java
package com.example.browser;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;
import dev.daviante.kromium.presentation.browser.KromiumClient;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

public class Main {
    public static void main(String[] args) {
        // 1. Initialize Kromium engine
        KromiumConfig config = KromiumConfig.builder()
                .userAgent("MyJavaBrowser/1.0")
                .build();
        Kromium.initialize(config);

        // 2. Register clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(Kromium::dispose));

        // 3. Create client session and browser instance
        KromiumClient client = Kromium.newClient();
        KromiumBrowser browser = client.createBrowser("https://github.com/daviante/kromium");

        // 4. Mount into a standard Swing JFrame
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kromium Java Browser");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 800);
            frame.setLocationRelativeTo(null);

            // browser.getUiComponent() returns a standard java.awt.Component
            frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(browser.getUiComponent(), BorderLayout.CENTER);

            frame.setVisible(true);
        });
    }
}
```

---

## 2. Browser Navigation & Lifecycle in Java

`KromiumBrowser` provides standard navigation controls:

```java
// Navigate to a URL:
browser.loadUrl("https://adoptium.net");

// History navigation:
if (browser.canGoBack()) browser.goBack();
if (browser.canGoForward()) browser.goForward();

// Reloading:
browser.reload();
browser.reloadIgnoreCache();
browser.stop();

// Zoom controls:
browser.setZoomLevel(1.5); // 150% zoom
double currentZoom = browser.getZoomLevel();

// DevTools:
browser.openDevTools();
browser.closeDevTools();
```

---

## 3. Listening to Loading & Console Events

You can attach event listeners using Java 8+ functional interfaces:

```java
// Listen to loading state changes:
client.setLoadingListener((isLoading, canGoBack, canGoForward) -> {
    System.out.println("Loading: " + isLoading + ", Back: " + canGoBack);
});

// Capture JavaScript console logs:
client.setConsoleMessageListener(msg -> {
    System.out.println("[" + msg.getLevel() + "] " + msg.getMessage() + " (" + msg.getSource() + ":" + msg.getLine() + ")");
});

// Handle load errors (DNS failure, connection refused):
client.setLoadErrorListener(err -> {
    System.err.println("Load error: " + err.getErrorText() + " for " + err.getFailedUrl());
});
```

---

## 4. Customizing Right-Click Context Menus

Use the fluent Java builder to add custom menu actions or turnkey shortcuts without manual integer command ID bookkeeping:

```java
import dev.daviante.kromium.presentation.menu.KromiumContextMenuHandler;

client.setContextMenuHandler((builder, ctx) -> {
    // Remove default Chromium items (View Source, etc.):
    builder.clearDefaults();

    // Add hyperlink actions if clicked on a link:
    if (ctx.getParams().isLink()) {
        builder.copyLink("Copy Target Link");
        builder.addSeparator();
    }

    // Add selection actions if text is highlighted:
    if (ctx.getParams().hasSelection()) {
        builder.copy("Copy");
        builder.searchWeb(); // Turnkey: "Search Google for '%s'"
        builder.addSeparator();
    }

    // Add a custom Java application action:
    builder.addItem("Custom Action", context -> {
        System.out.println("User triggered action on: " + context.getParams().getPageUrl());
    });

    // Add nested developer submenu:
    builder.addSubMenu("Developer Tools", sub -> {
        sub.inspectElement(); // Inspect element at clicked coordinates
        sub.viewSource();
    });
});

// Turnkey presets:
client.setContextMenuHandler(KromiumContextMenuHandler.minimalEditing(true));
client.setContextMenuHandler(KromiumContextMenuHandler.devToolsOnly());
```

---

## 5. WebRTC & Media Permissions

Intercept media access requests (Microphone, Camera, Screen Sharing) using Java lambdas:

```java
import dev.daviante.kromium.presentation.handler.KromiumPermissionDecision;
import dev.daviante.kromium.presentation.handler.KromiumPermissionHandler;
import dev.daviante.kromium.presentation.handler.KromiumPermissionType;

client.setPermissionHandler(request -> {
    // Whitelist specific corporate video conference domain:
    if ("https://meet.company.com".equals(request.getOrigin())) {
        return KromiumPermissionDecision.GRANT;
    }

    // Allow microphone only for internal audio tools:
    if ("https://audio.internal".equals(request.getOrigin())) {
        return KromiumPermissionDecision.grant(KromiumPermissionType.AUDIO_CAPTURE);
    }

    // Securely deny all untrusted origins:
    return KromiumPermissionDecision.DENY;
});

// Or turnkey domain whitelist preset:
client.setPermissionHandler(KromiumPermissionHandler.forOrigins("meet.google.com", "zoom.us"));

// Revoke remembered permissions at any time:
client.clearPermissionCache();
```

---

## 6. Asynchronous Vector PDF Export

Export web pages to PDF asynchronously using standard Java `CompletableFuture`:

```java
import dev.daviante.kromium.domain.model.KromiumPaperSize;
import dev.daviante.kromium.domain.model.KromiumPdfMargins;
import dev.daviante.kromium.domain.model.KromiumPdfSettings;
import java.io.File;

KromiumPdfSettings settings = KromiumPdfSettings.builder()
    .paperSize(KromiumPaperSize.A4)
    .printBackground(true)
    .margins(KromiumPdfMargins.fromMillimeters(10.0, 10.0, 10.0, 10.0))
    .displayHeaderFooter(true)
    .headerTemplate("<span class='title'></span>")
    .footerTemplate("<span class='pageNumber'></span> of <span class='totalPages'></span>")
    .build();

browser.printToPdfAsync(new File("exports/report.pdf"), settings)
    .thenAccept(file -> System.out.println("Generated PDF at: " + file.getAbsolutePath()))
    .exceptionally(ex -> {
        System.err.println("Printing failed: " + ex.getMessage());
        return null;
    });

// Or open the native OS print preview dialog:
browser.print();
```

---

## 7. Two-Way JavaScript Communication

Evaluate JavaScript or bind native Java objects to JavaScript's `window` object:

```java
import dev.daviante.kromium.presentation.js.JavascriptInterface;

// 1. Evaluate JavaScript returning CompletableFuture<String>:
browser.evaluateJavascript("document.title")
    .thenAccept(title -> System.out.println("Page title: " + title));

// 2. Register native Java bridge object:
public class NativeBridge {
    @JavascriptInterface
    public void notify(String message) {
        System.out.println("Received message from web page: " + message);
    }
}

browser.registerJsInterface(new NativeBridge(), "desktopApp");
// In web page: window.desktopApp.notify("Hello from JS!");
```

---

## 8. Dynamic Proxy Switching

Change network proxies at runtime without restarting the browser engine:

```java
import dev.daviante.kromium.domain.config.KromiumProxy;

// Direct connection (no proxy):
client.updateProxy(KromiumProxy.direct());

// HTTP/HTTPS proxy:
client.updateProxy(KromiumProxy.http("proxy.corp.internal", 8080));

// SOCKS5 proxy with remote DNS leak protection:
client.updateProxy(KromiumProxy.socks5("127.0.0.1", 1080));
```

---

## ⏭️ Next Steps

* **[Architecture Guide](../core-concepts/architecture.md)**: Deep dive into the multi-process Chromium architecture and bootstrapping.
* **[Browser & Client API Reference](../reference/browser-and-client-api.md)**: Complete catalog of `KromiumClient` and `KromiumBrowser` methods.
* **[Packaging & Distribution](../deployment/packaging-and-distribution.md)**: Build installers (MSI, DMG, DEB) for Windows, macOS, and Linux.
