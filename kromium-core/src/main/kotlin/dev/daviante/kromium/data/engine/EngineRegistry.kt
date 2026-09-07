package dev.daviante.kromium.data.engine

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

import java.io.File

object EngineRegistry {

    private const val LOCK_FILE_NAME = "install.lock"

    fun defaultInstallDir(): File {
        val platform = PlatformDetector.current()
        val userHome = System.getProperty("user.home") ?: "."

        val baseDir = when (platform.os) {
            OperatingSystem.Windows -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "Kromium") else File(userHome, ".kromium")
            }
            OperatingSystem.MacOS -> {
                File(userHome, "Library/Application Support/Kromium")
            }
            OperatingSystem.Linux -> {
                val xdgData = System.getenv("XDG_DATA_HOME")
                if (!xdgData.isNullOrBlank()) File(xdgData, "kromium") else File(userHome, ".local/share/kromium")
            }
        }
        val target = File(baseDir, "jcef-150-b11")
        return target.canonicalFile
    }

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

    fun clearInstallation(installDir: File) {
        FileUtils.deleteDirectory(installDir)
    }
}
