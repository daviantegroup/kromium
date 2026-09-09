# Universal Java Desktop Quickstart (Swing, AWT, Eclipse SWT, JavaFX)

This guide shows you how to integrate **Kromium** into any standard **100% Pure Java** desktop application across all major JVM graphical toolkits: **Java Swing**, **Standard AWT**, **Eclipse SWT**, and **JavaFX**.

All JVM integrations utilize pure Java 8+ idioms (`java.util.concurrent.CompletableFuture`, JavaBeans properties, SAM functional interfaces, fluent builders) with **zero Kotlin runtime dependencies** required in your application code.

---

## 🖥️ 1. Java Swing Integration (Lightweight OSR)

For modern Swing and FlatLaf applications, Kromium defaults to **Off-Screen Rendering (OSR)** using the built-in `KromiumOSRPanel`. This completely avoids native "airspace" conflicts: dropdown menus, dialogs, and tooltips render seamlessly on top of web content.

```java
package com.example.browser;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;
import dev.daviante.kromium.presentation.browser.KromiumClient;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

public class SwingApp {
    public static void main(String[] args) {
        // 1. Initialize Kromium engine (OSR enabled by default for Swing)
        KromiumConfig config = KromiumConfig.builder()
                .userAgent("MySwingBrowser/1.0")
                .build();
        Kromium.initialize(config);

        // 2. Register clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(Kromium::dispose));

        // 3. Create client session and browser instance
        KromiumClient client = Kromium.newClient();
        KromiumBrowser browser = client.createBrowser("https://adoptium.net");

        // 4. Mount into a standard Swing JFrame
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kromium Swing Browser");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 800);
            frame.setLocationRelativeTo(null);

            // browser.getUiComponent() returns a lightweight JPanel in OSR mode
            frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(browser.getUiComponent(), BorderLayout.CENTER);

            frame.setVisible(true);
        });
    }
}
```

