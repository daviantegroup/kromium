package dev.daviante.kromium.presentation.browser

import dev.daviante.kromium.domain.model.*
import dev.daviante.kromium.domain.config.*
import dev.daviante.kromium.domain.exception.*
import dev.daviante.kromium.data.engine.*
import dev.daviante.kromium.data.model.*
import dev.daviante.kromium.presentation.browser.*
import dev.daviante.kromium.presentation.handler.*
import dev.daviante.kromium.presentation.js.*
import dev.daviante.kromium.presentation.network.*
import dev.daviante.kromium.core.logging.*
import dev.daviante.kromium.core.util.*


import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.cef.callback.CefJSDialogCallback
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.network.CefRequest


private const val TAG = "KromiumClient"

/**
 * High-level wrapper over [CefClient] with built-in message routing, request interception,
 * custom headers injection, download handling, and dialog dispatching.
 */
class KromiumClient(
    val rawClient: CefClient,
    internal val routerQueryName: String = "kromiumQuery",
    internal val routerCancelName: String = "kromiumQueryCancel"
) {

    internal val jsHandler = KromiumJsHandler()

    var requestInterceptor: KromiumRequestInterceptor? = null
    var downloadListener: KromiumDownloadListener? = null
    var downloadDirectory: java.io.File = java.io.File(System.getProperty("user.home"), "Downloads")
    var onBeforeDownloadListener: ((item: KromiumDownloadItem, suggestedFileName: String) -> String?)? = null
    private val downloadCallbacks = java.util.concurrent.ConcurrentHashMap<Int, CefDownloadItemCallback>()

    fun cancelDownload(downloadId: Int): Boolean {
        val cb = downloadCallbacks[downloadId] ?: return false
        cb.cancel()
        return true
    }

    fun pauseDownload(downloadId: Int): Boolean {
        val cb = downloadCallbacks[downloadId] ?: return false
        cb.pause()
        return true
    }

    fun resumeDownload(downloadId: Int): Boolean {
        val cb = downloadCallbacks[downloadId] ?: return false
        cb.resume()
        return true
    }

    var jsDialogListener: KromiumJsDialogListener? = null
    var consoleMessageListener: ((KromiumConsoleMessage) -> Unit)? = null
    var authListener: KromiumAuthListener? = null
    var onPopupListener: ((url: String) -> Boolean)? = null
    var onPermissionRequest: ((url: String) -> Boolean)? = null
    var enableContextMenus: Boolean = true
    var loadErrorListener: ((KromiumLoadError) -> Unit)? = null
    var customUserAgent: String? = null
    var shouldOverrideUrlLoading: ((url: String) -> Boolean)? = null

    internal val htmlPayloads = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * SSL error handling policy. Defaults to [SslErrorPolicy.Strict] which rejects all
     * certificate errors. Use [SslErrorPolicy.AllowDomains] to whitelist specific domains,
     * or [SslErrorPolicy.AllowAll] for development only.
     *
     * @see SslErrorPolicy
     */
    var sslErrorPolicy: SslErrorPolicy = SslErrorPolicy.Strict
        set(value) {
            field = value
            if (value is SslErrorPolicy.AllowAll) {
                KromiumLogger.w(TAG, "⚠️ SSL error policy set to AllowAll — ALL certificate errors will be ignored. DO NOT use in production!")
            }
        }

    /**
     * @deprecated Use [sslErrorPolicy] instead for scoped SSL error handling.
     * Setting this to `true` is equivalent to `sslErrorPolicy = SslErrorPolicy.AllowAll`.
     */
    @Deprecated("Use sslErrorPolicy instead", ReplaceWith("sslErrorPolicy"))
    var ignoreSslErrors: Boolean
        get() = sslErrorPolicy is SslErrorPolicy.AllowAll
        set(value) {
            sslErrorPolicy = if (value) SslErrorPolicy.AllowAll else SslErrorPolicy.Strict
        }

    init {
        // Setup JS Message Router
        val routerConfig = CefMessageRouter.CefMessageRouterConfig(routerQueryName, routerCancelName)
        val messageRouter = CefMessageRouter.create(routerConfig)
        messageRouter.addHandler(jsHandler, false)
        rawClient.addMessageRouter(messageRouter)

        // Setup Request Interceptor & User-Agent Handler
        setupRequestHandler()

        // Setup Download Handler
        setupDownloadHandler()

        // Setup JS Dialog Handler
        setupDialogHandler()

        // Setup Load Handler for errors
        setupLoadHandler()
    }

    private fun setupLoadHandler() {
        rawClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?
            ) {
                val listener = loadErrorListener ?: return
                // Only trigger for main frame navigation errors to avoid spam from broken images/iframes
                if (frame?.isMain != true) return
                
                try {
                    listener(
                        KromiumLoadError(
                            errorCode = errorCode?.code ?: 0,
                            errorText = errorText ?: "Unknown error",
                            failedUrl = failedUrl ?: ""
                        )
                    )
                } catch (e: Throwable) {
                    KromiumLogger.e(TAG, "Load error listener threw an exception", e)
                }
            }
        })
    }

    private fun setupRequestHandler() {
        val resourceHandler = object : CefResourceRequestHandlerAdapter() {
            override fun onBeforeResourceLoad(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?
            ): Boolean {
                if (request == null) return false

                // 1. Inject custom user-agent if provided
                customUserAgent?.let { ua ->
                    request.setHeaderByName("User-Agent", ua, true)
                }

                // 2. Pass to developer's interceptor if provided
                val interceptor = requestInterceptor
                if (interceptor != null) {
                    val headersMap = mutableMapOf<String, String>()
                    request.getHeaderMap(headersMap)

                    val webReq = KromiumWebResourceRequest(
                        url = request.url ?: "",
                        method = request.method ?: "GET",
                        headers = headersMap,
                        isNavigation = (frame?.isMain == true),
                        isDownload = false
                    )

                    val shouldBlock = try {
                        interceptor.intercept(webReq)
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Request interceptor threw an exception for ${request.url}", e)
                        false
                    }

                    if (shouldBlock) {
                        KromiumLogger.d(TAG, "Request blocked by interceptor: ${request.url}")
                        return true // Block resource
                    }

                    // Apply any mutated headers back to CEF request
                    webReq.headers.forEach { (key, value) ->
                        request.setHeaderByName(key, value, true)
                    }
                }

                return false // Proceed
            }

            override fun getResourceHandler(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?
            ): CefResourceHandler? {
                val url = request?.url ?: return null
                val payload = htmlPayloads[url]
                if (payload != null) {
                    return KromiumHtmlResourceHandler(payload)
                }
                return null
            }
        }

        rawClient.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                userGesture: Boolean,
                isRedirect: Boolean
            ): Boolean {
                val override = shouldOverrideUrlLoading ?: return false
                val targetUrl = request?.url ?: return false
                return try {
                    override(targetUrl)
                } catch (e: Throwable) {
                    KromiumLogger.e(TAG, "shouldOverrideUrlLoading threw an exception for $targetUrl", e)
                    false
                }
            }

            override fun onCertificateError(
                browser: CefBrowser?,
                certError: CefLoadHandler.ErrorCode?,
                requestUrl: String?,
                sslInfo: org.cef.security.CefSSLInfo?,
                callback: org.cef.callback.CefCallback?
            ): Boolean {
                val policy = sslErrorPolicy
                val shouldAllow = when (policy) {
                    is SslErrorPolicy.Strict -> false
                    is SslErrorPolicy.AllowAll -> true
                    is SslErrorPolicy.AllowDomains -> policy.isAllowed(requestUrl)
                }

                if (shouldAllow) {
                    KromiumLogger.w(TAG, "SSL certificate error bypassed for: $requestUrl (error: $certError)")
                    callback?.Continue()
                    return true
                }

                KromiumLogger.d(TAG, "SSL certificate error rejected for: $requestUrl (error: $certError)")
                return false
            }

            override fun getAuthCredentials(
                browser: CefBrowser?,
                originUrl: String?,
                isProxy: Boolean,
                host: String?,
                port: Int,
                realm: String?,
                scheme: String?,
                callback: org.cef.callback.CefAuthCallback?
            ): Boolean {
                val listener = authListener ?: return false

                val req = KromiumAuthRequest(
                    isProxy = isProxy,
                    host = host ?: "",
                    port = port,
                    realm = realm ?: "",
                    scheme = scheme ?: ""
                )

                val response = try {
                    listener.onAuthRequired(req)
                } catch (e: Throwable) {
                    KromiumLogger.e(TAG, "Auth listener threw an exception", e)
                    KromiumAuthResponse.Cancel
                }

                return when (response) {
                    is KromiumAuthResponse.Proceed -> {
                        callback?.Continue(response.username, response.password)
                        true
                    }
                    is KromiumAuthResponse.Cancel -> {
                        callback?.cancel()
                        false
                    }
                }
            }

            override fun getResourceRequestHandler(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String?,
                disableDefaultHandling: BoolRef?
            ): CefResourceRequestHandler {
                return resourceHandler
            }
        })
    }

    private fun setupDownloadHandler() {
        rawClient.addDownloadHandler(object : CefDownloadHandlerAdapter() {
            override fun onBeforeDownload(
                browser: CefBrowser?,
                downloadItem: CefDownloadItem?,
                suggestedName: String?,
                callback: CefBeforeDownloadCallback?
            ): Boolean {
                if (callback == null) return false

                val rawName = suggestedName?.takeIf { it.isNotBlank() }
                    ?: downloadItem?.suggestedFileName?.takeIf { it.isNotBlank() }
                    ?: "download"
                val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\')
                    .replace("[?%*:|\"<>]".toRegex(), "_")
                    .ifBlank { "download" }

                val targetDir = downloadDirectory.apply { if (!exists()) mkdirs() }
                val targetFile = getNonConflictingDownloadFile(targetDir, cleanName)
                val defaultTargetPath = targetFile.absolutePath

                val item = downloadItem?.let {
                    KromiumDownloadItem(
                        id = it.id,
                        url = it.url ?: "",
                        suggestedFileName = cleanName,
                        fullPath = defaultTargetPath,
                        totalBytes = it.totalBytes,
                        receivedBytes = it.receivedBytes,
                        percentComplete = it.percentComplete,
                        speed = it.currentSpeed,
                        isInProgress = it.isInProgress,
                        isComplete = it.isComplete,
                        isCanceled = it.isCanceled
                    )
                }

                val customPath = if (item != null) onBeforeDownloadListener?.invoke(item, cleanName) else null

                when {
                    customPath != null && customPath.isBlank() -> {
                        KromiumLogger.i(TAG, "Download canceled: $cleanName")
                        return true
                    }
                    customPath != null -> {
                        KromiumLogger.i(TAG, "Downloading $cleanName to custom path: $customPath")
                        callback.Continue(customPath, false)
                    }
                    else -> {
                        KromiumLogger.i(TAG, "Downloading $cleanName to: $defaultTargetPath")
                        callback.Continue(defaultTargetPath, false)
                    }
                }
                return false
            }

            override fun onDownloadUpdated(
                browser: CefBrowser?,
                downloadItem: CefDownloadItem?,
                callback: CefDownloadItemCallback?
            ) {
                if (downloadItem == null) return

                if (callback != null) {
                    if (downloadItem.isComplete || downloadItem.isCanceled) {
                        downloadCallbacks.remove(downloadItem.id)
                    } else {
                        downloadCallbacks[downloadItem.id] = callback
                    }
                }

                val listener = downloadListener ?: return

                val item = KromiumDownloadItem(
                    id = downloadItem.id,
                    url = downloadItem.url ?: "",
                    suggestedFileName = downloadItem.suggestedFileName ?: "",
                    fullPath = downloadItem.fullPath ?: "",
                    totalBytes = downloadItem.totalBytes,
                    receivedBytes = downloadItem.receivedBytes,
                    percentComplete = downloadItem.percentComplete,
                    speed = downloadItem.currentSpeed,
                    isInProgress = downloadItem.isInProgress,
                    isComplete = downloadItem.isComplete,
                    isCanceled = downloadItem.isCanceled
                )

                try {
                    listener.onDownloadUpdated(item)
                } catch (e: Throwable) {
                    KromiumLogger.e(TAG, "Download listener threw an exception", e)
                }
            }
        })
    }

    private fun getNonConflictingDownloadFile(dir: java.io.File, fileName: String): java.io.File {
        var file = java.io.File(dir, fileName)
        if (!file.exists()) return file
        val base = fileName.substringBeforeLast('.', "")
        val ext = fileName.substringAfterLast('.', "")
        val prefix = if (base.isEmpty()) fileName else base
        val suffix = if (ext.isEmpty() || ext == fileName) "" else ".$ext"
        var counter = 1
        while (file.exists()) {
            file = java.io.File(dir, "$prefix ($counter)$suffix")
            counter++
        }
        return file
    }

    private fun setupDialogHandler() {
        rawClient.addJSDialogHandler(object : CefJSDialogHandlerAdapter() {
            override fun onJSDialog(
                browser: CefBrowser?,
                originUrl: String?,
                dialogType: CefJSDialogHandler.JSDialogType?,
                messageText: String?,
                defaultPromptText: String?,
                callback: CefJSDialogCallback?,
                suppressMessage: BoolRef?
            ): Boolean {
                val listener = jsDialogListener ?: return false

                val mappedType = when (dialogType) {
                    CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_ALERT -> KromiumJsDialogType.ALERT
                    CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_CONFIRM -> KromiumJsDialogType.CONFIRM
                    CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_PROMPT -> KromiumJsDialogType.PROMPT
                    else -> KromiumJsDialogType.ALERT
                }

                val dialog = KromiumJsDialog(
                    message = messageText ?: "",
                    defaultPromptText = defaultPromptText ?: "",
                    type = mappedType,
                    onConfirm = { result -> callback?.Continue(true, result) },
                    onCancel = { callback?.Continue(false, "") }
                )

                return try {
                    listener.onDialog(dialog)
                } catch (e: Throwable) {
                    KromiumLogger.e(TAG, "JS dialog listener threw an exception", e)
                    false
                }
            }
        })
    }

    fun createBrowser(
        url: String? = "about:blank",
        isOffScreenRendered: Boolean = false,
        isTransparent: Boolean = false,
        requestContext: CefRequestContext? = null
    ): KromiumBrowser {
        val rendering = if (isOffScreenRendered) CefRendering.OFFSCREEN else CefRendering.DEFAULT
        val browser = if (requestContext != null) {
            rawClient.createBrowser(url, rendering, isTransparent, requestContext)
        } else {
            rawClient.createBrowser(url, rendering, isTransparent)
        }
        return KromiumBrowser(this, browser)
    }

    fun createBrowser(
        url: String?,
        isTransparent: Boolean
    ): KromiumBrowser = createBrowser(url = url, isOffScreenRendered = false, isTransparent = isTransparent)

    fun addLoadHandler(handler: CefLoadHandler) = apply { rawClient.addLoadHandler(handler) }
    fun removeLoadHandler() = apply { rawClient.removeLoadHandler() }

    fun addDisplayHandler(handler: CefDisplayHandler) = apply { rawClient.addDisplayHandler(handler) }
    fun removeDisplayHandler() = apply { rawClient.removeDisplayHandler() }

    init {
        // Internal Display Handler for console messages
        rawClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: org.cef.CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                val listener = consoleMessageListener ?: return false
                val mappedLevel = when (level) {
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_ERROR, org.cef.CefSettings.LogSeverity.LOGSEVERITY_FATAL -> KromiumConsoleMessageLevel.ERROR
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING -> KromiumConsoleMessageLevel.WARNING
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_INFO -> KromiumConsoleMessageLevel.INFO
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_VERBOSE -> KromiumConsoleMessageLevel.DEBUG
                    else -> KromiumConsoleMessageLevel.DEFAULT
                }
                
                try {
                    listener(
                        KromiumConsoleMessage(
                            level = mappedLevel,
                            message = message ?: "",
                            source = source ?: "",
                            line = line
                        )
                    )
                } catch (e: Throwable) {
                    KromiumLogger.e(TAG, "Console message listener threw an exception", e)
                }
                
                // Return false to allow default handling (printing to stdout/stderr in dev mode)
                return false
            }
        })
    }

    fun addLifeSpanHandler(handler: CefLifeSpanHandler) = apply { rawClient.addLifeSpanHandler(handler) }
    fun removeLifeSpanHandler() = apply { rawClient.removeLifeSpanHandler() }

    init {
        // Internal LifeSpan Handler for popups
        rawClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                target_url: String?,
                target_frame_name: String?
            ): Boolean {
                val url = target_url ?: return true
                val listener = onPopupListener
                return try {
                    if (listener != null) {
                        listener.invoke(url)
                    } else {
                        // Default behavior if no listener: block native popups to prevent UI breakage
                        KromiumLogger.w(TAG, "Blocked popup to $url because onPopup was not handled.")
                        true
                    }
                } catch (e: Throwable) {
                    KromiumLogger.e(TAG, "Popup listener threw an exception", e)
                    true // Block popup on error
                }
            }
        })
    }

    fun addContextMenuHandler(handler: CefContextMenuHandler) = apply { rawClient.addContextMenuHandler(handler) }
    fun removeContextMenuHandler() = apply { rawClient.removeContextMenuHandler() }

    init {
        // Internal Context Menu Handler
        rawClient.addContextMenuHandler(object : org.cef.handler.CefContextMenuHandlerAdapter() {
            override fun onBeforeContextMenu(
                browser: CefBrowser?,
                frame: CefFrame?,
                params: org.cef.callback.CefContextMenuParams?,
                model: org.cef.callback.CefMenuModel?
            ) {
                if (!enableContextMenus) {
                    model?.clear()
                }
            }
        })
    }

    fun addFocusHandler(handler: CefFocusHandler) = apply { rawClient.addFocusHandler(handler) }
    fun removeFocusHandler() = apply { rawClient.removeFocusHandler() }

    fun addKeyboardHandler(handler: CefKeyboardHandler) = apply { rawClient.addKeyboardHandler(handler) }
    fun removeKeyboardHandler() = apply { rawClient.removeKeyboardHandler() }

    fun dispose() {
        try {
            rawClient.dispose()
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Error during client disposal", e)
        }
    }
}
