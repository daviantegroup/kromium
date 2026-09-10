# Downloads & Native Dialogs

This guide demonstrates how to handle file downloads with progress tracking, pause/resume capabilities, and customize JavaScript `alert()`, `confirm()`, and `prompt()` dialogs.

---

## 📥 Handling File Downloads

Kromium intercepts all browser file downloads without requiring external HTTP libraries.

### 1. Specifying the Download Directory

```kotlin
// Compose Desktop:
val state = rememberKromiumViewState("https://example.com/downloads")
state.downloadDirectory = File(System.getProperty("user.home"), "Downloads")
```

```java
// Pure Java:
client.setDownloadDirectory(new java.io.File(System.getProperty("user.home"), "Downloads"));
```

### 2. Tracking Download Progress & Transfer Speed

```kotlin
state.onDownload = { item ->
    println("File: ${item.suggestedFileName} | Progress: ${item.percentComplete}% | Speed: ${item.currentSpeed / 1024} KB/s")

    if (item.isComplete) {
        println("Download finished: ${item.fullPath}")
    } else if (item.isCanceled) {
        println("Download canceled.")
    }
}
```

### 3. Programmatic Download Controls

Control in-flight downloads programmatically by their unique download ID:

```java
// Pause an active download:
browser.pauseDownload(downloadId);

// Check pause status:
if (browser.isDownloadPaused(downloadId)) {
    System.out.println("Download is currently paused.");
}

// Resume download:
browser.resumeDownload(downloadId);

// Cancel download:
browser.cancelDownload(downloadId);
```

### 4. Custom Destination & Native File Chooser (`onBeforeDownload`)

Customize the filename or prompt the user before the download starts:

```kotlin
state.onBeforeDownload = { item, suggestedName ->
    // Return custom path, or null to cancel download:
    File("/custom/storage/location", suggestedName).absolutePath
}
```

---

## 💬 JavaScript Dialogs (`alert`, `confirm`, `prompt`)

By default, Kromium can display native system dialogs or allow you to intercept dialog calls to present custom Compose or Swing modals.

### Compose Dialog Interception

```kotlin
val state = rememberKromiumViewState("https://example.com")

state.onJsDialog = { dialog ->
    when (dialog.type) {
        KromiumJsDialogType.ALERT -> {
            println("JavaScript Alert: ${dialog.message}")
            dialog.confirm() // Dismiss alert
            true // Handled
        }
        KromiumJsDialogType.CONFIRM -> {
            // Programmatically accept or reject:
            dialog.confirm() // or dialog.cancel()
            true
        }
        KromiumJsDialogType.PROMPT -> {
            dialog.confirm(userInput = "Default Answer")
            true
        }
    }
}
```

### Pure Java Dialog Handling

```java
import dev.daviante.kromium.presentation.handler.KromiumJsDialog;
import dev.daviante.kromium.presentation.handler.KromiumJsDialogType;

client.setJsDialogListener(dialog -> {
    if (dialog.getType() == KromiumJsDialogType.ALERT) {
        javax.swing.JOptionPane.showMessageDialog(null, dialog.getMessage(), "Web Alert", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        dialog.confirm();
        return true;
    }
    return false; // Let default handler process it
});
```