*Sample module reference: [`:kromium-sample-swing`](https://github.com/daviantegroup/kromium/tree/main/kromium-sample-swing)*

---

## 🪟 2. Standard AWT Integration (Heavyweight Windowed)

When building legacy or lightweight AWT desktop applications using native `java.awt.Frame` and `java.awt.Panel`, configure Kromium for **Windowed rendering (`windowlessRendering = false`)** to mount the native OS Chromium window directly into the AWT container.

```java
package com.example.browser;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;

public class AwtApp {
    public static void main(String[] args) {
        // 1. Create Frame and Toolbar
        Frame frame = new Frame("Kromium AWT Browser");
        frame.setLayout(new BorderLayout());
        frame.setSize(1200, 800);
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Kromium.dispose();
                frame.dispose();
                System.exit(0);
            }
        });

        Panel toolbar = new Panel(new FlowLayout(FlowLayout.LEFT));
        Button reloadBtn = new Button("Reload");
        TextField addressBar = new TextField("https://adoptium.net", 60);
        toolbar.add(reloadBtn);
        toolbar.add(addressBar);
        frame.add(toolbar, BorderLayout.NORTH);
        frame.setVisible(true);

        // 2. Configure Kromium for Heavyweight Windowed rendering
        KromiumConfig config = KromiumConfig.builder()
                .windowlessRendering(false)
                .build();

        // 3. Asynchronously initialize and attach browser component
        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> {
                    KromiumBrowser browser = client.createBrowser("https://adoptium.net", false, false);

                    reloadBtn.addActionListener(e -> browser.reload());
                    addressBar.addActionListener(e -> browser.loadUrl(addressBar.getText()));
                    browser.onAddressChanged(url -> SwingUtilities.invokeLater(() -> addressBar.setText(url)));

                    SwingUtilities.invokeLater(() -> {
                        frame.add(browser.getUiComponent(), BorderLayout.CENTER);
                        frame.validate();
                    });
                });
    }
}
```

*Sample module reference: [`:kromium-sample-awt`](https://github.com/daviantegroup/kromium/tree/main/kromium-sample-awt)*

---

## ⚡ 3. Eclipse SWT Integration (Bridged AWT Composite)

Enterprise applications based on **Eclipse RCP** or standalone **Eclipse SWT** can embed Kromium by bridging an SWT `Composite` to AWT using standard `SWT_AWT.new_Frame(composite)`. Configure **Windowed mode (`windowlessRendering = false`)** for zero-copy native performance.

```java
package com.example.browser;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Composite;

import java.awt.BorderLayout;

public class SwtApp {
    public static void main(String[] args) {
        // 1. Initialize SWT Display and Shell
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("Kromium SWT Browser");
        shell.setSize(1200, 800);
        shell.setLayout(new GridLayout(1, false));

        // 2. Create Embedded AWT Composite
        Composite composite = new Composite(shell, SWT.EMBEDDED | SWT.NO_BACKGROUND);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        java.awt.Frame frame = SWT_AWT.new_Frame(composite);
        frame.setLayout(new BorderLayout());

        // 3. Configure Kromium for Windowed Heavyweight rendering
        KromiumConfig config = KromiumConfig.builder()
                .windowlessRendering(false)
                .build();

        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> {
                    KromiumBrowser browser = client.createBrowser("https://adoptium.net", false, false);

                    display.asyncExec(() -> {
                        if (shell.isDisposed()) return;
                        frame.add(browser.getUiComponent(), BorderLayout.CENTER);
                        frame.revalidate();
                        frame.repaint();
                    });
                });

        shell.open();

        // 4. Standard SWT Event Dispatch Loop on the main thread
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        display.dispose();
        Kromium.dispose();
        System.exit(0);
    }
}
```

*Sample module reference: [`:kromium-sample-swt`](https://github.com/daviantegroup/kromium/tree/main/kromium-sample-swt)*

---

## 🎨 4. JavaFX Integration (Lightweight OSR via SwingNode)

JavaFX applications embed Kromium using `javafx.embed.swing.SwingNode` backed by **Lightweight OSR (`windowlessRendering = true`)**. This ensures automatic HiDPI scaling, seamless scene resize synchronization, and proper thread safety between the JavaFX Application Thread and the AWT EDT.

```java
package com.example.browser;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;

public class JavaFxApp extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        SwingNode swingNode = new SwingNode();
        root.setCenter(swingNode);

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("Kromium JavaFX Browser");
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(e -> {
            Kromium.dispose();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();

        // 1. Force Lightweight OSR mode for JavaFX embedding
        KromiumConfig config = KromiumConfig.builder()
                .windowlessRendering(true)
                .build();

        // 2. Initialize engine and attach component
        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> {
                    KromiumBrowser browser = client.createBrowser("https://adoptium.net", true, true);
                    JComponent uiComp = (JComponent) browser.getUiComponent();

                    // CRITICAL: swingNode.setContent must be called on JavaFX Application Thread
                    Platform.runLater(() -> {
                        swingNode.setContent(uiComp);

                        // Synchronize JavaFX scene resize events to Chromium viewport
                        scene.widthProperty().addListener((obs, oldW, newW) -> updateSize(browser, uiComp, scene));
                        scene.heightProperty().addListener((obs, oldH, newH) -> updateSize(browser, uiComp, scene));
                    });

                    // Trigger initial sizing and native creation on Swing EDT
                    SwingUtilities.invokeLater(() -> {
                        int w = (int) scene.getWidth();
                        int h = (int) scene.getHeight();
                        uiComp.setPreferredSize(new Dimension(w, h));
                        uiComp.setSize(w, h);
                        browser.getRawBrowser().createImmediately();
                        browser.getRawBrowser().wasResized(w, h);
                    });
                });
    }

    private static void updateSize(KromiumBrowser browser, JComponent uiComp, Scene scene) {
        int w = (int) scene.getWidth();
        int h = (int) scene.getHeight();
        if (w > 50 && h > 50) {
            SwingUtilities.invokeLater(() -> {
                uiComp.setPreferredSize(new Dimension(w, h));
                uiComp.setSize(w, h);
                browser.getRawBrowser().wasResized(w, h);
            });
        }
    }
}
```

> [!TIP]
> **JavaFX Main Class Launcher Pattern**: When running modular JavaFX on the standard classpath without `--module-path` flags, create a standalone launcher class (e.g. `public class Launcher { public static void main(String[] args) { JavaFxApp.main(args); } }`) to bypass JavaFX runtime initialization checks.

*Sample module reference: [`:kromium-sample-javafx`](https://github.com/daviantegroup/kromium/tree/main/kromium-sample-javafx)*

---

## 🌐 5. Common Browser Controls & APIs (Universal)

The following core APIs and callbacks work identically across all four desktop frameworks (Swing, AWT, SWT, JavaFX):

### 5.1 Navigation & Lifecycle

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

### 5.2 Listening to Loading & Console Events

Attach event listeners using Java 8+ functional interfaces:

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

### 5.3 Customizing Right-Click Context Menus

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

### 5.4 WebRTC & Media Permissions

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

### 5.5 Asynchronous Vector PDF Export

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

### 5.6 Two-Way JavaScript Communication

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

### 5.7 Dynamic Proxy Switching

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

## 📊 6. Toolkit Decision & Architecture Comparison Guide

Choosing between Windowed and Off-Screen Rendering depends on your framework's graphics compositing architecture:

| UI Toolkit | Recommended Mode | Container Component | Why this mode? |
|:---|:---|:---|:---|
| **Java Swing / FlatLaf** | **Lightweight OSR** (`windowlessRendering = true`) | `JFrame`, `JPanel` | Prevents the classic Heavyweight "airspace" problem where native windows render on top of Swing menus, dialogs, and popups. |
| **Standard AWT** | **Windowed Heavyweight** (`windowlessRendering = false`) | `java.awt.Frame`, `Panel` | AWT frames are native OS windows; hosting the Chromium window handle directly provides optimal hardware acceleration without buffer copies. |
| **Eclipse SWT** | **Windowed Heavyweight** (`windowlessRendering = false`) | `SWT_AWT.new_Frame(composite)` | SWT utilizes native OS controls; bridging through `SWT_AWT` provides native window parenting with zero-copy GPU performance. |
| **JavaFX** | **Lightweight OSR** (`windowlessRendering = true`) | `javafx.embed.swing.SwingNode` | JavaFX's Prism renderer manages its own rendering thread; embedding a pure Java2D OSR panel ensures clean scene composition and automatic HiDPI scaling. |
| **Compose Multiplatform** | **Windowed (GPU)** (`windowlessRendering = false`) | `@Composable KromiumView` | Compose's Skia engine performs transparent canvas hole-punching for maximum 60/120+ FPS hardware acceleration. |

---

## ⏭️ Next Steps

* **[Architecture Guide](../core-concepts/architecture.md)**: Deep dive into the multi-process Chromium architecture, Pure Java2D OSR pipeline, and bootstrapping.
* **[Browser & Client API Reference](../reference/browser-and-client-api.md)**: Complete catalog of `KromiumClient` and `KromiumBrowser` methods.
* **[Packaging & Distribution](../deployment/packaging-and-distribution.md)**: Build installers (MSI, DMG, DEB) for Windows, macOS, and Linux.
