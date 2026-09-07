package dev.daviante.kromium.domain.model

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
import java.util.Locale

/**
 * Operating system identifier with platform-specific paths for CEF.
 */
sealed class OperatingSystem(val name: String, private vararg val aliases: String) {

    data object MacOS : OperatingSystem("mac", "mac", "darwin", "osx") {
        fun getFrameworkPath(installDir: File, inFrameworks: Boolean = false): String {
            val prefix = if (inFrameworks) "Frameworks/" else ""
            return "${installDir.canonicalPath}/$prefix" + "Chromium Embedded Framework.framework"
        }

        fun getMainBundlePath(installDir: File): String {
            return "${installDir.canonicalPath}/Frameworks/jcef Helper.app"
        }

        override fun getResourcesPath(installDir: File): String {
            return "${getFrameworkPath(installDir, inFrameworks = true)}/Resources"
        }

        override fun getBrowserPath(installDir: File): String {
            return "${installDir.canonicalPath}/Frameworks/jcef Helper.app/Contents/MacOS/jcef Helper"
        }

        override fun getFixedArgs(installDir: File, args: Collection<String>): Collection<String> {
            val list = args.toMutableList()
            list.add(0, "--framework-dir-path=${getFrameworkPath(installDir, inFrameworks = true)}")
            list.add(0, "--main-bundle-path=${getMainBundlePath(installDir)}")
            list.add(0, "--browser-subprocess-path=${getBrowserPath(installDir)}")
            return list
        }
    }

    data object Linux : OperatingSystem("linux", "linux") {
        override fun getBrowserPath(installDir: File): String {
            val safeBase = FileUtils.sanitizeDirectory(installDir) ?: installDir.canonicalFile
            val candidates = listOf(
                FileUtils.resolveChild(safeBase, "lib/jcef_helper"),
                FileUtils.resolveChild(safeBase, "bin/jcef_helper"),
                FileUtils.resolveChild(safeBase, "jcef_helper")
            ).filterNotNull()
            return (candidates.firstOrNull { it.exists() } ?: File(safeBase, "lib/jcef_helper")).canonicalPath
        }

        override fun getResourcesPath(installDir: File): String {
            val safeBase = FileUtils.sanitizeDirectory(installDir) ?: installDir.canonicalFile
            val libDir = FileUtils.resolveChild(safeBase, "lib")
            val pak = libDir?.let { FileUtils.resolveChild(it, "resources.pak") }
            if (libDir != null && pak != null && pak.exists()) {
                return libDir.canonicalPath
            }
            return safeBase.canonicalPath
        }
    }

    data object Windows : OperatingSystem("windows", "win", "windows") {
        override fun getBrowserPath(installDir: File): String {
            val safeBase = FileUtils.sanitizeDirectory(installDir) ?: installDir.canonicalFile
            val candidates = listOf(
                FileUtils.resolveChild(safeBase, "bin/jcef_helper.exe"),
                FileUtils.resolveChild(safeBase, "jcef_helper.exe"),
                FileUtils.resolveChild(safeBase, "bin/jcef_helper"),
                FileUtils.resolveChild(safeBase, "jcef_helper")
            ).filterNotNull()
            return (candidates.firstOrNull { it.exists() } ?: File(safeBase, "bin/jcef_helper.exe")).canonicalPath
        }

        override fun getResourcesPath(installDir: File): String {
            val safeBase = FileUtils.sanitizeDirectory(installDir) ?: installDir.canonicalFile
            val binDir = FileUtils.resolveChild(safeBase, "bin")
            val pak = binDir?.let { FileUtils.resolveChild(it, "resources.pak") }
            if (binDir != null && pak != null && pak.exists()) {
                return binDir.canonicalPath
            }
            return safeBase.canonicalPath
        }
    }

    fun matches(osName: String): Boolean {
        val lower = osName.lowercase(Locale.ENGLISH)
        return aliases.any { lower.startsWith(it) || lower.contains(it) }
    }

    val isMacOS: Boolean get() = this is MacOS
    val isLinux: Boolean get() = this is Linux
    val isWindows: Boolean get() = this is Windows

    open fun getFixedArgs(installDir: File, args: Collection<String>): Collection<String> = args

    open fun getResourcesPath(installDir: File): String = installDir.canonicalPath

    open fun getBrowserPath(installDir: File): String = File(installDir, "jcef_helper").canonicalPath

    val dynamicLibraryExtension: String
        get() = when (this) {
            Windows -> ".dll"
            Linux -> ".so"
            MacOS -> ".dylib"
        }

    override fun toString(): String = when (this) {
        MacOS -> "MacOS"
        Linux -> "Linux"
        Windows -> "Windows"
    }

    companion object {
        fun fromSystem(osName: String = System.getProperty("os.name") ?: ""): OperatingSystem? {
            return when {
                MacOS.matches(osName) -> MacOS
                Linux.matches(osName) -> Linux
                Windows.matches(osName) -> Windows
                else -> null
            }
        }
    }
}

