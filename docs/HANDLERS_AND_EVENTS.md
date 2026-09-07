# Handlers, Listeners & Events Reference

This document covers the event listener interfaces in Kromium: authentication challenges, JavaScript dialogs, file downloads, developer console messages, popup suppression, and load error handling.

---

## Table of Contents

1. [Overview](#overview)
2. [HTTP & Proxy Authentication (`KromiumAuthListener`)](#http--proxy-authentication-kromiumauthlistener)
   - [The `KromiumAuthRequest` Model](#the-kromiumauthrequest-model)
   - [Responding with `KromiumAuthResponse`](#responding-with-kromiumauthresponse)
3. [JavaScript Dialogs (`KromiumJsDialogListener`)](#javascript-dialogs-kromiumjsdialoglistener)
   - [Dialog Types (`KromiumJsDialogType`)](#dialog-types-kromiumjsdialogtype)
   - [Handling Alerts, Confirms, and Prompts](#handling-alerts-confirms-and-prompts)
4. [File Downloads (`KromiumDownloadListener`)](#file-downloads-kromiumdownloadlistener)
   - [Download Progress Tracking (`KromiumDownloadItem`)](#download-progress-tracking-kromiumdownloaditem)
5. [Browser Console Logging (`KromiumConsoleMessage`)](#browser-console-logging-kromiumconsolemessage)
   - [Log Levels (`KromiumConsoleMessageLevel`)](#log-levels-kromiumconsolemessagelevel)
6. [Page Load Errors (`KromiumLoadError`)](#page-load-errors-kromiumloaderror)
7. [Popups & Permission Requests](#popups--permission-requests)
   - [Intercepting Popups (`onPopup`)](#intercepting-popups-onpopup)
   - [Feature Permissions (`onPermissionRequest`)](#feature-permissions-onpermissionrequest)
8. [Context Menus (`enableContextMenus`)](#context-menus-enablecontextmenus)

---

## Overview

All listeners can be registered either via properties on `KromiumViewState` (in Compose Desktop) or on `KromiumClient` (in raw JVM):

```
┌────────────────────────────────────────────────────────┐
│                      KromiumClient                     │
├────────────────────────────────────────────────────────┤
│ • authListener: KromiumAuthListener?                   │
│ • jsDialogListener: KromiumJsDialogListener?           │
│ • downloadListener: KromiumDownloadListener?           │
│ • consoleMessageListener: ((KromiumConsoleMessage) -> Unit)?
│ • loadErrorListener: ((KromiumLoadError) -> Unit)?     │
│ • onPopupListener: ((url: String) -> Boolean)?         │
│ • onPermissionRequest: ((url: String) -> Boolean)?     │
│ • enableContextMenus: Boolean                          │
└────────────────────────────────────────────────────────┘
```

In Compose, setting these properties on `KromiumViewState` automatically binds them to the underlying `KromiumClient`.

---

## HTTP & Proxy Authentication (`KromiumAuthListener`)

Triggered whenever a web server (HTTP 401 Unauthorized) or an intermediary proxy (HTTP 407 Proxy Authentication Required) requests credentials:

```kotlin
fun interface KromiumAuthListener {
    fun onAuthRequired(request: KromiumAuthRequest): KromiumAuthResponse
}
```

### The `KromiumAuthRequest` Model

| Property | Type | Description |
|---|---|---|
| `isProxy` | `Boolean` | `true` if this authentication challenge originated from a proxy; `false` if from the origin web server. |
| `host` | `String` | Hostname requesting authentication (e.g. `"auth.internal.corp"`). |
| `port` | `Int` | Target port number. |
| `realm` | `String` | Authentication realm string provided by the server. |
| `scheme` | `String` | Authentication scheme (e.g. `"Basic"`, `"Digest"`, `"NTLM"`). |

### Responding with `KromiumAuthResponse`
Return one of two responses:
- `KromiumAuthResponse.Proceed(username, password)`: Supplies the credentials to CEF.
- `KromiumAuthResponse.Cancel`: Aborts authentication and displays the server's 401/407 error page.

### Example

```kotlin
val authListener = KromiumAuthListener { request ->
    if (request.host == "internal-gateway.corp" && request.isProxy) {
        KromiumAuthResponse.Proceed(
            username = "service_account",
            password = "secure_password_99"
        )
    } else {
        // Show an auth modal in your UI or cancel
        KromiumAuthResponse.Cancel
    }
}

// In Compose:
state.onAuthRequired = { req -> authListener.onAuthRequired(req) }

// In raw JVM:
client.authListener = authListener
```

---

## JavaScript Dialogs (`KromiumJsDialogListener`)

Intercepts JavaScript modal dialogs (`window.alert`, `window.confirm`, `window.prompt`) so you can replace native OS dialogs with custom UI dialogs:

```kotlin
fun interface KromiumJsDialogListener {
    fun onDialog(dialog: KromiumJsDialog): Boolean
}
```

### Dialog Types (`KromiumJsDialogType`)
- `ALERT`: Single confirmation notification (`window.alert("Hello")`).
- `CONFIRM`: Binary confirmation dialog (`window.confirm("Delete item?")`).
- `PROMPT`: Text input prompt (`window.prompt("Enter your name:", "John")`).

### The `KromiumJsDialog` Object
- `dialog.message: String`: Text message emitted by the webpage.
- `dialog.defaultPromptText: String`: Default input value (for prompts).
- `dialog.type: KromiumJsDialogType`: Dialog kind.
- `dialog.confirm(promptResult: String = defaultPromptText)`: Continues execution (accepting alert, confirming confirm with `true`, or returning text for prompt).
- `dialog.cancel()`: Rejects the dialog (`false` for confirm, `null` for prompt).

### Example: Custom Compose Modal Dialog

```kotlin
state.onJsDialog = { dialog ->
    when (dialog.type) {
        KromiumJsDialogType.ALERT -> {
            displayAlertDialog(dialog.message) {
                dialog.confirm()
            }
            true // Handled by our application
        }
        KromiumJsDialogType.CONFIRM -> {
            displayConfirmDialog(
                message = dialog.message,
                onConfirm = { dialog.confirm() },
                onDismiss = { dialog.cancel() }
            )
            true
        }
        KromiumJsDialogType.PROMPT -> {
            displayPromptDialog(
                message = dialog.message,
                defaultText = dialog.defaultPromptText,
                onSubmit = { text -> dialog.confirm(text) },
                onCancel = { dialog.cancel() }
            )
            true
        }
    }
}
```
> Return `true` if your code handled the dialog; return `false` to let Chromium use default handling.

---

## File Downloads (`KromiumDownloadListener`)

Tracks files downloaded from the browser:

```kotlin
fun interface KromiumDownloadListener {
    fun onDownloadUpdated(item: KromiumDownloadItem)
}
```

### Download Progress Tracking (`KromiumDownloadItem`)

| Property | Type | Description |
|---|---|---|
| `id` | `Int` | Unique download task ID. |
| `url` | `String` | Source URL of the downloaded file. |
| `suggestedFileName` | `String` | Suggested filename extracted from `Content-Disposition` or URL path. |
| `totalBytes` | `Long` | Total expected byte count (-1 if unknown). |
| `receivedBytes` | `Long` | Number of bytes downloaded so far. |
| `percentComplete` | `Int` | Integer percentage (0 to 100). |
| `speed` | `Long` | Current download transfer rate in bytes/sec. |
| `isInProgress` | `Boolean` | `true` while bytes are actively transferring. |
| `isComplete` | `Boolean` | `true` when download has finished successfully. |
| `isCanceled` | `Boolean` | `true` if the download was aborted. |

### Example

```kotlin
state.onDownload = { item ->
    val speedKb = item.speed / 1024
    println("Downloading ${item.suggestedFileName}: ${item.percentComplete}% ($speedKb KB/s)")

    if (item.isComplete) {
        println("Download complete: ${item.suggestedFileName}")
    } else if (item.isCanceled) {
        println("Download cancelled: ${item.suggestedFileName}")
    }
}
```

---

## Browser Console Logging (`KromiumConsoleMessage`)

Captures messages printed inside the webpage by `console.log()`, `console.error()`, etc.:

```kotlin
state.onConsoleMessage = { msg ->
    println("[JS ${msg.level}] ${msg.source}:${msg.line} -> ${msg.message}")
}
```

### Log Levels (`KromiumConsoleMessageLevel`)
- `DEBUG`: Verbose logs (`console.debug`)
- `INFO`: Informational messages (`console.info`, `console.log`)
- `WARNING`: Warnings (`console.warn`)
- `ERROR`: Errors and uncaught exceptions (`console.error`)
- `DEFAULT`: Unspecified log level

---

## Page Load Errors (`KromiumLoadError`)

Intercepts failed page loads (e.g. connection refused, DNS lookup failure, timeout):

```kotlin
data class KromiumLoadError(
    val errorCode: Int,
    val errorText: String,
    val failedUrl: String
)
```

> **Note on filtering**: Kromium automatically filters load errors to **main-frame** navigations only (`frame?.isMain == true`). This ensures your application is not bombarded by failed sub-resource requests (e.g. a broken 3rd-party tracking pixel or missing image).

### Example

```kotlin
state.onLoadError = { error ->
    System.err.println("Failed to load page: ${error.failedUrl}")
    System.err.println("Error code: ${error.errorCode}, reason: ${error.errorText}")

    // Optionally render a local error screen:
    state.loadHtml("""
        <div style="font-family: sans-serif; padding: 40px; text-align: center;">
            <h2>Unable to connect</h2>
            <p>Could not load <code>${error.failedUrl}</code> (${error.errorText}).</p>
            <button onclick="location.reload()">Retry</button>
        </div>
    """.trimIndent())
}
```

---

## Popups & Permission Requests

### Intercepting Popups (`onPopup`)
By default, Kromium blocks unmanaged native popups (`window.open`) to prevent UI breakage:

```kotlin
state.onPopup = { targetUrl ->
    // Option A: Open the link in the current tab
    state.loadUrl(targetUrl)
    true // Mark popup as handled

    // Option B: Open in user's default external browser
    // java.awt.Desktop.getDesktop().browse(java.net.URI(targetUrl))
    // true
}
```

### Feature Permissions (`onPermissionRequest`)
Handles web platform permissions (geolocation, camera, microphone, notifications):

```kotlin
state.onPermissionRequest = { requestingUrl ->
    // Only grant permissions to trusted origins
    requestingUrl.startsWith("https://meet.mycompany.com")
}
```

---

## Context Menus (`enableContextMenus`)

To disable or suppress the native Chromium right-click context menu:

```kotlin
// In Compose:
state.enableContextMenus = false

// In raw JVM:
client.enableContextMenus = false
```
When set to `false`, Kromium clears the menu model in `onBeforeContextMenu`, preventing the popup menu from appearing.
