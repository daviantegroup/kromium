package dev.daviante.kromium.data.engine

import dev.daviante.kromium.core.util.FileUtils
import dev.daviante.kromium.core.util.PlatformDetector
import dev.daviante.kromium.domain.model.OperatingSystem

import java.io.File

object EngineRegistry {

    private const val LOCK_FILE_NAME = "install.lock"

    @JvmStatic
    fun defaultInstallDir(): File {
        val customProp = System.getProperty("kromium.install.dir")
        if (!customProp.isNullOrBlank() && !customProp.contains("..")) {
            val f = File(customProp).canonicalFile
            if (!f.path.contains("..")) {
                return f
            }
        }

        val platform = PlatformDetector.current()
        val rawHome = System.getProperty("user.home") ?: "."
        if (rawHome.contains("..")) {
            return File(".kromium/jcef-150-b11").canonicalFile
        }
        val homeDir = File(rawHome).canonicalFile
        if (homeDir.path.contains("..")) {
            return File(".kromium/jcef-150-b11").canonicalFile
        }

        val dotKromium = FileUtils.resolveChild(homeDir, ".kromium") ?: homeDir
        val baseDir = when (platform.os) {
            OperatingSystem.Windows -> {
                val rawAppData = System.getenv("APPDATA")
                val appDataDir = if (!rawAppData.isNullOrBlank() && !rawAppData.contains("..")) {
                    val f = File(rawAppData).canonicalFile
                    if (!f.path.contains("..")) f else null
                } else null
                if (appDataDir != null) {
                    val kDir = File(appDataDir, "Kromium").canonicalFile
                    if (kDir.canonicalPath.startsWith(appDataDir.canonicalPath)) kDir else dotKromium
                } else {
                    dotKromium
                }
            }
            OperatingSystem.MacOS -> {
                val appSupport = File(homeDir, "Library/Application Support").canonicalFile
                if (appSupport.canonicalPath.startsWith(homeDir.canonicalPath)) {
                    val kDir = File(appSupport, "Kromium").canonicalFile
                    if (kDir.canonicalPath.startsWith(appSupport.canonicalPath)) kDir else homeDir
                } else {
                    dotKromium
                }
            }
            OperatingSystem.Linux -> {
                val rawXdg = System.getenv("XDG_DATA_HOME")
                val xdgDir = if (!rawXdg.isNullOrBlank() && !rawXdg.contains("..")) {
                    val f = File(rawXdg).canonicalFile
                    if (!f.path.contains("..")) f else null
                } else null
                val localShare = FileUtils.resolveChild(homeDir, ".local/share/kromium") ?: homeDir
                if (xdgDir != null) {
                    val kDir = File(xdgDir, "kromium").canonicalFile
                    if (kDir.canonicalPath.startsWith(xdgDir.canonicalPath)) kDir else localShare
                } else {
                    localShare
                }
            }
        }
        val target = File(baseDir, "jcef-150-b11").canonicalFile
        return if (target.canonicalPath.startsWith(baseDir.canonicalPath)) {
            target
        } else {
            File(homeDir, ".kromium/jcef-150-b11").canonicalFile
        }
    }

    @JvmStatic
    fun isInstalled(installDir: File): Boolean {
        val safeDir = FileUtils.sanitizeDirectory(installDir) ?: return false
        if (!safeDir.exists() || !safeDir.isDirectory) return false

        val platform = PlatformDetector.current()
        fun checkFile(relative: String): Boolean {
            val file = FileUtils.resolveChild(safeDir, relative) ?: return false
            return file.exists()
        }

        val hasBinaries = when (platform.os) {
            OperatingSystem.Windows -> {
                checkFile("jcef.dll") ||
                    checkFile("libcef.dll") ||
                    checkFile("bin/jcef.dll") ||
                    checkFile("bin/libcef.dll")
            }
            OperatingSystem.MacOS -> {
                OperatingSystem.MacOS.ensureMacFrameworkLinks(safeDir)
                checkFile("Chromium Embedded Framework.framework") ||
                    checkFile("Frameworks/Chromium Embedded Framework.framework") ||
                    checkFile("Frameworks/cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework")
            }
            OperatingSystem.Linux -> {
                checkFile("libcef.so") ||
                    checkFile("libjcef.so") ||
                    checkFile("lib/libcef.so") ||
                    checkFile("lib/libjcef.so")
            }
        }
        if (!hasBinaries) return false

        val lock = FileUtils.resolveChild(safeDir, LOCK_FILE_NAME)
        if (lock == null || !lock.exists()) {
            // Framework binaries are present on disk; self-heal install.lock to prevent unnecessary re-download
            try {
                markInstalled(safeDir)
            } catch (_: Throwable) {}
        }

        return true
    }

    @JvmStatic
    fun markInstalled(installDir: File) {
        val safeDir = FileUtils.sanitizeDirectory(installDir)
            ?: throw IllegalArgumentException("Invalid or unsafe install directory: ${installDir.path}")
        val lock = FileUtils.resolveChild(safeDir, LOCK_FILE_NAME)
            ?: throw IllegalStateException("Cannot resolve lock file in: ${safeDir.path}")
        val info = KromiumEngine.getInfo(safeDir)
        val metadata = """
            {
                "jcefVersion": "${info.jcefVersion}",
                "cefVersion": "${info.cefVersion}",
                "chromiumVersion": "${info.chromiumVersion}",
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()
        lock.writeText(metadata)

        val platform = PlatformDetector.current()
        if (platform.os.isMacOS) {
            OperatingSystem.MacOS.ensureMacFrameworkLinks(safeDir)
            FileUtils.removeMacQuarantine(safeDir)
        }
    }

    @JvmStatic
    fun clearInstallation(installDir: File) {
        FileUtils.deleteDirectory(installDir)
    }
}
