# Architecture & Process Model

Kromium bridges the world of modern desktop JVM development (Jetpack Compose Desktop, Swing, JavaFX) with the state-of-the-art **Chromium Embedded Framework (CEF)**.

Understanding Kromium's multi-process model, native bridging layer, and threading guarantees is key to building responsive, crash-resilient desktop applications.

---

## 🏛️ Multi-Process Architecture

Like Google Chrome, Kromium executes web content across multiple isolated native processes. A crash or memory leak in a single web page cannot crash your JVM application.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        JVM HOST APPLICATION PROCESS                     │
│                                                                        │
│   ┌───────────────────────────┐      ┌──────────────────────────────┐  │
│   │ Compose Desktop / Swing   │      │ Kromium Kotlin / Java API     │  │
│   │ (AWT Event Dispatch Thread)      │ (KromiumEngine, KromiumClient)│  │
│   └─────────────┬─────────────┘      └──────────────┬───────────────┘  │
│                 │                                   │                  │
│                 ▼                                   ▼                  │
│   ┌─────────────────────────────────────────────────────────────────┐  │
│   │                 JNI / JCEF Native Binding Layer                 │  │
│   │                     (libjcef.dylib / jcef.dll)                  │  │
│   └─────────────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      │ IPC (Chromium Mojo IPC Pipes)
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      NATIVE CHROMIUM PROCESSES                         │
│                                                                        │
│   ┌───────────────────────────┐      ┌──────────────────────────────┐  │
│   │  Chromium Render Process  │      │   Chromium GPU Process       │  │
│   │  (Blink HTML5, V8 JS)     │      │   (Skia, Vulkan, Metal, D3D) │  │
│   └───────────────────────────┘      └──────────────────────────────┘  │
│   ┌───────────────────────────┐      ┌──────────────────────────────┐  │
│   │  Chromium Network Process │      │   Chromium Utility Processes │  │
│   │  (BoringSSL, HTTP/3 QUIC) │      │   (Audio, Storage, Printing) │  │
│   └───────────────────────────┘      └──────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Process Roles

1. **Host Browser Process (JVM Main Process)**:
   - Manages the top-level application window, menus, life cycle, and native UI component mounting.
   - Dispatches I/O, file downloads, permission prompts, and cookie persistence.
2. **Render Process (`jcef_helper`)**:
   - Executes Blink (HTML/CSS parsing, DOM tree layout) and Google V8 (JavaScript engine).
   - Sandboxed by native OS policies (restricted disk and hardware access).
3. **GPU Process**:
   - Hardware-accelerated compositing using OS-native graphics APIs (Metal on macOS, DirectX 11/12 on Windows, Vulkan/OpenGL on Linux).
4. **Network & Utility Processes**:
   - Performs network socket operations, DNS resolution, and media decoding in isolated sandboxes.

---

## 🌉 The JCEF Native Bridging Layer

Kromium builds upon modern, optimized JCEF (Java Chromium Embedded Framework) binaries compiled for:
- **macOS**: `x86_64` (Intel) and `aarch64` (Apple Silicon M1/M2/M3/M4)
- **Windows**: `x86_64` (64-bit Intel/AMD) and `arm64` (Snapdragon X Elite / Windows on ARM)
- **Linux**: `x86_64` and `aarch64` (GLIBC 2.31+)

When `KromiumEngine.initialize()` is called:
1. Kromium unpacks or locates the native helper binaries and shared libraries (`libcef.dylib`, `libcef.so`, `chrome_elf.dll`).
2. Initializes the CEF C++ core runtime via Java Native Interface (JNI).
3. Sets up message routers for cross-process JavaScript IPC.

---

## 🖥️ Rendering Modes: Windowed vs. Off-Screen (OSR)

Kromium supports two distinct rendering pipelines to safely bridge Chromium into the JVM environment. The default mode depends on your UI framework:

### The "Heavyweight vs. Lightweight" Java Flaw
Historically, Java AWT components (like native OS window handles) are known as **Heavyweight** components, while Swing components (like `JButton`, `JTextField`) are drawn purely in Java memory as **Lightweight** components. 

Embedding a native Chromium window inside a Java Swing hierarchy creates a severe Heavyweight/Lightweight mixing conflict. Because the native OS window always renders independently, it permanently sits on top of all Swing components (causing Z-ordering bugs where dropdown menus hide behind the browser) and bypasses Java's keyboard focus manager (causing focus-stealing loops).

### Off-Screen Rendering (OSR) for Java Swing
To solve this fundamental Java architectural limitation, **Kromium defaults to OSR (`isOffScreenRendered = true`) for all pure Java/Swing integrations.**

