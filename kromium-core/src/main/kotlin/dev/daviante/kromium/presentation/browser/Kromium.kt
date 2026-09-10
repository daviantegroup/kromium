package dev.daviante.kromium.presentation.browser

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.util.FileUtils
import dev.daviante.kromium.core.util.FutureBridge
import dev.daviante.kromium.core.util.JvmModuleOpener
import dev.daviante.kromium.data.engine.EngineDownloader
import dev.daviante.kromium.data.engine.EngineExtractor
import dev.daviante.kromium.data.engine.EngineRegistry
import dev.daviante.kromium.domain.config.KromiumConfig
import dev.daviante.kromium.domain.config.KromiumProxy
import dev.daviante.kromium.domain.exception.KromiumException
import dev.daviante.kromium.domain.model.DownloadProgress
import dev.daviante.kromium.domain.model.KromiumState
import dev.daviante.kromium.presentation.scheme.KromiumAssetHandler
import dev.daviante.kromium.presentation.scheme.KromiumSchemeHandlerFactory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cef.CefApp
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

private const val TAG = "Kromium"

/**
 * Primary singleton coordinator for Kromium.
 *
 * Manages the lifecycle of the embedded Chromium engine: downloading,
 * extracting, bootstrapping, and graceful disposal.
 */
object Kromium {

    private val _state = MutableStateFlow<KromiumState>(KromiumState.Idle)
    @JvmStatic val state: StateFlow<KromiumState> = _state.asStateFlow()

    private class StateListenerWrapper(
        val consumer: Consumer<KromiumState>
    ) {
        private val lastDelivered = java.util.concurrent.atomic.AtomicReference<KromiumState?>()

        fun deliver(state: KromiumState) {
            val prev = lastDelivered.getAndSet(state)
            if (prev != state) {
                try {
                    consumer.accept(state)
                } catch (e: Throwable) {
                    KromiumLogger.w(TAG, "Exception in KromiumState listener", e)
                }
            }
        }
    }

    private val stateListeners = java.util.concurrent.ConcurrentHashMap<Consumer<KromiumState>, StateListenerWrapper>()

    init {
        CoroutineScope(Dispatchers.Default).launch {
            _state.collect { newState ->
                for (wrapper in stateListeners.values) {
                    wrapper.deliver(newState)
                }
            }
        }
    }

    /**
     * Registers a Java [Consumer] callback invoked whenever [KromiumState] transitions.
     * The listener is immediately invoked on the current state upon registration.
     *
     * @param consumer The Java callback accepting the new [KromiumState].
     * @return An [AutoCloseable] to unsubscribe the listener.
     */
    @JvmStatic
    fun addStateListener(consumer: Consumer<KromiumState>): AutoCloseable {
        val wrapper = StateListenerWrapper(consumer)
        stateListeners[consumer] = wrapper
        wrapper.deliver(_state.value)
        return AutoCloseable {
            stateListeners.remove(consumer)
        }
    }

    private val mutex = Mutex()
    private var cefApp: CefApp? = null
    private var _activeConfig: KromiumConfig? = null

    @Volatile
    private var _activeProxy: KromiumProxy = KromiumProxy.System
    @JvmStatic val activeProxy: KromiumProxy get() = _activeProxy

    @JvmStatic val isReady: Boolean get() = _state.value is KromiumState.Ready

    /**
     * Programmatically cancels an in-progress download identified by its download ID across any active browser session.
     */
    @JvmStatic
    fun cancelDownload(downloadId: Int): Boolean = KromiumClient.cancelDownloadGlobally(downloadId)

    /**
     * Programmatically pauses an in-progress download identified by its download ID across any active browser session.
     */
    @JvmStatic
    fun pauseDownload(downloadId: Int): Boolean = KromiumClient.pauseDownloadGlobally(downloadId)

    /**
     * Programmatically resumes a paused download identified by its download ID across any active browser session.
     */
    @JvmStatic
    fun resumeDownload(downloadId: Int): Boolean = KromiumClient.resumeDownloadGlobally(downloadId)

    /**
     * Checks whether an in-progress download is currently paused.
     */
    @JvmStatic
    fun isDownloadPaused(downloadId: Int): Boolean = KromiumClient.isDownloadPausedGlobally(downloadId)

    /**
     * Initializes the Kromium engine with a DSL configuration block.
     *
     * @throws KromiumException on any initialization failure
     */
    suspend fun initialize(configure: KromiumConfig.() -> Unit = {}) {
        initialize(KromiumConfig().apply(configure))
    }

