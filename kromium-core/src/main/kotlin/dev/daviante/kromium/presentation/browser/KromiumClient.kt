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
    val requestContext: CefRequestContext? = null,
    internal val routerQueryName: String = "kromiumQuery",
    internal val routerCancelName: String = "kromiumQueryCancel"
) {

    private var clientProxy: KromiumProxy? = null

    /**
     * The active proxy configuration for this client session.
     * Inherits from [Kromium.activeProxy] unless overridden by [setProxy].
     */
    var activeProxy: KromiumProxy
        get() = clientProxy ?: Kromium.activeProxy
        set(value) {
            setProxy(value)
        }

    /**
     * Dynamically updates the proxy strategy for this client or its associated request context.
     *
     * @param proxy The new [KromiumProxy] configuration to apply.
     * @return [Result.success] if applied, or [Result.failure] with error details.
     */
    fun setProxy(proxy: KromiumProxy): Result<Unit> {
        clientProxy = proxy
        if (!Kromium.isReady) {
            return Result.failure(KromiumException.NotInitialized)
        }
        val context = requestContext ?: CefRequestContext.getGlobalContext()
            ?: return Result.failure(KromiumException.NotInitialized)
        return try {
            val prefMap = proxy.toPreferenceMap()
            val error = context.setPreference("proxy", prefMap)
            if (error.isNullOrEmpty()) {
                KromiumLogger.i(TAG, "Client proxy updated to: $proxy")
                Result.success(Unit)
            } else {
                KromiumLogger.e(TAG, "Failed to set client proxy: $error")
                Result.failure(KromiumException.ProxyError("Failed to set proxy preference: $error"))
            }
        } catch (t: Throwable) {
            KromiumLogger.e(TAG, "Error applying dynamic client proxy", t)
            Result.failure(t)
        }
    }

    /**
     * Dynamically updates the client proxy returning a boolean.
     * Provides 100% clean Java compatibility bypassing Kotlin Result value class mangling.
     */
    @JvmName("updateProxy")
    fun updateProxy(proxy: KromiumProxy): Boolean {
        return setProxy(proxy).isSuccess
    }

    internal val jsHandler = KromiumJsHandler()

    @Volatile var requestInterceptor: KromiumRequestInterceptor? = null
    @Volatile var downloadListener: KromiumDownloadListener? = null
    @Volatile var downloadDirectory: java.io.File = resolveDefaultDownloadDirectory()
    @Volatile var onBeforeDownloadListener: ((item: KromiumDownloadItem, suggestedFileName: String) -> String?)? = null

    /** Sets the onBeforeDownloadListener using a Java [java.util.function.BiFunction]. */
    fun setOnBeforeDownloadListener(listener: java.util.function.BiFunction<KromiumDownloadItem, String, String?>?) {
        onBeforeDownloadListener = if (listener != null) { { item, name -> listener.apply(item, name) } } else null
    }

    fun cancelDownload(downloadId: Int): Boolean = cancelDownloadGlobally(downloadId)

    fun pauseDownload(downloadId: Int): Boolean = pauseDownloadGlobally(downloadId)

    fun resumeDownload(downloadId: Int): Boolean = resumeDownloadGlobally(downloadId)

    fun isDownloadPaused(downloadId: Int): Boolean = isDownloadPausedGlobally(downloadId)

    @Volatile var jsDialogListener: KromiumJsDialogListener? = null
    @Volatile var consoleMessageListener: ((KromiumConsoleMessage) -> Unit)? = null

    /** Sets the console message listener using a Java [java.util.function.Consumer]. */
    fun setConsoleMessageListener(listener: java.util.function.Consumer<KromiumConsoleMessage>?) {
        consoleMessageListener = if (listener != null) { { msg -> listener.accept(msg) } } else null
    }

    @Volatile var authListener: KromiumAuthListener? = null
    @Volatile var onPopupListener: ((url: String) -> Boolean)? = null

    /** Sets the popup listener using a Java [java.util.function.Predicate]. */
    fun setOnPopupListener(listener: java.util.function.Predicate<String>?) {
        onPopupListener = if (listener != null) { { url -> listener.test(url) } } else null
    }

    @Volatile var onPermissionRequest: ((url: String) -> Boolean)? = null

    /** Sets the permission request listener using a Java [java.util.function.Predicate]. */
    fun setOnPermissionRequest(listener: java.util.function.Predicate<String>?) {
        onPermissionRequest = if (listener != null) { { url -> listener.test(url) } } else null
    }

    @Volatile var enableContextMenus: Boolean = true
    @Volatile var loadErrorListener: ((KromiumLoadError) -> Unit)? = null

    /** Sets the load error listener using a Java [java.util.function.Consumer]. */
    fun setLoadErrorListener(listener: java.util.function.Consumer<KromiumLoadError>?) {
        loadErrorListener = if (listener != null) { { err -> listener.accept(err) } } else null
    }

    @Volatile var customUserAgent: String? = null
    @Volatile var shouldOverrideUrlLoading: ((url: String) -> Boolean)? = null

    /** Sets the URL loading override using a Java [java.util.function.Predicate]. */
    fun setShouldOverrideUrlLoading(listener: java.util.function.Predicate<String>?) {
        shouldOverrideUrlLoading = if (listener != null) { { url -> listener.test(url) } } else null
    }

    /**
     * Asset filter configuration for blocking media, images, fonts, and stylesheets.
     */
    @Volatile var assetFilter: KromiumAssetFilter? = null

    /**
     * Whitelist of allowed hostnames/domains. Navigations outside these domains will be rejected.
     */
    @Volatile var hostLock: Set<String>? = null

    /**
     * When true, host lock also restricts subresource network requests (scripts, xhr, fetch).
     */
    @Volatile var hostLockSubresources: Boolean = false

    private val loadHandlers = java.util.concurrent.CopyOnWriteArrayList<CefLoadHandler>()
    private val displayHandlers = java.util.concurrent.CopyOnWriteArrayList<CefDisplayHandler>()
    private val lifeSpanHandlers = java.util.concurrent.CopyOnWriteArrayList<CefLifeSpanHandler>()
    private val contextMenuHandlers = java.util.concurrent.CopyOnWriteArrayList<CefContextMenuHandler>()
    private val focusHandlers = java.util.concurrent.CopyOnWriteArrayList<CefFocusHandler>()
    private val keyboardHandlers = java.util.concurrent.CopyOnWriteArrayList<CefKeyboardHandler>()

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

        // Setup Composite Load, Display, LifeSpan, ContextMenu, Focus, and Keyboard Handlers
        setupCompositeHandlers()
    }

    private fun setupRequestHandler() {
        val resourceHandler = object : CefResourceRequestHandlerAdapter() {
            override fun onBeforeResourceLoad(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?
            ): Boolean {
                if (request == null) return false

                // 0. Asset filter (block media, images, fonts, stylesheets)
                assetFilter?.let { filter ->
                    if (filter.shouldBlock(request)) {
                        KromiumLogger.d(TAG, "Resource blocked by asset filter: ${request.url}")
                        return true
                    }
                }

                // 1. Subresource host lock if enabled
                if (hostLockSubresources) {
                    val allowed = hostLock
                    val reqUrl = request.url
                    if (!allowed.isNullOrEmpty() && !reqUrl.isNullOrBlank()) {
                        if (!KromiumAssetFilter.isHostAllowed(reqUrl, allowed)) {
                            KromiumLogger.d(TAG, "Subresource blocked by host lock: $reqUrl")
                            return true
                        }
                    }
                }

                // 2. Inject custom user-agent if provided
                customUserAgent?.let { ua ->
                    request.setHeaderByName("User-Agent", ua, true)
                }

                // 3. Pass to developer's interceptor if provided
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
                val targetUrl = request?.url ?: return false

                // Enforce host lock on navigation
                val allowed = hostLock
                if (!allowed.isNullOrEmpty()) {
                    if (!KromiumAssetFilter.isHostAllowed(targetUrl, allowed)) {
                        KromiumLogger.w(TAG, "Navigation blocked by host lock: $targetUrl (allowed: $allowed)")
                        return true // Block navigation
                    }
                }

                val override = shouldOverrideUrlLoading ?: return false
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
                // 1. If an explicit custom authListener is set, let it handle the challenge first
                val listener = authListener
                if (listener != null) {
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

                // 2. Automated Proxy Authentication fallback:
                // If it's a proxy challenge and credentials were configured in KromiumProxy,
                // automatically supply them so authenticated proxies connect seamlessly.
                if (isProxy) {
                    val creds = activeProxy.getCredentials(host, port)
                        ?: Kromium.activeProxy.getCredentials(host, port)
                    if (creds != null) {
                        KromiumLogger.d(TAG, "Supplying configured proxy credentials for $host:$port (user: ${creds.first})")
                        callback?.Continue(creds.first, creds.second)
                        return true
                    }
                }

                callback?.cancel()
                return false
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

                val targetDir = if (downloadDirectory.canWrite() || downloadDirectory.mkdirs()) {
                    downloadDirectory
                } else {
                    resolveDefaultDownloadDirectory()
                }
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

                return when {
                    customPath != null && customPath.isBlank() -> {
                        KromiumLogger.i(TAG, "Download canceled: $cleanName")
                        callback.Continue("", false)
                        false
                    }
                    customPath != null -> {
                        KromiumLogger.i(TAG, "Downloading $cleanName to custom path: $customPath")
                        callback.Continue(customPath, false)
                        true
                    }
                    else -> {
                        KromiumLogger.i(TAG, "Downloading $cleanName to: $defaultTargetPath")
                        callback.Continue(defaultTargetPath, false)
                        true
                    }
                }
            }

            override fun onDownloadUpdated(
                browser: CefBrowser?,
                downloadItem: CefDownloadItem?,
                callback: CefDownloadItemCallback?
            ) {
                if (downloadItem == null) return

                if (callback != null) {
                    if (downloadItem.isComplete || downloadItem.isCanceled) {
                        globalDownloadCallbacks.remove(downloadItem.id)
                        pausedDownloads.remove(downloadItem.id)
                    } else {
                        globalDownloadCallbacks[downloadItem.id] = callback
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
                    isCanceled = downloadItem.isCanceled,
                    isPaused = pausedDownloads.contains(downloadItem.id)
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
        if (isOffScreenRendered && !hasJoglSupport) {
            KromiumLogger.w(
                TAG,
                "isOffScreenRendered requested but JOGL (com.jogamp.opengl.GLEventListener) is not present on classpath. " +
                    "Transparently falling back to zero-dependency offscreen Swing native peer browser."
            )
            return createHeadlessBrowser(
                url = url,
                requestContext = requestContext
            )
        }

        val rendering = if (isOffScreenRendered) CefRendering.OFFSCREEN else CefRendering.DEFAULT
        val effectiveContext = requestContext ?: this.requestContext
        val browser = if (effectiveContext != null) {
            rawClient.createBrowser(url, rendering, isTransparent, effectiveContext)
        } else {
            rawClient.createBrowser(url, rendering, isTransparent)
        }
        return KromiumBrowser(this, browser)
    }

    /**
     * Creates a headless browser instance backed by an off-screen Swing native window peer (`JWindow`).
     *
     * This provides 100% reliable, zero-dependency background page loading, JavaScript evaluation,
     * DOM extraction, and rendering without requiring JOGL native libraries on the classpath.
     */
    @JvmOverloads
    fun createHeadlessBrowser(
        url: String? = "about:blank",
        width: Int = 1280,
        height: Int = 800,
        requestContext: CefRequestContext? = null
    ): KromiumBrowser {
        val effectiveContext = requestContext ?: this.requestContext
        val browser = if (effectiveContext != null) {
            rawClient.createBrowser(url, CefRendering.DEFAULT, false, effectiveContext)
        } else {
            rawClient.createBrowser(url, CefRendering.DEFAULT, false)
        }

        var hostWindow: javax.swing.JWindow? = null
        val setupPeer = Runnable {
            try {
                val win = javax.swing.JWindow().apply {
                    // Position far outside visible desktop bounds
                    setLocation(-20000, -20000)
                    setSize(width, height)
                    contentPane.layout = java.awt.BorderLayout()
                    contentPane.add(browser.uiComponent, java.awt.BorderLayout.CENTER)
                    browser.uiComponent.setBounds(0, 0, width, height)
                    isVisible = true
                    validate()
                }
                hostWindow = win
            } catch (t: Throwable) {
                KromiumLogger.w(TAG, "Failed to initialize headless JWindow peer: ${t.message}")
            }
        }

        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            setupPeer.run()
        } else {
            try {
                javax.swing.SwingUtilities.invokeAndWait(setupPeer)
            } catch (t: Throwable) {
                KromiumLogger.w(TAG, "EDT invocation failed for headless JWindow setup: ${t.message}")
            }
        }

        return KromiumBrowser(this, browser, hostPeer = hostWindow)
    }

    fun createBrowser(): KromiumBrowser = createBrowser("about:blank")

    fun createBrowser(url: String?): KromiumBrowser =
        createBrowser(url = url, isOffScreenRendered = false, isTransparent = false, requestContext = null)

    fun createBrowser(
        url: String?,
        isTransparent: Boolean
    ): KromiumBrowser = createBrowser(url = url, isOffScreenRendered = false, isTransparent = isTransparent)

    fun createBrowser(
        url: String?,
        isOffScreenRendered: Boolean,
        isTransparent: Boolean
    ): KromiumBrowser = createBrowser(url = url, isOffScreenRendered = isOffScreenRendered, isTransparent = isTransparent, requestContext = null)

    fun addLoadHandler(handler: CefLoadHandler) = apply { loadHandlers.add(handler) }
    fun removeLoadHandler(handler: CefLoadHandler) = apply { loadHandlers.remove(handler) }
    fun removeLoadHandler() = apply { loadHandlers.clear() }

    fun addDisplayHandler(handler: CefDisplayHandler) = apply { displayHandlers.add(handler) }
    fun removeDisplayHandler(handler: CefDisplayHandler) = apply { displayHandlers.remove(handler) }
    fun removeDisplayHandler() = apply { displayHandlers.clear() }

    fun addLifeSpanHandler(handler: CefLifeSpanHandler) = apply { lifeSpanHandlers.add(handler) }
    fun removeLifeSpanHandler(handler: CefLifeSpanHandler) = apply { lifeSpanHandlers.remove(handler) }
    fun removeLifeSpanHandler() = apply { lifeSpanHandlers.clear() }

    fun addContextMenuHandler(handler: CefContextMenuHandler) = apply { contextMenuHandlers.add(handler) }
    fun removeContextMenuHandler(handler: CefContextMenuHandler) = apply { contextMenuHandlers.remove(handler) }
    fun removeContextMenuHandler() = apply { contextMenuHandlers.clear() }

    fun addFocusHandler(handler: CefFocusHandler) = apply { focusHandlers.add(handler) }
    fun removeFocusHandler(handler: CefFocusHandler) = apply { focusHandlers.remove(handler) }
    fun removeFocusHandler() = apply { focusHandlers.clear() }

    fun addKeyboardHandler(handler: CefKeyboardHandler) = apply { keyboardHandlers.add(handler) }
    fun removeKeyboardHandler(handler: CefKeyboardHandler) = apply { keyboardHandlers.remove(handler) }
    fun removeKeyboardHandler() = apply { keyboardHandlers.clear() }

    private fun setupCompositeHandlers() {
        rawClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                for (h in loadHandlers) {
                    try {
                        h.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward)
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onLoadingStateChange handler", e)
                    }
                }
            }

            override fun onLoadStart(
                browser: CefBrowser?,
                frame: CefFrame?,
                transitionType: org.cef.network.CefRequest.TransitionType?
            ) {
                for (h in loadHandlers) {
                    try {
                        h.onLoadStart(browser, frame, transitionType)
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onLoadStart handler", e)
                    }
                }
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                for (h in loadHandlers) {
                    try {
                        h.onLoadEnd(browser, frame, httpStatusCode)
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onLoadEnd handler", e)
                    }
                }
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?
            ) {
                if (frame?.isMain == true) {
                    loadErrorListener?.let { listener ->
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
                }

                for (h in loadHandlers) {
                    try {
                        h.onLoadError(browser, frame, errorCode, errorText, failedUrl)
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onLoadError handler", e)
                    }
                }
            }
        })

        rawClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {
                for (h in displayHandlers) {
                    try { h.onAddressChange(browser, frame, url) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onAddressChange handler", e)
                    }
                }
            }

            override fun onTitleChange(browser: CefBrowser?, title: String?) {
                for (h in displayHandlers) {
                    try { h.onTitleChange(browser, title) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onTitleChange handler", e)
                    }
                }
            }

            override fun onFullscreenModeChange(browser: CefBrowser?, fullscreen: Boolean) {
                for (h in displayHandlers) {
                    try { h.onFullscreenModeChange(browser, fullscreen) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onFullscreenModeChange handler", e)
                    }
                }
            }

            override fun onTooltip(browser: CefBrowser?, text: String?): Boolean {
                var handled = false
                for (h in displayHandlers) {
                    try {
                        if (h.onTooltip(browser, text)) handled = true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onTooltip handler", e)
                    }
                }
                return handled
            }

            override fun onStatusMessage(browser: CefBrowser?, value: String?) {
                for (h in displayHandlers) {
                    try { h.onStatusMessage(browser, value) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onStatusMessage handler", e)
                    }
                }
            }

            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: org.cef.CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                consoleMessageListener?.let { listener ->
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
                }

                var consumed = false
                for (h in displayHandlers) {
                    try {
                        if (h.onConsoleMessage(browser, level, message, source, line)) consumed = true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onConsoleMessage handler", e)
                    }
                }
                return consumed
            }

            override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean {
                var consumed = false
                for (h in displayHandlers) {
                    try {
                        if (h.onCursorChange(browser, cursorType)) consumed = true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onCursorChange handler", e)
                    }
                }
                return consumed
            }
        })

        rawClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                target_url: String?,
                target_frame_name: String?
            ): Boolean {
                val url = target_url ?: return true
                for (h in lifeSpanHandlers) {
                    try {
                        if (h.onBeforePopup(browser, frame, target_url, target_frame_name)) return true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onBeforePopup handler", e)
                    }
                }

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

            override fun onAfterCreated(browser: CefBrowser?) {
                for (h in lifeSpanHandlers) {
                    try { h.onAfterCreated(browser) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onAfterCreated handler", e)
                    }
                }
            }

            override fun onAfterParentChanged(browser: CefBrowser?) {
                for (h in lifeSpanHandlers) {
                    try { h.onAfterParentChanged(browser) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onAfterParentChanged handler", e)
                    }
                }
            }

            override fun doClose(browser: CefBrowser?): Boolean {
                var close = false
                for (h in lifeSpanHandlers) {
                    try {
                        if (h.doClose(browser)) close = true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in doClose handler", e)
                    }
                }
                return close
            }

            override fun onBeforeClose(browser: CefBrowser?) {
                for (h in lifeSpanHandlers) {
                    try { h.onBeforeClose(browser) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onBeforeClose handler", e)
                    }
                }
            }
        })

        rawClient.addContextMenuHandler(object : CefContextMenuHandlerAdapter() {
            override fun onBeforeContextMenu(
                browser: CefBrowser?,
                frame: CefFrame?,
                params: org.cef.callback.CefContextMenuParams?,
                model: org.cef.callback.CefMenuModel?
            ) {
                if (!enableContextMenus) {
                    model?.clear()
                }
                for (h in contextMenuHandlers) {
                    try { h.onBeforeContextMenu(browser, frame, params, model) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onBeforeContextMenu handler", e)
                    }
                }
            }

            override fun onContextMenuCommand(
                browser: CefBrowser?,
                frame: CefFrame?,
                params: org.cef.callback.CefContextMenuParams?,
                commandId: Int,
                eventFlags: Int
            ): Boolean {
                var handled = false
                for (h in contextMenuHandlers) {
                    try {
                        if (h.onContextMenuCommand(browser, frame, params, commandId, eventFlags)) handled = true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onContextMenuCommand handler", e)
                    }
                }
                return handled
            }

            override fun onContextMenuDismissed(browser: CefBrowser?, frame: CefFrame?) {
                for (h in contextMenuHandlers) {
                    try { h.onContextMenuDismissed(browser, frame) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onContextMenuDismissed handler", e)
                    }
                }
            }
        })

        rawClient.addFocusHandler(object : CefFocusHandlerAdapter() {
            override fun onTakeFocus(browser: CefBrowser?, next: Boolean) {
                for (h in focusHandlers) {
                    try { h.onTakeFocus(browser, next) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onTakeFocus handler", e)
                    }
                }
            }

            override fun onSetFocus(browser: CefBrowser?, source: org.cef.handler.CefFocusHandler.FocusSource?): Boolean {
                var handled = false
                for (h in focusHandlers) {
                    try {
                        if (h.onSetFocus(browser, source)) handled = true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onSetFocus handler", e)
                    }
                }
                return handled
            }

            override fun onGotFocus(browser: CefBrowser?) {
                for (h in focusHandlers) {
                    try { h.onGotFocus(browser) } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onGotFocus handler", e)
                    }
                }
            }
        })

        rawClient.addKeyboardHandler(object : CefKeyboardHandlerAdapter() {
            override fun onPreKeyEvent(
                browser: CefBrowser?,
                event: org.cef.handler.CefKeyboardHandler.CefKeyEvent?,
                isKeyboardShortcut: BoolRef?
            ): Boolean {
                var handled = false
                for (h in keyboardHandlers) {
                    try {
                        if (h.onPreKeyEvent(browser, event, isKeyboardShortcut)) handled = true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onPreKeyEvent handler", e)
                    }
                }
                return handled
            }

            override fun onKeyEvent(
                browser: CefBrowser?,
                event: org.cef.handler.CefKeyboardHandler.CefKeyEvent?
            ): Boolean {
                var handled = false
                for (h in keyboardHandlers) {
                    try {
                        if (h.onKeyEvent(browser, event)) handled = true
                    } catch (e: Throwable) {
                        KromiumLogger.e(TAG, "Exception in onKeyEvent handler", e)
                    }
                }
                return handled
            }
        })
    }

    fun dispose() {
        try {
            loadHandlers.clear()
            displayHandlers.clear()
            lifeSpanHandlers.clear()
            contextMenuHandlers.clear()
            focusHandlers.clear()
            keyboardHandlers.clear()
            rawClient.dispose()
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Error during client disposal", e)
        }
    }

    companion object {
        /**
         * Checks whether JOGL (Java OpenGL) runtime classes are present on the classpath.
         * Required for CEF native offscreen rendering (OSR); if absent, headless mode safely falls back to Swing native peer.
         */
        @JvmStatic
        val hasJoglSupport: Boolean by lazy {
            try {
                Class.forName("com.jogamp.opengl.GLEventListener")
                true
            } catch (_: Throwable) {
                false
            }
        }

        private val globalDownloadCallbacks = java.util.concurrent.ConcurrentHashMap<Int, CefDownloadItemCallback>()
        private val pausedDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

        @JvmStatic
        fun cancelDownloadGlobally(downloadId: Int): Boolean {
            val cb = globalDownloadCallbacks[downloadId] ?: return false
            return try {
                cb.cancel()
                pausedDownloads.remove(downloadId)
                true
            } catch (e: Throwable) {
                KromiumLogger.w(TAG, "Failed to cancel download $downloadId", e)
                false
            }
        }

        @JvmStatic
        fun pauseDownloadGlobally(downloadId: Int): Boolean {
            val cb = globalDownloadCallbacks[downloadId] ?: return false
            return try {
                cb.pause()
                pausedDownloads.add(downloadId)
                true
            } catch (e: Throwable) {
                KromiumLogger.w(TAG, "Failed to pause download $downloadId", e)
                false
            }
        }

        @JvmStatic
        fun resumeDownloadGlobally(downloadId: Int): Boolean {
            val cb = globalDownloadCallbacks[downloadId] ?: return false
            return try {
                cb.resume()
                pausedDownloads.remove(downloadId)
                true
            } catch (e: Throwable) {
                KromiumLogger.w(TAG, "Failed to resume download $downloadId", e)
                false
            }
        }

        @JvmStatic
        fun isDownloadPausedGlobally(downloadId: Int): Boolean = pausedDownloads.contains(downloadId)

        @JvmStatic
        fun resolveDefaultDownloadDirectory(): java.io.File {
            val rawHome = System.getProperty("user.home") ?: "."
            if (rawHome.contains("..")) {
                return java.io.File("Downloads").canonicalFile.apply { mkdirs() }
            }
            val homeDir = java.io.File(rawHome).canonicalFile
            if (homeDir.path.contains("..")) {
                return java.io.File("Downloads").canonicalFile.apply { mkdirs() }
            }
            val userDownloads = java.io.File(homeDir, "Downloads").canonicalFile
            if (!userDownloads.canonicalPath.startsWith(homeDir.canonicalPath)) {
                return getFallbackDownloadDirectory()
            }
            return try {
                if (!userDownloads.exists()) userDownloads.mkdirs()
                if (userDownloads.canWrite()) {
                    userDownloads
                } else {
                    getFallbackDownloadDirectory()
                }
            } catch (_: Throwable) {
                getFallbackDownloadDirectory()
            }
        }

        private fun getFallbackDownloadDirectory(): java.io.File {
            val rawHome = System.getProperty("user.home") ?: "."
            if (rawHome.contains("..")) {
                return java.io.File("KromiumDownloads").canonicalFile.apply { mkdirs() }
            }
            val homeDir = java.io.File(rawHome).canonicalFile
            if (homeDir.path.contains("..")) {
                return java.io.File("KromiumDownloads").canonicalFile.apply { mkdirs() }
            }
            val fallback = java.io.File(homeDir, "KromiumDownloads").canonicalFile
            if (!fallback.canonicalPath.startsWith(homeDir.canonicalPath)) {
                return java.io.File("KromiumDownloads").canonicalFile.apply { mkdirs() }
            }
            if (!fallback.exists()) fallback.mkdirs()
            return fallback
        }
    }
}
