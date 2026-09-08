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


import org.cef.CefApp
import org.cef.CefSettings
import org.cef.SystemBootstrap
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object CefBootstrapper {

    private const val TAG = "CefBootstrapper"

    @JvmStatic
    fun bootstrap(
        installDir: File,
        cefArgs: List<String>,
        cefSettings: CefSettings
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

        // Force local in-process mode (JetBrains JCEF defaults to remote cef_server.exe)
        CefApp.setIsRemoteEnabled(false)

        // Preload JAWT (Java AWT Native Library)
        loadNativeLibrary(installDir, "jawt", platform)

        // Preload GPU libraries if not explicitly disabled
        if (cefArgs.none { it.trim().equals("--disable-gpu", ignoreCase = true) }) {
            val gpuLibPath = if (os.isMacOS) {
                val macOs = os as OperatingSystem.MacOS
                File(macOs.getFrameworkPath(installDir, inFrameworks = true), "Libraries")
            } else {
                installDir
            }
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

        // Prepare startup arguments
        val launchArgs = if (os.isMacOS) {
            (os as OperatingSystem.MacOS).getFixedArgs(installDir, cefArgs)
        } else {
            cefArgs
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

        // Load core Chromium binaries
        if (os.isWindows) {
            loadNativeLibrary(installDir, "chrome_elf", platform)
        }
        if (!os.isMacOS) {
            loadNativeLibrary(installDir, "libcef", platform)
        }

        val app = try {
            CefApp.getInstance(launchArgs.toTypedArray(), cefSettings, safeInstallDir)
        } catch (e: NoSuchMethodError) {
            CefApp.getInstance()
        } catch (t: Throwable) {
            KromiumLogger.e(TAG, "Failed to instantiate CefApp", t)
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
