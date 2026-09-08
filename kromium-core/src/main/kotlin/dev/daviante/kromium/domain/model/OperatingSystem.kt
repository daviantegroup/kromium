package dev.daviante.kromium.domain.model

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.util.FileUtils

import java.io.File
import java.util.Locale

/**
 * Operating system identifier with platform-specific paths for CEF.
 */
sealed class OperatingSystem(val name: String, private vararg val aliases: String) {

    data object MacOS : OperatingSystem("mac", "mac", "darwin", "osx") {
        /**
         * Resolves the actual directory where CEF frameworks and helper apps reside.
         * In JBR 25, they live inside cef_server.app/Contents/Frameworks.
         * In older JBR releases, they live directly under Frameworks.
         */
        private fun resolveCefFrameworksDir(installDir: File): File {
            val safeBase = FileUtils.sanitizeDirectory(installDir) ?: installDir.canonicalFile
            val cefServerDir = FileUtils.resolveChild(safeBase, "Frameworks/cef_server.app/Contents/Frameworks")
            return if (cefServerDir != null && cefServerDir.exists()) {
                cefServerDir
            } else {
                FileUtils.resolveChild(safeBase, "Frameworks") ?: File(safeBase, "Frameworks")
            }
        }

        /**
         * Ensures required symbolic links exist under Frameworks/ so dyld can locate
         * Chromium Embedded Framework.framework and helper apps via standard rpath.
         */
        fun ensureMacFrameworkLinks(installDir: File) {
            val safeBase = installDir.canonicalFile
            if (safeBase.path.contains("..")) return

            val frameworksDir = File(safeBase, "Frameworks").canonicalFile
            if (!frameworksDir.canonicalPath.startsWith(safeBase.canonicalPath)) return

            val cefServerFrameworks = File(frameworksDir, "cef_server.app/Contents/Frameworks").canonicalFile
            if (!cefServerFrameworks.canonicalPath.startsWith(frameworksDir.canonicalPath)) return

            if (cefServerFrameworks.exists() && cefServerFrameworks.isDirectory) {
                frameworksDir.mkdirs()
                val children = cefServerFrameworks.listFiles() ?: emptyArray()
                for (child in children) {
                    val childName = child.name
                    if (childName.contains("..") || childName.contains("/") || childName.contains("\\")) continue
                    val symlinkFile = File(frameworksDir, childName).canonicalFile
                    if (!symlinkFile.canonicalPath.startsWith(frameworksDir.canonicalPath)) continue
                    val childCanonical = child.canonicalFile
                    if (!childCanonical.canonicalPath.startsWith(cefServerFrameworks.canonicalPath)) continue

                    val linkTarget = java.nio.file.Path.of("cef_server.app/Contents/Frameworks/$childName")
                    // Remove broken or dangling symlink if present
                    if (java.nio.file.Files.isSymbolicLink(symlinkFile.toPath()) && !symlinkFile.exists()) {
                        try { java.nio.file.Files.delete(symlinkFile.toPath()) } catch (_: Throwable) {}
                    }

                    if (!symlinkFile.exists() && !java.nio.file.Files.isSymbolicLink(symlinkFile.toPath())) {
                        try {
                            java.nio.file.Files.createSymbolicLink(symlinkFile.toPath(), linkTarget)
                        } catch (e: Exception) {
                            try {
                                if (childCanonical.isDirectory) {
                                    childCanonical.copyRecursively(symlinkFile, overwrite = true)
                                } else {
                                    java.nio.file.Files.copy(childCanonical.toPath(), symlinkFile.toPath())
                                }
                            } catch (t: Throwable) {
                                KromiumLogger.w("MacOS", "Failed to link or copy $childName to Frameworks", t)
                            }
                        }
                    }
                }

                val rootFrameworkLink = File(safeBase, "Chromium Embedded Framework.framework").canonicalFile
                if (!rootFrameworkLink.canonicalPath.startsWith(safeBase.canonicalPath)) return

                if (java.nio.file.Files.isSymbolicLink(rootFrameworkLink.toPath()) && !rootFrameworkLink.exists()) {
                    try { java.nio.file.Files.delete(rootFrameworkLink.toPath()) } catch (_: Throwable) {}
                }
                if (!rootFrameworkLink.exists() && !java.nio.file.Files.isSymbolicLink(rootFrameworkLink.toPath())) {
                    try {
                        java.nio.file.Files.createSymbolicLink(
                            rootFrameworkLink.toPath(),
                            java.nio.file.Path.of("Frameworks/cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework")
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        fun getFrameworkPath(installDir: File, inFrameworks: Boolean = false): String {
            val safeBase = FileUtils.sanitizeDirectory(installDir) ?: installDir.canonicalFile
            val baseDir = if (inFrameworks) resolveCefFrameworksDir(safeBase) else safeBase
            val frameworkFile = FileUtils.resolveChild(baseDir, "Chromium Embedded Framework.framework")
                ?: File(baseDir, "Chromium Embedded Framework.framework")
            return frameworkFile.canonicalPath
        }

        fun getMainBundlePath(installDir: File): String {
            val frameworksDir = resolveCefFrameworksDir(installDir)
            val bundleFile = FileUtils.resolveChild(frameworksDir, "jcef Helper.app")
                ?: File(frameworksDir, "jcef Helper.app")
            return bundleFile.canonicalPath
        }

        override fun getResourcesPath(installDir: File): String {
            return "${getFrameworkPath(installDir, true)}/Resources"
        }

        override fun getBrowserPath(installDir: File): String {
            val frameworksDir = resolveCefFrameworksDir(installDir)
            val helperFile = FileUtils.resolveChild(frameworksDir, "jcef Helper.app/Contents/MacOS/jcef Helper")
                ?: File(frameworksDir, "jcef Helper.app/Contents/MacOS/jcef Helper")
            return helperFile.canonicalPath
        }

        override fun getFixedArgs(installDir: File, args: Collection<String>): Collection<String> {
            return (listOf(
                "--framework-dir-path=${getFrameworkPath(installDir, true)}",
                "--main-bundle-path=${getMainBundlePath(installDir)}",
                "--browser-subprocess-path=${getBrowserPath(installDir)}"
            ) + args)
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
            val candidates = listOf(
                FileUtils.resolveChild(safeBase, "lib/resources.pak")?.parentFile,
                FileUtils.resolveChild(safeBase, "resources.pak")?.parentFile,
                FileUtils.resolveChild(safeBase, "bin/resources.pak")?.parentFile
            ).filterNotNull()
            return (candidates.firstOrNull { it.exists() } ?: File(safeBase, "lib")).canonicalPath
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
            val candidates = listOf(
                FileUtils.resolveChild(safeBase, "bin/resources.pak")?.parentFile,
                FileUtils.resolveChild(safeBase, "resources.pak")?.parentFile,
                FileUtils.resolveChild(safeBase, "lib/resources.pak")?.parentFile
            ).filterNotNull()
            return (candidates.firstOrNull { it.exists() } ?: File(safeBase, "bin")).canonicalPath
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