    /**
     * Initializes the Kromium engine with a pre-configured [KromiumConfig].
     *
     * @throws KromiumException on any initialization failure
     */
    suspend fun initialize(config: KromiumConfig): Unit = withContext(Dispatchers.IO) {
        // Automatically open required java.desktop packages dynamically (Java 17/21+)
        JvmModuleOpener.ensureModulesOpened()

        // Validate configuration before acquiring the mutex
        config.validate()

        // Quick check without mutex: if already ready or disposed, handle immediately
        when (val currentState = _state.value) {
            is KromiumState.Ready -> return@withContext
            is KromiumState.Disposed -> throw KromiumException.Disposed
            is KromiumState.Initializing, is KromiumState.Extracting,
            is KromiumState.Locating, is KromiumState.Downloading -> {
                // Another coroutine is initializing — wait outside the mutex to avoid deadlock
                KromiumLogger.i(TAG, "Initialization already in progress, waiting for completion...")
                awaitReadyState()
                return@withContext
            }
            else -> { /* Proceed to acquire mutex */ }
        }

        mutex.withLock {
            // Double-check after acquiring the mutex (another coroutine may have finished)
            when (_state.value) {
                is KromiumState.Ready -> return@withLock
                is KromiumState.Disposed -> throw KromiumException.Disposed
                else -> { /* Proceed */ }
            }

            try {
                _state.value = KromiumState.Locating
                val rawInstallDir = config.installDir
                val rawPath = rawInstallDir.path
                if (rawPath.contains("..")) {
                    throw KromiumException.InstallationFailed("Invalid installation directory: traversal detected in $rawPath")
                }
                val installDir = rawInstallDir.canonicalFile
                val canonicalInstallPath = installDir.canonicalPath
                if (canonicalInstallPath.contains("..")) {
                    throw KromiumException.InstallationFailed("Invalid installation directory: traversal detected in $canonicalInstallPath")
                }
                val isUncPath = canonicalInstallPath.startsWith("\\\\")
                if (!isUncPath) {
                    val roots = File.listRoots() ?: emptyArray()
                    val root = roots.firstOrNull { r ->
                        val rPath = r.canonicalPath
                        canonicalInstallPath.startsWith(rPath) && canonicalInstallPath.length > rPath.length
                    } ?: throw KromiumException.InstallationFailed("Invalid installation directory: not within valid filesystem root")
                    if (!canonicalInstallPath.startsWith(root.canonicalPath)) {
                        throw KromiumException.InstallationFailed("Invalid installation directory: root validation failed")
                    }
                }
                KromiumLogger.i(TAG, "Install directory: ${installDir.absolutePath}")

                if (!EngineRegistry.isInstalled(installDir)) {
                    if (!config.autoDownload) {
                        throw KromiumException.AutoDownloadDisabled(installDir.absolutePath)
                    }
                    if (installDir.exists()) {
                        KromiumLogger.i(TAG, "Cleaning incomplete engine directory before installation: ${installDir.absolutePath}")
                        EngineRegistry.clearInstallation(installDir)
                    }

                    if (!FileUtils.ensureDirectory(installDir)) {
                        throw KromiumException.InstallationFailed(installDir.absolutePath)
                    }

                    val downloader = EngineDownloader()
                    try {
                        val resolvedPackage = if (!config.customBundleUrl.isNullOrBlank()) {
                            EngineDownloader.ResolvedPackage(config.customBundleUrl!!, config.customChecksumUrl)
                        } else {
                            downloader.resolvePackageUrl(releaseTag = config.releaseTag)
                        }
                        KromiumLogger.i(TAG, "Downloading engine bundle: ${resolvedPackage.bundleUrl}")

                        val tempArchive = File.createTempFile("kromium_bundle_", resolvedPackage.archiveExtension)
                        tempArchive.deleteOnExit()

                        try {
                            _state.value = KromiumState.Downloading(DownloadProgress.Initial)
                            downloader.downloadToFile(resolvedPackage, tempArchive) { progress ->
                                _state.value = KromiumState.Downloading(progress)
                            }

                            KromiumLogger.i(TAG, "Download complete, extracting...")
                            _state.value = KromiumState.Extracting
                            EngineExtractor.extractArchive(tempArchive, installDir)
                        } finally {
                            tempArchive.delete()
                        }
                    } finally {
                        downloader.close()
                    }
                } else {
                    KromiumLogger.i(TAG, "Engine already installed at: ${installDir.absolutePath}")
                }

                _state.value = KromiumState.Initializing
                KromiumLogger.i(TAG, "Bootstrapping CEF...")
                _activeConfig = config
                _activeProxy = config.proxy
                val app = CefBootstrapper.bootstrap(
                    installDir = installDir,
                    cefArgs = config.commandLineArgs,
                    cefSettings = config.toCefSettings(),
                    customSchemes = config.customSchemes,
                    processModel = config.processModel,
                    gpuMode = config.gpuMode
                )
                cefApp = app

                // Register any initial scheme handlers declared in config
                for (reg in config.schemeHandlers) {
                    registerSchemeHandlerInternal(app, reg.schemeName, reg.domainName, reg.handler)
                }

                // Mark engine as installed only after native bootstrap succeeds
                EngineRegistry.markInstalled(installDir)
                KromiumLogger.i(TAG, "Engine installed and verified successfully")

                Runtime.getRuntime().addShutdownHook(Thread {
                    disposeInternal()
                })

                _state.value = KromiumState.Ready
                KromiumLogger.i(TAG, "Kromium is ready")
            } catch (e: KromiumException) {
                KromiumLogger.e(TAG, "Initialization failed: ${e.message}", e)
                _state.value = KromiumState.Error(e)
                throw e
            } catch (e: Throwable) {
                KromiumLogger.e(TAG, "Initialization failed unexpectedly: ${e.message}", e)
                val wrapped = KromiumException.BootstrapFailed(e)
                _state.value = KromiumState.Error(wrapped)
                throw wrapped
            }
        }

        // If we exited withLock due to the in-progress safety net, wait here
        if (_state.value !is KromiumState.Ready) {
            awaitReadyState()
        }
    }

