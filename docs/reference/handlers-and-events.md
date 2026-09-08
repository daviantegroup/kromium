# Handlers & Event Listeners

Kromium uses strongly-typed, Java SAM (Single Abstract Method) compatible interfaces for event handling. This allows both idiomatic Kotlin DSLs and clean Java 8+ lambdas.

---

## 🖱️ Context Menu Customization

Package: `dev.daviante.kromium.presentation.menu`

`KromiumContextMenuHandler` intercepts right-click mouse events on the web page to modify, remove, or replace the browser context menu.

### Interface Definition

```kotlin
fun interface KromiumContextMenuHandler {
    fun onBeforeContextMenu(builder: KromiumMenuBuilder, context: KromiumContextMenuContext)
}
```

### Context Information (`KromiumContextMenuContext`)

Inspect what the user right-clicked:
- `linkUrl: String?`: Non-empty if clicking a hyperlink.
- `srcUrl: String?`: Non-empty if clicking an image, video, or audio element.
- `selectionText: String?`: Non-empty if text is currently highlighted.
- `isEditable: Boolean`: `true` if inside an `<input>` or `<textarea>`.
- `x: Int`, `y: Int`: Click coordinates.
- `browser: KromiumBrowser`: The originating browser instance.

### Declarative Kotlin DSL (Compose Desktop)

```kotlin
val state = rememberKromiumViewState("https://example.com")

state.setContextMenu { ctx ->
    clear() // Remove default Chromium inspect/reload/view source items

    if (ctx.selectionText.isNotBlank()) {
        addItem("Copy Selection") {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                java.awt.datatransfer.StringSelection(ctx.selectionText),
                null
            )
        }
        addSearchWeb() // Built-in Google search for selected text
        addSeparator()
    }

    if (!ctx.linkUrl.isNullOrBlank()) {
        addCopyLink() // Built-in Copy URL action
        addSeparator()
    }

    addItem("Reload Page") { ctx.browser.reload() }
    addInspectElement() // Built-in DevTools inspector
}
```

### Fluent Pure Java Builder (Swing)

```java
import dev.daviante.kromium.presentation.menu.KromiumContextMenuHandler;
import dev.daviante.kromium.presentation.menu.KromiumMenuBuilder;
import dev.daviante.kromium.presentation.menu.KromiumContextMenuContext;

client.setContextMenuHandler((builder, context) -> {
    builder.clear();

    if (!context.getSelectionText().isEmpty()) {
        builder.addItem("Search Corporate Knowledge Base", ctx -> {
            ctx.getBrowser().loadUrl("https://kb.internal.corp/search?q=" + ctx.getSelectionText());
        });
        builder.addSeparator();
    }

    builder.addItem("Refresh View", ctx -> ctx.getBrowser().reload());
    builder.addInspectElement();
});
```

---

## 🎙️ WebRTC & Device Permissions

Package: `dev.daviante.kromium.presentation.handler`

`KromiumPermissionHandler` intercepts browser requests for hardware or sensitive APIs (e.g. microphone, webcam, geolocation, DRM).

### Interface Definition

```kotlin
fun interface KromiumPermissionHandler {
    fun onRequestPermission(request: KromiumPermissionRequest)
}
```

### `KromiumPermissionRequest`

| Property | Type | Description |
|:---|:---|:---|
| `origin: String` | `String` | Security origin of the requesting page (e.g., `https://meet.google.com`). |
| `types: Set<KromiumPermissionType>` | `Set<KromiumPermissionType>` | Permissions requested (`DEVICE_AUDIO_CAPTURE`, `DEVICE_VIDEO_CAPTURE`, etc.). |
| `allow(remember: Boolean = true)` | `Unit` | Grants requested permissions. |
| `deny(remember: Boolean = true)` | `Unit` | Denies requested permissions. |

### Compose Kotlin Example

```kotlin
val state = rememberKromiumViewState("https://meet.jit.si")

state.permissionHandler = KromiumPermissionHandler { request ->
    val isTrusted = request.origin.startsWith("https://meet.jit.si")
    if (isTrusted) {
        request.allow(remember = true)
    } else {
        request.deny(remember = true)
    }
}
```

### Pure Java Example

```java
import dev.daviante.kromium.presentation.handler.KromiumPermissionHandler;
import dev.daviante.kromium.presentation.handler.KromiumPermissionType;

client.setPermissionHandler(request -> {
    if (request.getOrigin().startsWith("https://trusted-portal.corp")) {
        request.allow(true);
    } else {
        System.err.println("Rejected permission request from: " + request.getOrigin());
        request.deny(true);
    }
});
```

---

## 📥 File Downloads (`KromiumDownloadListener`)

Package: `dev.daviante.kromium.presentation.handler`

```kotlin
fun interface KromiumDownloadListener {
    fun onDownloadUpdated(item: KromiumDownloadItem)
}
```

### `KromiumDownloadItem` Properties

- `id: Int`: Unique integer identifier for the download.
- `url: String`: Source download URL.
- `suggestedFileName: String`: Inferred file name from HTTP `Content-Disposition`.
- `fullPath: String`: Local destination path.
- `percentComplete: Int`: 0 to 100 percentage.
- `totalBytes: Long`, `receivedBytes: Long`, `currentSpeed: Long`: Transfer stats.
- `isInProgress: Boolean`, `isComplete: Boolean`, `isCanceled: Boolean`.

### Download Pre-Configuration (`onBeforeDownloadListener`)

Customize where the file will be saved and whether to prompt the native OS file picker:

```kotlin
// In Kotlin: Return null to cancel, or a custom target file path:
client.onBeforeDownloadListener = { item, suggestedName ->
    File(System.getProperty("user.home"), "Downloads/$suggestedName").absolutePath
}
```

```java
// In Pure Java:
client.setOnBeforeDownloadListener((item, suggestedName) -> {
    return new java.io.File(System.getProperty("user.home"), "Downloads/" + suggestedName).getAbsolutePath();
});
```

---

## ⏱️ Page Loading & Navigation Events (`KromiumLoadListener`)

Package: `dev.daviante.kromium.presentation.listener`

```java
public interface KromiumLoadListener {
    void onLoadingStateChange(boolean isLoading, boolean canGoBack, boolean canGoForward);
    void onLoadStart(String url);
    void onLoadEnd(String url, int httpStatusCode);
    void onLoadError(String failedUrl, int errorCode, String errorText);
}
```

### Adding and Removing Listeners

```java
KromiumLoadListener listener = new KromiumLoadListener() {
    @Override public void onLoadingStateChange(boolean isLoading, boolean canGoBack, boolean canGoForward) {}
    @Override public void onLoadStart(String url) {}
    @Override public void onLoadEnd(String url, int httpStatusCode) {
        if (httpStatusCode >= 400) {
            System.err.println("HTTP Error: " + httpStatusCode);
        }
    }
    @Override public void onLoadError(String failedUrl, int errorCode, String errorText) {}
};

browser.addLoadListener(listener);
// Later:
browser.removeLoadListener(listener);
```
