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

Kromium supports two distinct rendering pipelines to safely bridge Chromium into the JVM environment. The optimal mode depends on your UI framework and layout architecture:

### The "Heavyweight vs. Lightweight" Java Flaw
Historically, Java AWT components (like native OS window handles) are known as **Heavyweight** components, while Swing components (like `JButton`, `JMenu`, `JPopupMenu`) are rendered purely in Java memory as **Lightweight** components. 

Embedding a native Chromium window inside a Java Swing hierarchy creates a severe Heavyweight/Lightweight mixing conflict (the "airspace" problem). Because the native OS window always renders independently on the window server level:
1. It permanently sits on top of all Swing lightweight components, causing dropdown menus, tooltips, and modal dialogs to render invisible behind the browser window.
2. It bypasses Java's keyboard focus manager, causing focus-stealing loops and broken tab-traversal.

---

### Pure Java2D Lightweight OSR (Swing & JavaFX Default)

To permanently resolve this architectural flaw without introducing fragile native bindings, **Kromium implements a 100% Pure Java2D Off-Screen Rendering (OSR) engine (`KromiumOSRPanel`) with zero JOGL or external OpenGL dependencies.**

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ Chromium Native │ onPaint│ Direct Memory   │ Atomic│ Pure Java2D     │
│ Pixel Buffer    ├──────►│ IntBuffer Copy  ├──────►│ Double-Buffer   ├──────► Swing / JavaFX
│ (BGRA / OSR)    │       │ (Little-Endian) │ Swap  │ (BufferedImage) │        Scene Graph
└─────────────────┘       └─────────────────┘       └─────────────────┘
```

#### How the Java2D Pipeline Works:
1. **Direct Memory Mapping**: Chromium renders off-screen web frames directly into a shared native memory `ByteBuffer` in BGRA format.
2. **Zero-Overhead IntBuffer Copy**: `KromiumOSRPanel` maps the byte buffer to a little-endian `IntBuffer` and performs a high-speed CPU array copy directly into the `DataBufferInt` of a pre-allocated `BufferedImage` (`TYPE_INT_ARGB_PRE`).
3. **Double-Buffering & Atomic Swap**: To prevent screen tearing during rapid DOM animations or 60 FPS video playback, `KromiumOSRPanel` maintains separate front and back buffers, atomically swapping references inside a synchronized lock.
4. **Popup Layer Compositing**: HTML select dropdowns, autocomplete menus, and context popups are rendered via an independent `popupBuffer` and composited directly at their logical coordinates during `paintComponent()`.

Because the browser is drawn purely as a Swing `JPanel`, **Z-ordering is flawless, Swing popup menus float seamlessly over the web content, and keyboard focus is strictly governed by the standard Java AWT Focus Manager.**

---

### Dynamic HiDPI / Retina Scale Factor Detection

A classic challenge with off-screen rendering is blurry or pixelated text on fractional scaling displays (e.g. Windows 125%, 150%, or macOS Retina 200%). 

Kromium solves this via automated runtime DPI detection inside `CefBrowserOsr`:
- During each paint pass, `CefBrowserOsr` queries `Graphics2D.getTransform().getScaleX()` and the display's `GraphicsConfiguration`.
- If a DPI scale change is detected (or upon initial layout on a HiDPI screen), Kromium immediately invokes `notifyScreenInfoChanged()` and notifies Chromium via `wasResized(width, height)`.
- Chromium automatically rasterizes frames at the native physical pixel resolution, while Java2D renders the high-res buffer crisp and sharp without scaling artifacts or downsampling blur.

---

### Windowed Mode (Compose Desktop, AWT & Eclipse SWT)

For desktop toolkits that directly manage native OS window handles, **Windowed rendering (`windowlessRendering = false`)** provides direct GPU zero-copy compositing:
- **Jetpack / JetBrains Compose Desktop**: Compose operates on a Skia rendering canvas and explicitly supports heavyweight components via `SwingPanel` by cutting transparent "holes" in its canvas to allow the underlying native OS window to show through.
- **Standard AWT**: `java.awt.Frame` is a native heavyweight container that hosts the Chromium native OS window handle (`HWND` on Windows, `NSView` on macOS, X11 `Window` on Linux) with direct hardware acceleration.
- **Eclipse SWT**: SWT's native `Composite` integrates with AWT using `SWT_AWT.new_Frame(composite)`, providing direct native handle hosting with optimal performance.

---

### Architectural Toolkit Comparison

| Feature | Windowed Mode (Compose, AWT, SWT) | Pure Java2D OSR Mode (Swing, JavaFX) |
|:---|:---|:---|
| **Underlying Component** | Native OS Window Handle (`HWND`, `NSView`, X11) | Pure Java2D `JPanel` (`BufferedImage`) |
| **Dependencies** | Zero extra dependencies (Native JCEF core) | Zero extra dependencies (Pure Java Standard Library) |
| **Performance** | **Maximum 60/120+ FPS** direct GPU zero-copy | Smooth 60 FPS, ultra-efficient memory array copy |
| **Focus & Z-Ordering** | Native OS window manager | Managed by Java AWT / JavaFX Focus Managers |
| **Overlays & Popups** | Requires OS popup windows or Compose popups | Full support for lightweight Swing/JavaFX overlays |
| **HiDPI Support** | Managed automatically by OS window server | Dynamic auto-detection via `AffineTransform` |

---

## 🧵 Threading Model & Concurrency Guarantees

Desktop applications deal with multiple asynchronous threads. Kromium strictly enforces thread safety across framework boundaries:

### 1. The AWT Event Dispatch Thread (EDT)
All UI interactions, component mounting, and Swing/Compose operations happen on the Java AWT Event Dispatch Thread.
- Accessing `browser.getUiComponent()` must occur on the EDT.
- In Compose Desktop, `KromiumView` automatically schedules mounting operations on the EDT.

### 2. The Chromium UI Thread
The native Chromium core runs its own internal event loop. Kromium dispatches cross-process calls (such as `loadUrl()`, `goBack()`, and zoom level changes) safely across this boundary without freezing the Java UI.

### 3. Asynchronous Worker Threads (`CompletableFuture` / Kotlin Coroutines)
All heavy or I/O-bound operations in Kromium are non-blocking:
- `evaluateJavascript()` returns a `CompletableFuture<String>` or runs as a suspending function in Kotlin.
- `printToPdfAsync()` generates vector PDF documents asynchronously without blocking the UI thread.
- File downloads and cookie reads stream asynchronously.

### Safe Concurrency Example (Pure Java)

```java
package com.example.architecture;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;
import dev.daviante.kromium.presentation.browser.KromiumClient;

import java.awt.BorderLayout;
import java.util.concurrent.CompletableFuture;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class ThreadSafeArchitectureDemo {
    public static void main(String[] args) {
        // Step 1: Configure and initialize the Kromium engine
        KromiumConfig config = KromiumConfig.builder().build();
        Kromium.initialize(config);

        // Step 2: Ensure UI creation happens on AWT Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            KromiumClient client = Kromium.newClient();
            KromiumBrowser browser = client.createBrowser("https://adoptium.net");

            JFrame frame = new JFrame("Kromium Threading Architecture");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1024, 768);
            frame.setLayout(new BorderLayout());
            frame.add(browser.getUiComponent(), BorderLayout.CENTER);
            frame.setVisible(true);

            // Step 3: Execute asynchronous operation without blocking the EDT
            CompletableFuture<String> titleFuture = browser.evaluateJavascript("document.title");
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