    /**
     * Asynchronously initializes the Kromium engine returning a Java [CompletableFuture].
     * Idempotent and non-blocking for Java callers.
     */
    @JvmStatic
    @JvmOverloads
    fun initializeAsync(config: KromiumConfig = KromiumConfig()): CompletableFuture<Void?> =
        FutureBridge.toCompletableFuture {
            initialize(config)
            null
        }

    /**
     * Asynchronously initializes the Kromium engine with a Java configuration lambda returning a [CompletableFuture].
     */
    @JvmStatic
    fun initializeAsync(configurer: Consumer<KromiumConfig>): CompletableFuture<Void?> {
        val config = KromiumConfig().apply { configurer.accept(this) }
        return initializeAsync(config)
    }

    /**
     * Creates a new [KromiumClient] backed by a fresh CEF client instance.
     *
     * @param isolated When true, instantiates an isolated [org.cef.browser.CefRequestContext] allowing
     *                 independent proxy configurations and cookie jars without affecting global browser routing.
     * @throws KromiumException.NotInitialized if Kromium hasn't been initialized
     * @throws KromiumException.Disposed if Kromium has been disposed
     */
    @JvmStatic
    @JvmOverloads
    fun newClient(isolated: Boolean = false): KromiumClient {
        if (_state.value is KromiumState.Disposed) throw KromiumException.Disposed
        val app = cefApp ?: throw KromiumException.NotInitialized
        val requestContext = if (isolated) {
            org.cef.browser.CefRequestContext.createContext(null)
        } else null
        val client = KromiumClient(app.createClient(), requestContext = requestContext)
        _activeConfig?.let { cfg ->
            if (cfg.emulateDesktopEnvironment) {
                client.emulateDesktopEnvironment = true
            }
            client.sslErrorPolicy = cfg.sslErrorPolicy
            client.doNotTrack = cfg.doNotTrack
        }
        return client
    }

    /**
     * Creates a new [KromiumClient] backed by an isolated request context.
     * Ensures changes to proxy or cookies do not impact other browser sessions.
     */
    @JvmStatic
    fun newIsolatedClient(): KromiumClient = newClient(isolated = true)

    /**
     * Suspends until Kromium is ready, then creates a new [KromiumClient].
     *
     * @param isolated When true, creates a client backed by an isolated request context.
     * @throws KromiumException if initialization failed
     */
    @JvmStatic
    @JvmOverloads
    suspend fun awaitClient(isolated: Boolean = false): KromiumClient {
        if (_state.value is KromiumState.Disposed) {
            throw KromiumException.Disposed
        }
        if (!isReady) {
            awaitReadyState()
        }
        return newClient(isolated = isolated)
    }