In OSR mode, Chromium uses JOGL (Java OpenGL) to render web frames purely into an off-screen memory buffer (a Lightweight `GLJPanel`). Because there is no native OS window handle, the browser plays perfectly by Swing's rules: Z-ordering is flawless, popups render on top, and keyboard focus is strictly managed by Java.

### Windowed Mode for Compose Desktop
Jetpack Compose Desktop operates differently. It utilizes Skia for drawing and explicitly supports embedding Heavyweight components via `SwingPanel` by intelligently cutting transparent "holes" in its own canvas to let the underlying native OS window shine through. 

Because Compose gracefully handles Heavyweight clipping, **Kromium Compose explicitly defaults to Windowed mode (`isOffScreenRendered = false`)** to leverage direct hardware-accelerated zero-copy compositing for maximum performance.

| Feature | Windowed Rendering (Compose Default) | Off-Screen Rendering (Swing Default) |
|:---|:---|:---|
| **Underlying Component** | Native OS Window Handle (`HWND`, `NSView`) embedded directly. | Lightweight Java2D/OpenGL buffer (`GLJPanel`). |
| **Performance** | **Maximum 60/120+ FPS**. Direct GPU zero-copy compositing. | Excellent, but incurs a minor CPU-GPU buffer copy overhead. |
| **Focus & Z-Ordering** | Managed natively by the OS (Can break pure Swing apps). | Managed perfectly by the Java AWT Focus Manager. |
| **Compose Overlays** | Requires Compose popup windows to float above. | Allows direct semi-transparent Compose overlays. |

---

## 🧵 Threading Model & Concurrency Guarantees

Desktop applications deal with multiple asynchronous threads. Kromium strictly enforces thread safety:

### 1. The AWT Event Dispatch Thread (EDT)
All UI interactions, component mounting, and Compose recompositions happen on the Java AWT Event Dispatch Thread.
- Accessing `browser.getUIComponent()` must occur on the EDT.
- In Compose Desktop, `KromiumView` automatically schedules mounting operations on the EDT.

### 2. The Chromium UI Thread
The native Chromium core runs its own internal event loop. Kromium dispatches cross-process calls (such as `loadUrl()`, `goBack()`, and zoom level changes) safely across this boundary without freezing the Java UI.

### 3. Asynchronous Worker Threads (`CompletableFuture` / Kotlin Coroutines)
All heavy or I/O-bound operations in Kromium are non-blocking:
- `evaluateJavascriptAsync()` returns a `CompletableFuture<String>` or runs as a suspending function `evaluateJavascript()`.
- `printToPdfAsync()` generates vector PDF documents asynchronously without blocking the UI thread.
- File downloads and cookie reads stream asynchronously.

### Safe Concurrency Example (Pure Java)

```java
package com.example.architecture;

import dev.daviante.kromium.KromiumBrowser;
import dev.daviante.kromium.KromiumClient;
import dev.daviante.kromium.KromiumConfig;
import dev.daviante.kromium.KromiumEngine;
import java.awt.BorderLayout;
import java.util.concurrent.CompletableFuture;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class ThreadSafeArchitectureDemo {
    public static void main(String[] args) {
        // Step 1: Initialize the Chromium engine
        KromiumConfig config = new KromiumConfig();
        KromiumEngine.getInstance().initialize(config);

        // Step 2: Ensure UI creation happens on AWT Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            KromiumClient client = KromiumEngine.getInstance().createClient();
            KromiumBrowser browser = client.createBrowser("https://example.com");

            JFrame frame = new JFrame("Kromium Threading Architecture");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1024, 768);
            frame.setLayout(new BorderLayout());
            frame.add(browser.getUIComponent(), BorderLayout.CENTER);
            frame.setVisible(true);

            // Step 3: Execute asynchronous operation without blocking the EDT
            CompletableFuture<String> titleFuture = browser.evaluateJavascriptAsync("document.title");
            titleFuture.thenAccept(title -> {
                // Return to EDT to update UI
                SwingUtilities.invokeLater(() -> frame.setTitle("Page Title: " + title));
            }).exceptionally(ex -> {
                System.err.println("JS Evaluation failed: " + ex.getMessage());
                return null;
            });
        });
    }
}
```

---

## 🔄 Summary of Architectural Guarantees

- **Process Isolation**: Sandboxed renderer processes ensure application stability.
- **Zero Memory Leaks**: Native C++ CEF reference counts (`CefRefPtr`) are systematically decremented when `browser.close()` and `client.dispose()` are called.
- **DPI Scaling**: Automatic per-monitor high-DPI scaling across Retina macOS displays, 4K Windows fractional scaling (125%, 150%, 175%), and Linux X11/Wayland configurations.
- **Headless Capabilities**: Can run fully headless without initializing an AWT display server.
