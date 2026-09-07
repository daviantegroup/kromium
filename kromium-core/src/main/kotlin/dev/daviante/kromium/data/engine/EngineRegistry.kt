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
        return File(baseDir, "jcef-150-b11")
    }

    fun isInstalled(installDir: File): Boolean {
        if (!installDir.exists() || !installDir.isDirectory) return false
        val lock = File(installDir, LOCK_FILE_NAME)
        if (!lock.exists()) return false

        val platform = PlatformDetector.current()
        return when (platform.os) {
            OperatingSystem.Windows -> {
                File(installDir, "jcef.dll").exists() ||
                    File(installDir, "libcef.dll").exists() ||
                    File(installDir, "bin/jcef.dll").exists() ||
                    File(installDir, "bin/libcef.dll").exists()
            }
            OperatingSystem.MacOS -> {
                File(installDir, "Chromium Embedded Framework.framework").exists() ||
                    File(installDir, "Frameworks/Chromium Embedded Framework.framework").exists()
            }
            OperatingSystem.Linux -> {
                File(installDir, "libcef.so").exists() ||
                    File(installDir, "libjcef.so").exists() ||
                    File(installDir, "lib/libcef.so").exists() ||
                    File(installDir, "lib/libjcef.so").exists()
            }
        }
    }

    fun markInstalled(installDir: File) {
        val lock = File(installDir, LOCK_FILE_NAME)
        val info = KromiumEngine.getInfo(installDir)
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
            FileUtils.removeMacQuarantine(installDir)
        }
    }

    fun clearInstallation(installDir: File) {
        FileUtils.deleteDirectory(installDir)
    }
}
