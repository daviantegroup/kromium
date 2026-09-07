# Downloads, Dialogs & DevTools

[Documentation Hub](../README.md) &bull; **Guides** &bull; Downloads & Dialogs

---

## 📥 File Download Interception

Intercept user-initiated file downloads, customize save directories, and track real-time progress:

```kotlin
// 1. Set default target directory
client.downloadDirectory = java.io.File(System.getProperty("user.home"), "Downloads")

// 2. Customize target path or prompt user before download starts
client.onBeforeDownloadListener = { item, suggestedFileName ->
    println("Starting download: $suggestedFileName (${item.totalBytes} bytes)")
    // Return custom path to override destination, or null to use default downloadDirectory
    null
}

// 3. Monitor live download progress
client.downloadListener = KromiumDownloadListener { item ->
    println(
        "Download #${item.id}: ${item.percentComplete}% | " +
        "Speed: ${item.speed / 1024} KB/s | " +
        "Received: ${item.receivedBytes} / ${item.totalBytes}"
    )

    if (item.isComplete) {
        println("Download complete: ${item.fullPath}")
    }
}
```

### Download Controls
Pause, resume, or cancel active downloads by their numeric ID:

```kotlin
client.pauseDownload(downloadId)
client.resumeDownload(downloadId)
client.cancelDownload(downloadId)
```

---

## 💬 JavaScript Modal Dialogs (`alert`, `confirm`, `prompt`)

Prevent web pages from locking the UI thread with unhandled modal dialogs:

```kotlin
client.jsDialogListener = KromiumJsDialogListener { dialog ->
    when (dialog.type) {
        KromiumJsDialogType.ALERT -> {
            println("Alert message: ${dialog.message}")
            dialog.continueDialog(true) // Dismiss alert
            true
        }
        KromiumJsDialogType.CONFIRM -> {
            println("Confirm prompt: ${dialog.message}")
            dialog.continueDialog(true) // Accept (true) or Cancel (false)
            true
        }
        KromiumJsDialogType.PROMPT -> {
            println("Prompt: ${dialog.message}")
            dialog.continueDialog(true, "User input text")
            true
        }
        KromiumJsDialogType.BEFORE_UNLOAD -> {
            dialog.continueDialog(true) // Allow leaving page
            true
        }
    }
}
```

---

## 🛠️ DevTools & Console Logs

### Redirecting Web Console Messages to Kotlin
Capture `console.log`, `console.warn`, and `console.error` calls emitted by web pages:

```kotlin
client.consoleMessageListener = { msg ->
    println("[WebConsole] [${msg.level}] [${msg.source}:${msg.line}] ${msg.message}")
}
```

### Remote Chrome DevTools Debugging
Inspect pages using Google Chrome / Edge Developer Tools:

```kotlin
Kromium.initialize {
    // Open DevTools port on localhost:9222
    remoteDebuggingPort = 9222
}
```

Once running, navigate to `chrome://inspect` in your standard Google Chrome browser to inspect DOM, network waterfalls, memory heaps, and CSS in real time.