    /**
     * Asynchronously waits until Kromium is ready and returns a new [KromiumClient] via a [CompletableFuture].
     *
     * @param isolated When true, creates a client backed by an isolated request context.
     */
    @JvmStatic
    @JvmOverloads
    fun awaitClientAsync(isolated: Boolean = false): CompletableFuture<KromiumClient> =
        FutureBridge.toCompletableFuture { awaitClient(isolated = isolated) }

    /**
     * Creates a new [KromiumBrowser] instance using a fresh client.
     *
     * @param isolated When true, creates a browser backed by an isolated request context and cookie store.
     */
    @JvmStatic
    @JvmOverloads
    fun createBrowser(
        url: String? = "about:blank",
        isOffScreenRendered: Boolean = true,
        isTransparent: Boolean = false,
        isolated: Boolean = false
    ): KromiumBrowser = newClient(isolated = isolated).createBrowser(url, isOffScreenRendered, isTransparent)

    /**
     * Creates a new [KromiumBrowser] instance using an existing [KromiumClient].
     */
    @JvmStatic
    @JvmOverloads
    fun createBrowser(
        client: KromiumClient,
        url: String? = "about:blank",
        isOffScreenRendered: Boolean = true,
        isTransparent: Boolean = false
    ): KromiumBrowser = client.createBrowser(url, isOffScreenRendered, isTransparent)

    /**
     * Creates a new [KromiumBrowser] instance backed by an isolated session and cookie store.
     */
    @JvmStatic
    @JvmOverloads
    fun createIsolatedBrowser(
        url: String? = "about:blank",
        isOffScreenRendered: Boolean = true,
        isTransparent: Boolean = false
    ): KromiumBrowser = createBrowser(url, isOffScreenRendered, isTransparent, isolated = true)

    /**
     * Creates a new zero-dependency headless [KromiumBrowser] instance backed by an off-screen Swing peer.
     */
    @JvmStatic
    @JvmOverloads
    fun createHeadlessBrowser(
        url: String? = "about:blank",
        width: Int = 1280,
        height: Int = 800
    ): KromiumBrowser = newClient().createHeadlessBrowser(url, width, height)

    /**
     * Suspends until Kromium is ready, then creates a new [KromiumBrowser] instance.
     */
    @JvmStatic
    @JvmOverloads
    suspend fun awaitBrowser(
        url: String? = "about:blank",
        isOffScreenRendered: Boolean = true,
        isTransparent: Boolean = false
    ): KromiumBrowser = awaitClient().createBrowser(url, isOffScreenRendered, isTransparent)

    /**
     * Asynchronously waits until Kromium is ready, then creates a new [KromiumBrowser] instance.
     */
    @JvmStatic
    @JvmOverloads
    fun awaitBrowserAsync(
        url: String? = "about:blank",
        isOffScreenRendered: Boolean = true,
        isTransparent: Boolean = false
    ): CompletableFuture<KromiumBrowser> =
        FutureBridge.toCompletableFuture { awaitBrowser(url, isOffScreenRendered, isTransparent) }

    /**
     * Suspends until Kromium is ready, then creates a new headless [KromiumBrowser] instance.
     */
    @JvmStatic
    @JvmOverloads
    suspend fun awaitHeadlessBrowser(
        url: String? = "about:blank",
        width: Int = 1280,
        height: Int = 800
    ): KromiumBrowser = awaitClient().createHeadlessBrowser(url, width, height)

    /**
     * Suspends until Kromium is ready, creates a headless browser, and awaits navigation to [waitUntil].
     */
    @JvmStatic
    @JvmOverloads
    suspend fun awaitHeadlessBrowser(
        url: String?,
        waitUntil: NavigationStage,
        width: Int = 1280,
        height: Int = 800,
        timeoutMs: Long = 10_000L
    ): KromiumBrowser = awaitClient().createHeadlessBrowser(url, waitUntil, width, height, timeoutMs)

    /**
     * Asynchronously waits until Kromium is ready, then creates a new headless [KromiumBrowser] instance.
     */
    @JvmStatic
    @JvmOverloads
    fun awaitHeadlessBrowserAsync(
        url: String? = "about:blank",
        width: Int = 1280,
        height: Int = 800
    ): CompletableFuture<KromiumBrowser> =
        FutureBridge.toCompletableFuture { awaitHeadlessBrowser(url, width, height) }

    /**
     * Asynchronously waits until Kromium is ready, creates a headless browser, and awaits navigation to [waitUntil].
     */
    @JvmStatic
    @JvmOverloads
    fun awaitHeadlessBrowserAsync(
        url: String?,
        waitUntil: NavigationStage,
        width: Int = 1280,
        height: Int = 800,
        timeoutMs: Long = 10_000L
    ): CompletableFuture<KromiumBrowser> =
        FutureBridge.toCompletableFuture { awaitHeadlessBrowser(url, waitUntil, width, height, timeoutMs) }

