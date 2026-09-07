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


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cef.CefApp
import java.io.File

private const val TAG = "Kromium"

/**
 * Primary singleton coordinator for Kromium.
 *
 * Manages the lifecycle of the embedded Chromium engine: downloading,
 * extracting, bootstrapping, and graceful disposal.
 */
object Kromium {

    private val _state = MutableStateFlow<KromiumState>(KromiumState.Idle)
    val state: StateFlow<KromiumState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var cefApp: CefApp? = null

    private var _activeProxy: KromiumProxy = KromiumProxy.System
    val activeProxy: KromiumProxy get() = _activeProxy

    val isReady: Boolean get() = _state.value is KromiumState.Ready

    /**
     * Initializes the Kromium engine. This is idempotent — calling it when already
     * initialized or when initialization is in progress will wait for the result
     * instead of re-initializing.
     *
     * @throws KromiumException on any initialization failure
     */
    suspend fun initialize(configure: KromiumConfig.() -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        // Automatically open required java.desktop packages dynamically (Java 17/21+)
        JvmModuleOpener.ensureModulesOpened()

        val config = KromiumConfig().apply(configure)

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
                _state.first { it is KromiumState.Ready || it is KromiumState.Error }
                if (_state.value is KromiumState.Error) {
                    throw (_state.value as KromiumState.Error).cause
                }
                return@withContext
            }
            else -> { /* Proceed to acquire mutex */ }
        }

        mutex.withLock {
            // Double-check after acquiring the mutex (another coroutine may have finished)
            when (_state.value) {
                is KromiumState.Ready -> return@withContext
                is KromiumState.Disposed -> throw KromiumException.Disposed
                is KromiumState.Initializing, is KromiumState.Extracting,
                is KromiumState.Locating, is KromiumState.Downloading -> {
                    // Extremely unlikely but safe: release mutex and wait
                    // This shouldn't happen since we wait above, but as a safety net
                    return@withLock
                }
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
                val roots = File.listRoots() ?: emptyArray()
                val root = roots.firstOrNull { r ->
                    val rPath = r.canonicalPath
                    canonicalInstallPath.startsWith(rPath) && canonicalInstallPath.length > rPath.length
                } ?: throw KromiumException.InstallationFailed("Invalid installation directory: not within valid filesystem root")
                if (!canonicalInstallPath.startsWith(root.canonicalPath)) {
                    throw KromiumException.InstallationFailed("Invalid installation directory: root validation failed")
                }
                KromiumLogger.i(TAG, "Install directory: ${installDir.absolutePath}")

                if (!EngineRegistry.isInstalled(installDir)) {
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
                _activeProxy = config.proxy
                val app = CefBootstrapper.bootstrap(
                    installDir = installDir,
                    cefArgs = config.commandLineArgs,
                    cefSettings = config.toCefSettings()
                )
                cefApp = app

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
            _state.first { it is KromiumState.Ready || it is KromiumState.Error }
            if (_state.value is KromiumState.Error) {
                throw (_state.value as KromiumState.Error).cause
            }
        }
    }

    /**
     * Creates a new [KromiumClient] backed by a fresh CEF client instance.
     *
     * @throws KromiumException.NotInitialized if Kromium hasn't been initialized
     * @throws KromiumException.Disposed if Kromium has been disposed
     */
    fun newClient(): KromiumClient {
        if (_state.value is KromiumState.Disposed) throw KromiumException.Disposed
        val app = cefApp ?: throw KromiumException.NotInitialized
        return KromiumClient(app.createClient())
    }

    /**
     * Suspends until Kromium is ready, then creates a new [KromiumClient].
     *
     * @throws KromiumException if initialization failed
     */
    suspend fun awaitClient(): KromiumClient {
        if (!isReady) {
            val finalState = _state.first { it is KromiumState.Ready || it is KromiumState.Error }
            if (finalState is KromiumState.Error) {
                throw finalState.cause
            }
        }
        return newClient()
    }

    /**
     * Creates a new [KromiumBrowser] instance using a fresh client.
     */
    fun createBrowser(
        url: String? = "about:blank",
        isOffScreenRendered: Boolean = false,
        isTransparent: Boolean = false
    ): KromiumBrowser = newClient().createBrowser(url, isOffScreenRendered, isTransparent)

    /**
     * Creates a new zero-dependency headless [KromiumBrowser] instance backed by an off-screen Swing peer.
     */
    fun createHeadlessBrowser(
        url: String? = "about:blank",
        width: Int = 1280,
        height: Int = 800
    ): KromiumBrowser = newClient().createHeadlessBrowser(url, width, height)

    /**
     * Suspends until Kromium is ready, then creates a new [KromiumBrowser] instance.
     */
    suspend fun awaitBrowser(
        url: String? = "about:blank",
        isOffScreenRendered: Boolean = false,
        isTransparent: Boolean = false
    ): KromiumBrowser = awaitClient().createBrowser(url, isOffScreenRendered, isTransparent)

    /**
     * Suspends until Kromium is ready, then creates a new headless [KromiumBrowser] instance.
     */
    suspend fun awaitHeadlessBrowser(
        url: String? = "about:blank",
        width: Int = 1280,
        height: Int = 800
    ): KromiumBrowser = awaitClient().createHeadlessBrowser(url, width, height)

    /**
     * Dynamically updates the proxy strategy across all active browser windows
     * at runtime without restarting the engine or losing tab state.
     *
     * @param proxy The new [KromiumProxy] configuration to apply.
     * @return [Result.success] if applied, or [Result.failure] with error details.
     */
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
     * Disposes the Kromium engine. After calling this, no new clients or browsers
     * can be created.
     */
    fun dispose() {
        KromiumLogger.i(TAG, "Disposing Kromium...")
        disposeInternal()
        _state.value = KromiumState.Disposed
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
