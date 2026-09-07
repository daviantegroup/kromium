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

    fun bootstrap(
        installDir: File,
        cefArgs: List<String>,
        cefSettings: CefSettings
    ): CefApp {
        val platform = PlatformDetector.current()
        val os = platform.os

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
        val safeInstallDir = FileUtils.sanitizeDirectory(installDir) ?: installDir.canonicalFile

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

        // Load core Chromium binary
        loadNativeLibrary(installDir, "libcef", platform)

        val app = runCatching { CefApp.getInstance(launchArgs.toTypedArray(), cefSettings, installDir) }.getOrNull()
            ?: CefApp.getInstance()

        // Synchronously await native engine INITIALIZED state before returning to caller
        if (CefApp.getState() != CefApp.CefAppState.INITIALIZED) {
            val latch = CountDownLatch(1)
            app.onInitialization { state ->
                if (state == CefApp.CefAppState.INITIALIZED) {
                    latch.countDown()
                }
            }
            if (CefApp.getState() == CefApp.CefAppState.INITIALIZED) {
                latch.countDown()
            }
            val reached = latch.await(15, TimeUnit.SECONDS)
            if (!reached && CefApp.getState() != CefApp.CefAppState.INITIALIZED) {
                KromiumLogger.w(TAG, "Chromium engine initialization in progress (state: ${CefApp.getState()})")
            }
        }

        return app
    }

    private fun loadNativeLibrary(dir: File, baseName: String, platform: PlatformInfo): Boolean {
        val ext = platform.os.dynamicLibraryExtension

        val searchDirs = mutableListOf<File>()
        searchDirs.add(dir)
        File(dir, "bin").takeIf { it.exists() }?.let { searchDirs.add(it) }
        File(dir, "lib").takeIf { it.exists() }?.let { searchDirs.add(it) }

        // Include JVM home lib/bin paths for reliable JAWT resolution across all OSes
        val javaHome = System.getProperty("java.home")?.let { File(it) }
        if (javaHome != null && javaHome.exists()) {
            File(javaHome, "bin").takeIf { it.exists() }?.let { searchDirs.add(it) }
            File(javaHome, "lib").takeIf { it.exists() }?.let { searchDirs.add(it) }
        }

        // On macOS, search Frameworks directories where native libraries may reside
        if (platform.os.isMacOS) {
            val frameworksDir = File(dir, "Frameworks")
            if (frameworksDir.exists()) searchDirs.add(frameworksDir)
            val cefServerFrameworks = File(dir, "Frameworks/cef_server.app/Contents/Frameworks")
            if (cefServerFrameworks.exists()) searchDirs.add(cefServerFrameworks)
            val cefFrameworkLibs = File(cefServerFrameworks, "Chromium Embedded Framework.framework/Libraries")
            if (cefFrameworkLibs.exists()) searchDirs.add(cefFrameworkLibs)
            val directCefFrameworkLibs = File(frameworksDir, "Chromium Embedded Framework.framework/Libraries")
            if (directCefFrameworkLibs.exists()) searchDirs.add(directCefFrameworkLibs)
        }

        for (searchDir in searchDirs) {
            val candidates = listOf(
                File(searchDir, "$baseName$ext"),
                File(searchDir, "lib$baseName$ext"),
                File(searchDir, baseName)
            )

            for (file in candidates) {
                if (file.exists()) {
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