    /**
     * Dynamically updates the proxy strategy across all active browser windows
     * at runtime without restarting the engine or losing tab state.
     *
     * @param proxy The new [KromiumProxy] configuration to apply.
     * @return [Result.success] if applied, or [Result.failure] with error details.
     */
    @JvmStatic
    fun setProxy(proxy: KromiumProxy): Result<Unit> {
        _activeProxy = proxy
        if (!isReady || cefApp == null) {
            return Result.failure(KromiumException.NotInitialized)
        }
        return try {
            val context = org.cef.browser.CefRequestContext.getGlobalContext()
                ?: return Result.failure(KromiumException.NotInitialized)
            val prefMap = proxy.toPreferenceMap()
            val error = context.setPreference("proxy", prefMap)
            if (error.isNullOrEmpty()) {
                KromiumLogger.i(TAG, "Proxy dynamically updated to: $proxy")
                Result.success(Unit)
            } else {
                KromiumLogger.e(TAG, "Failed to set dynamic proxy: $error")
                Result.failure(KromiumException.ProxyError("Failed to set proxy preference: $error"))
            }
        } catch (t: Throwable) {
            KromiumLogger.e(TAG, "Error applying dynamic proxy", t)
            Result.failure(t)
        }
    }

    /**
     * Dynamically updates the proxy strategy across all active browser windows
     * at runtime returning a boolean.
     * Provides 100% clean Java compatibility bypassing Kotlin Result value class mangling.
     */
    @JvmStatic
    @JvmName("updateProxy")
    fun updateProxy(proxy: KromiumProxy): Boolean {
        return setProxy(proxy).isSuccess
    }

    /**
     * Registers a virtual scheme handler factory with the Chromium engine.
     *
     * Enables serving bundled or virtual assets for the given scheme and optional domain name.
     *
     * Example:
     * ```kotlin
     * Kromium.registerSchemeHandler("app", "myapp", KromiumSchemeHandler.fromClasspath("web"))
     * ```
     *
     * @param schemeName The protocol scheme (e.g. "app", "https").
     * @param domainName Optional domain name (e.g. "myapp"), or null to match all domains for this scheme.
     * @param handler The [KromiumAssetHandler] that resolves and streams virtual responses.
     * @return True if registration succeeded.
     * @throws KromiumException.NotInitialized if Kromium has not been initialized yet.
     */
    @JvmStatic
    @JvmOverloads
    fun registerSchemeHandler(
        schemeName: String,
        domainName: String? = null,
        handler: KromiumAssetHandler
    ): Boolean {
        val app = cefApp ?: throw KromiumException.NotInitialized
        return registerSchemeHandlerInternal(app, schemeName, domainName, handler)
    }

    private fun registerSchemeHandlerInternal(
        app: CefApp,
        schemeName: String,
        domainName: String?,
        handler: KromiumAssetHandler
    ): Boolean {
        val factory = KromiumSchemeHandlerFactory(handler)
        val domain = domainName ?: ""
        KromiumLogger.i(TAG, "Registering scheme handler factory for $schemeName://$domain")
        return app.registerSchemeHandlerFactory(schemeName, domain, factory)
    }

    /**
     * Clears all registered scheme handler factories.
     */
    @JvmStatic
    fun clearSchemeHandlers(): Boolean {
        val app = cefApp ?: return false
        return app.clearSchemeHandlerFactories()
    }

    /**
     * Disposes the Kromium engine. After calling this, no new clients or browsers
     * can be created.
     */
    @JvmStatic
    fun dispose() {
        KromiumLogger.i(TAG, "Disposing Kromium...")
        disposeInternal()
        _state.value = KromiumState.Disposed
    }

    private suspend fun awaitReadyState(): KromiumState.Ready {
        val finalState = _state.first {
            it is KromiumState.Ready || it is KromiumState.Error || it is KromiumState.Disposed
        }
        return when (finalState) {
            is KromiumState.Ready -> finalState
            is KromiumState.Error -> throw finalState.cause
            is KromiumState.Disposed -> throw KromiumException.Disposed
            else -> throw IllegalStateException("Unexpected state: $finalState")
        }
    }

    private fun disposeInternal() {
        try {
            cefApp?.dispose()
            cefApp = null
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Error during CEF disposal", e)
        }
    }
}


