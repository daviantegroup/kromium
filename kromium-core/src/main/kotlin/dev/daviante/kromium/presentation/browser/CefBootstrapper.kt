package dev.daviante.kromium.presentation.browser

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.util.FileUtils
import dev.daviante.kromium.core.util.JvmModuleOpener
import dev.daviante.kromium.core.util.PlatformDetector
import dev.daviante.kromium.domain.exception.KromiumException
import dev.daviante.kromium.domain.model.KromiumCustomScheme
import dev.daviante.kromium.domain.model.KromiumGpuMode
import dev.daviante.kromium.domain.model.KromiumProcessModel
import dev.daviante.kromium.domain.model.OperatingSystem
import dev.daviante.kromium.domain.model.PlatformInfo
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.SystemBootstrap
import org.cef.callback.CefSchemeRegistrar
import org.cef.handler.CefAppHandlerAdapter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object CefBootstrapper {

    private const val TAG = "CefBootstrapper"

    @JvmStatic
    @JvmOverloads
    fun bootstrap(
        installDir: File,
        cefArgs: List<String>,
        cefSettings: CefSettings,
        customSchemes: List<KromiumCustomScheme> = emptyList(),
        processModel: KromiumProcessModel = KromiumProcessModel.AUTO,
        gpuMode: KromiumGpuMode = KromiumGpuMode.COMPOSITING_DISABLED
    ): CefApp {
        // Ensure required JDK module packages are dynamically open to ALL-UNNAMED
        JvmModuleOpener.ensureModulesOpened()

        val platform = PlatformDetector.current()
        val os = platform.os
        val safeInstallDir = FileUtils.sanitizeDirectory(installDir) ?: installDir.canonicalFile

        // Configure JCEF System properties for org.cef.Startup
        if (os.isMacOS) {
            val macOs = os as OperatingSystem.MacOS
            macOs.ensureMacFrameworkLinks(safeInstallDir)
            val frameworkPath = macOs.getFrameworkPath(safeInstallDir, inFrameworks = true)
            val helperPath = macOs.getMainBundlePath(safeInstallDir)
            val jcefLibDir = File(safeInstallDir, "Home/lib").takeIf { it.exists() }
                ?: File(safeInstallDir, "lib")

            System.setProperty("ALT_CEF_FRAMEWORK_DIR", frameworkPath)
            System.setProperty("ALT_CEF_HELPER_APP_DIR", helperPath)
            System.setProperty("ALT_JCEF_LIB_DIR", jcefLibDir.canonicalPath)
        } else if (os.isLinux) {
            val cefDir = listOf(
                File(safeInstallDir, "lib/libcef.so"),
                File(safeInstallDir, "libcef.so")
            ).firstOrNull { it.exists() }?.parentFile ?: File(safeInstallDir, "lib")
            val jcefDir = listOf(
                File(safeInstallDir, "lib/libjcef.so"),
                File(safeInstallDir, "libjcef.so")
            ).firstOrNull { it.exists() }?.parentFile ?: cefDir
            val helperDir = listOf(
                File(safeInstallDir, "lib/jcef_helper"),
                File(safeInstallDir, "bin/jcef_helper"),
                File(safeInstallDir, "jcef_helper")
            ).firstOrNull { it.exists() }?.parentFile ?: cefDir

            System.setProperty("ALT_CEF_FRAMEWORK_DIR", cefDir.canonicalPath)
            System.setProperty("ALT_JCEF_LIB_DIR", jcefDir.canonicalPath)
            System.setProperty("ALT_CEF_HELPER_APP_DIR", helperDir.canonicalPath)
        } else if (os.isWindows) {
            val cefDir = listOf(
                File(safeInstallDir, "bin/libcef.dll"),
                File(safeInstallDir, "libcef.dll")
            ).firstOrNull { it.exists() }?.parentFile ?: File(safeInstallDir, "bin")
            val jcefDir = listOf(
                File(safeInstallDir, "bin/jcef.dll"),
                File(safeInstallDir, "jcef.dll")
            ).firstOrNull { it.exists() }?.parentFile ?: cefDir
            val helperDir = listOf(
                File(safeInstallDir, "bin/jcef_helper.exe"),
                File(safeInstallDir, "jcef_helper.exe")
            ).firstOrNull { it.exists() }?.parentFile ?: cefDir

            System.setProperty("ALT_CEF_FRAMEWORK_DIR", cefDir.canonicalPath)
            System.setProperty("ALT_JCEF_LIB_DIR", jcefDir.canonicalPath)
            System.setProperty("ALT_CEF_HELPER_APP_DIR", helperDir.canonicalPath)
        }

        // Enable preinit on any thread to avoid EDT deadlock during headless/background bootstrap
        System.setProperty("jcef_app_preinit_any", "true")

        // Resolve Out-of-Process Server (`cef_server`) and Process Isolation Model
        val serverPath = os.getServerPath(safeInstallDir)
        val isServerAvailable = serverPath != null && File(serverPath).exists()

        // Set ALT_CEF_SERVER_PATH before any CefApp or NativeServerManager methods are called,
        // as NativeServerManager.ALT_CEF_SERVER_PATH is a static final field loaded during <clinit>.
        if (serverPath != null) {
            System.setProperty("ALT_CEF_SERVER_PATH", serverPath)
        }

        val effectiveRemote = when (processModel) {
            KromiumProcessModel.OUT_OF_PROCESS -> {
                if (!isServerAvailable) {
                    KromiumLogger.w(TAG, "Out-of-process CEF requested, but cef_server executable not found at $serverPath. Falling back to in-process mode.")
                    false
                } else {
                    true
                }
            }
            KromiumProcessModel.AUTO -> {
                // In desktop UI toolkits (Compose Desktop, Swing, JavaFX, SWT), CEF multi-process architecture
                // isolates GPU rasterization and web renderers in dedicated `jcef_helper` subprocesses via
                // `--browser-subprocess-path`, while the browser host embeds natively in the host JVM.
                // JetBrains `cef_server` RPC mode requires a proprietary shared-memory CefNativeRenderHandler
                // and rejects Windowed and standard OSR rendering. Therefore AUTO uses in-process browser host.
                false
            }
            KromiumProcessModel.IN_PROCESS -> false
        }

        // Configure process isolation in JetBrains JCEF
        CefApp.setIsRemoteEnabled(effectiveRemote)
        KromiumLogger.i(
            TAG,
            "CEF process model initialized: ${if (effectiveRemote) "OUT_OF_PROCESS (remote cef_server)" else "IN_PROCESS (local)"}"
        )

        // Preload JAWT (Java AWT Native Library)
        loadNativeLibrary(installDir, "jawt", platform)

        // Preload GPU libraries into JVM only on macOS where dyld requires dylibs in Frameworks/Libraries.
        // On Windows and Linux, jcef_helper loads GPU libraries out-of-process; loading EGL/GLESv2 directly
        // into the JVM process injects ANGLE hooks that corrupt Skiko's Direct3D device (skiko-windows-x64.dll crash).
        if (os.isMacOS && !effectiveRemote && gpuMode != KromiumGpuMode.SOFTWARE && cefArgs.none { it.trim().equals("--disable-gpu", ignoreCase = true) }) {
            val macOs = os as OperatingSystem.MacOS
            val gpuLibPath = File(macOs.getFrameworkPath(installDir, inFrameworks = true), "Libraries")
            loadNativeLibrary(gpuLibPath, "EGL", platform)
            loadNativeLibrary(gpuLibPath, "GLESv2", platform)
            loadNativeLibrary(gpuLibPath, "vk_swiftshader", platform)
        }

        // Configure dynamic loader for JCEF
        SystemBootstrap.setLoader { libName ->
            if (!loadNativeLibrary(installDir, libName, platform)) {
                // Fallback to standard JVM library resolution
                System.loadLibrary(libName)
            }
        }

        // Configure default paths if not explicitly overridden
        if (cefSettings.locales_dir_path.isNullOrEmpty() && !os.isMacOS) {
            val binLocales = FileUtils.resolveChild(safeInstallDir, "bin/locales")
            val libLocales = FileUtils.resolveChild(safeInstallDir, "lib/locales")
            val rootLocales = FileUtils.resolveChild(safeInstallDir, "locales")
            val resolvedLocales = when {
                binLocales != null && binLocales.exists() -> binLocales
                libLocales != null && libLocales.exists() -> libLocales
                else -> rootLocales ?: File(safeInstallDir, "locales")
            }
            cefSettings.locales_dir_path = resolvedLocales.canonicalPath
        }

        if (cefSettings.resources_dir_path.isNullOrEmpty() && !os.isMacOS) {
            cefSettings.resources_dir_path = os.getResourcesPath(safeInstallDir)
        }

        if (cefSettings.browser_subprocess_path.isNullOrEmpty()) {
            val subProcessPath = os.getBrowserPath(safeInstallDir)
            val subProcessFile = try { File(subProcessPath).canonicalFile } catch (e: Exception) { File(subProcessPath) }
            if (!subProcessFile.exists()) {
                throw KromiumException.BootstrapFailed(
                    IllegalStateException("Browser subprocess executable not found at: $subProcessPath")
                )
            }
            cefSettings.browser_subprocess_path = subProcessFile.canonicalPath
        } else {
            val configuredFile = try { File(cefSettings.browser_subprocess_path).canonicalFile } catch (e: Exception) { File(cefSettings.browser_subprocess_path) }
            if (!configuredFile.exists()) {
                throw KromiumException.BootstrapFailed(
                    IllegalStateException("Configured browser subprocess executable not found at: ${cefSettings.browser_subprocess_path}")
                )
            }
        }

        // Prepare startup arguments with platform-specific fixed arguments
        val launchArgs = os.getFixedArgs(installDir, cefArgs)

        // Register custom schemes with Chromium's security manager before CefApp initialization
        if (customSchemes.isNotEmpty()) {
            try {
                val appHandler = object : CefAppHandlerAdapter(launchArgs.toTypedArray()) {
                    override fun onRegisterCustomSchemes(registrar: CefSchemeRegistrar) {
                        for (scheme in customSchemes) {
                            KromiumLogger.d(TAG, "Registering custom scheme with Chromium: ${scheme.schemeName}")
                            registrar.addCustomScheme(
                                scheme.schemeName,
                                scheme.isStandard,
                                scheme.isLocal,
                                scheme.isDisplayIsolated,
                                scheme.isSecure,
                                scheme.isCorsEnabled,
                                scheme.isCspBypassing,
                                scheme.isFetchEnabled
                            )
                        }
                    }
                }
                CefApp.addAppHandler(appHandler)
            } catch (e: IllegalStateException) {
                KromiumLogger.w(TAG, "CefApp already initialized; custom schemes could not be registered: ${e.message}")
            }
        }

        val started = CefApp.startup(launchArgs.toTypedArray())
        val isInitialized = try {
            CefApp.getState() == org.cef.CefApp.CefAppState.INITIALIZED
        } catch (e: Throwable) {
            false
        }

        if (!started && !isInitialized) {
            throw KromiumException.BootstrapFailed(
                IllegalStateException("CefApp.startup() returned false and engine is not initialized. Ensure valid arguments and environment.")
            )
        }

        // Load core Chromium binaries into JVM only when running in-process
        if (!effectiveRemote) {
            if (os.isWindows) {
                loadNativeLibrary(installDir, "chrome_elf", platform)
            }
            if (!os.isMacOS) {
                loadNativeLibrary(installDir, "libcef", platform)
            }
        }

        val app = try {
            CefApp.getInstance(launchArgs.toTypedArray(), cefSettings, safeInstallDir)
        } catch (e: NoSuchMethodError) {
            CefApp.getInstance()
        } catch (t: Throwable) {
            val message = t.message ?: ""
            if (message.contains("0x887a0005", ignoreCase = true) || message.contains("Direct3D", ignoreCase = true)) {
                KromiumLogger.e(
                    TAG,
                    "Detected Direct3D device removal/failure during CEF initialization. Consider configuring `processModel = KromiumProcessModel.OUT_OF_PROCESS` or `gpuMode = KromiumGpuMode.SOFTWARE`.",
                    t
                )
            } else {
                KromiumLogger.e(TAG, "Failed to instantiate CefApp", t)
            }
            throw KromiumException.BootstrapFailed(t)
        }

        // Attach listener to internal startup future for diagnostic logging
        try {
            val futureField = CefApp::class.java.getDeclaredField("ourStartupFeature")
            futureField.isAccessible = true
            val future = futureField.get(null) as? java.util.concurrent.CompletableFuture<*>
            future?.whenComplete { _, ex ->
                if (ex != null) {
                    KromiumLogger.e(TAG, "Native CEF startup future completed exceptionally", ex)
                }
            }
        } catch (_: Throwable) {}

        // Synchronously await native engine INITIALIZED state before returning to caller
        if (CefApp.getState() != CefApp.CefAppState.INITIALIZED) {
            val latch = CountDownLatch(1)
            app.onInitialization { state ->
                KromiumLogger.d(TAG, "CEF initialization state: $state")
                if (state == CefApp.CefAppState.INITIALIZED) {
                    latch.countDown()
                }
            }
            if (CefApp.getState() == CefApp.CefAppState.INITIALIZED) {
                latch.countDown()
            }
            val reached = latch.await(30, TimeUnit.SECONDS)
            if (!reached && CefApp.getState() != CefApp.CefAppState.INITIALIZED) {
                throw KromiumException.BootstrapFailed(
                    IllegalStateException("Chromium engine failed to reach INITIALIZED state within 30s (state: ${CefApp.getState()})")
                )
            }
        }

        return app
    }

    private fun loadNativeLibrary(dir: File, baseName: String, platform: PlatformInfo): Boolean {
        if (dir.path.contains("..") || baseName.contains("..") || baseName.contains("/") || baseName.contains("\\")) {
            return false
        }
        val canonicalDir = dir.canonicalFile
        if (canonicalDir.path.contains("..")) return false

        val ext = platform.os.dynamicLibraryExtension

        val searchDirs = mutableListOf<File>()
        searchDirs.add(canonicalDir)

        val binDir = File(canonicalDir, "bin").canonicalFile
        if (binDir.canonicalPath.startsWith(canonicalDir.canonicalPath) && binDir.exists()) searchDirs.add(binDir)

        val libDir = File(canonicalDir, "lib").canonicalFile
        if (libDir.canonicalPath.startsWith(canonicalDir.canonicalPath) && libDir.exists()) searchDirs.add(libDir)

        val homeLibDir = File(canonicalDir, "Home/lib").canonicalFile
        if (homeLibDir.canonicalPath.startsWith(canonicalDir.canonicalPath) && homeLibDir.exists()) searchDirs.add(homeLibDir)

        // Also check sibling bin/lib if dir points to a subfolder
        canonicalDir.parentFile?.let { parent ->
            val parentCanonical = parent.canonicalFile
            val pBin = File(parentCanonical, "bin").canonicalFile
            if (pBin.canonicalPath.startsWith(parentCanonical.canonicalPath) && pBin.exists() && !searchDirs.contains(pBin)) {
                searchDirs.add(pBin)
            }
            val pLib = File(parentCanonical, "lib").canonicalFile
            if (pLib.canonicalPath.startsWith(parentCanonical.canonicalPath) && pLib.exists() && !searchDirs.contains(pLib)) {
                searchDirs.add(pLib)
            }
        }

        // Include JVM home lib/bin paths for reliable JAWT resolution across all OSes
        val rawJavaHome = System.getProperty("java.home")
        val javaHome = if (!rawJavaHome.isNullOrBlank() && !rawJavaHome.contains("..")) {
            val jf = File(rawJavaHome).canonicalFile
            if (!jf.path.contains("..")) jf else null
        } else null

        if (javaHome != null && javaHome.exists()) {
            val jHomeCanonical = javaHome.canonicalFile
            val jBin = File(jHomeCanonical, "bin").canonicalFile
            if (jBin.canonicalPath.startsWith(jHomeCanonical.canonicalPath) && jBin.exists()) searchDirs.add(jBin)
            val jLib = File(jHomeCanonical, "lib").canonicalFile
            if (jLib.canonicalPath.startsWith(jHomeCanonical.canonicalPath) && jLib.exists()) searchDirs.add(jLib)
        }

        // On macOS, search Frameworks directories where native libraries may reside
        if (platform.os.isMacOS) {
            val frameworksDir = File(canonicalDir, "Frameworks").canonicalFile
            if (frameworksDir.canonicalPath.startsWith(canonicalDir.canonicalPath) && frameworksDir.exists()) {
                searchDirs.add(frameworksDir)
                val directCefFrameworkLibs = File(frameworksDir, "Chromium Embedded Framework.framework/Libraries").canonicalFile
                if (directCefFrameworkLibs.canonicalPath.startsWith(frameworksDir.canonicalPath) && directCefFrameworkLibs.exists()) {
                    searchDirs.add(directCefFrameworkLibs)
                }
            }

            val cefServerFrameworks = File(canonicalDir, "Frameworks/cef_server.app/Contents/Frameworks").canonicalFile
            if (cefServerFrameworks.canonicalPath.startsWith(canonicalDir.canonicalPath) && cefServerFrameworks.exists()) {
                searchDirs.add(cefServerFrameworks)
                val cefFrameworkLibs = File(cefServerFrameworks, "Chromium Embedded Framework.framework/Libraries").canonicalFile
                if (cefFrameworkLibs.canonicalPath.startsWith(cefServerFrameworks.canonicalPath) && cefFrameworkLibs.exists()) {
                    searchDirs.add(cefFrameworkLibs)
                }
            }
        }

        for (searchDir in searchDirs) {
            val sCanonical = searchDir.canonicalFile
            val candidates = listOf(
                File(sCanonical, "$baseName$ext").canonicalFile,
                File(sCanonical, "lib$baseName$ext").canonicalFile,
                File(sCanonical, baseName).canonicalFile
            )

            for (file in candidates) {
                if (file.canonicalPath.startsWith(sCanonical.canonicalPath) && file.exists()) {
                    try {
                        System.load(file.canonicalPath)
                        return true
                    } catch (e: Throwable) {
                        KromiumLogger.d(TAG, "Failed loading candidate ${file.path}: ${e.message}")
                    }
                }
            }
        }

        return try {
            System.loadLibrary(baseName)
            true
        } catch (e: Throwable) {
            KromiumLogger.d(TAG, "System.loadLibrary($baseName) could not resolve library: ${e.message}")
            false
        }
    }
}
