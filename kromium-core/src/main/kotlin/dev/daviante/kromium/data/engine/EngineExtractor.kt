package dev.daviante.kromium.data.engine

import dev.daviante.kromium.domain.model.*
import dev.daviante.kromium.domain.config.*
import dev.daviante.kromium.domain.exception.*
import dev.daviante.kromium.core.logging.*
import dev.daviante.kromium.core.util.*

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object EngineExtractor {

    private const val TAG = "EngineExtractor"

    /**
     * Extracts an engine archive (supporting both .tar.gz and .zip formats) into [destinationDir].
     * Automatically detects the archive format via header magic bytes or file extension.
     */
    fun extractArchive(archiveFile: File, destinationDir: File, bufferSize: Int = 32 * 1024) {
        if (isZipArchive(archiveFile)) {
            KromiumLogger.d(TAG, "Extracting ZIP archive: ${archiveFile.name}")
            extractZip(archiveFile, destinationDir, bufferSize)
        } else {
            KromiumLogger.d(TAG, "Extracting TAR.GZ archive: ${archiveFile.name}")
            extractTarGz(archiveFile, destinationDir, bufferSize)
        }
    }

    private fun isZipArchive(file: File): Boolean {
        if (file.name.endsWith(".zip", ignoreCase = true)) return true
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                // ZIP magic bytes: PK\x03\x04 (0x50, 0x4B, 0x03, 0x04)
                read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts a ZIP archive into [destinationDir] with Zip-Slip protection,
     * Unix symlink support, and executable permission preservation.
     */
    fun extractZip(archiveFile: File, destinationDir: File, bufferSize: Int = 32 * 1024) {
        val safeDestination = FileUtils.sanitizeDirectory(destinationDir)
            ?: throw IllegalArgumentException("Invalid destination directory: ${destinationDir.path}")
        FileUtils.ensureDirectory(safeDestination)
        val destinationBasePath = safeDestination.toPath().toAbsolutePath().normalize()

        val deferredSymlinks = mutableListOf<Pair<java.nio.file.Path, java.nio.file.Path>>()

        ZipFile.builder().setFile(archiveFile).get().use { zipFile ->
            val entries = zipFile.entries
            val buffer = ByteArray(bufferSize)

            while (entries.hasMoreElements()) {
                val entry: ZipArchiveEntry = entries.nextElement()
                val entryName = entry.name

                // Prevent Zip-Slip directory traversal
                if (entryName.contains("..") || entryName.contains("\u0000")) {
                    throw KromiumException.MaliciousArchiveEntry(entryName)
                }

                val resolvedPath = destinationBasePath.resolve(entryName).normalize()
                if (!resolvedPath.startsWith(destinationBasePath)) {
                    throw KromiumException.MaliciousArchiveEntry(entryName)
                }

                val targetFile = resolvedPath.toFile()

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else if (entry.isUnixSymlink) {
                    val linkTarget = try {
                        zipFile.getUnixSymlink(entry)
                    } catch (e: Exception) {
                        null
                    }

                    if (linkTarget != null) {
                        if (linkTarget.contains("\u0000")) {
                            throw KromiumException.MaliciousArchiveEntry("Invalid null in link target: $entryName")
                        }

                        val parentDir = resolvedPath.parent ?: destinationBasePath
                        val targetPath = if (java.nio.file.Path.of(linkTarget).isAbsolute) {
                            java.nio.file.Path.of(linkTarget).normalize()
                        } else {
                            parentDir.resolve(linkTarget).normalize()
                        }

                        if (!targetPath.startsWith(destinationBasePath)) {
                            throw KromiumException.MaliciousArchiveEntry(
                                "Symlink points outside destination: $entryName -> $linkTarget"
                            )
                        }

                        targetFile.parentFile?.mkdirs()
                        if (targetFile.exists() || java.nio.file.Files.isSymbolicLink(resolvedPath)) {
                            try { java.nio.file.Files.delete(resolvedPath) } catch (_: Exception) {}
                        }

                        try {
                            java.nio.file.Files.createSymbolicLink(resolvedPath, java.nio.file.Path.of(linkTarget))
                        } catch (e: Exception) {
                            // Fallback for filesystems (e.g. Windows without developer mode)
                            deferredSymlinks.add(Pair(resolvedPath, targetPath))
                        }
                    }
                } else {
                    targetFile.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(targetFile).use { fos ->
                            var count: Int
                            while (input.read(buffer, 0, buffer.size).also { count = it } != -1) {
                                fos.write(buffer, 0, count)
                            }
                        }
                    }

                    // Check entry mode for POSIX executable permissions
                    val mode = entry.unixMode
                    val lowerName = entry.name.lowercase()
                    if ((mode and 0b001_000_000) != 0 || isExecutableBinaryName(lowerName)) {
                        FileUtils.makeExecutable(targetFile)
                    }
                }
            }
        }

        // Resolve any symlinks that could not be created directly
        for ((linkPath, targetPath) in deferredSymlinks) {
            try {
                if (java.nio.file.Files.exists(targetPath)) {
                    val linkFile = linkPath.toFile()
                    val target = targetPath.toFile()
                    if (target.isDirectory) {
                        target.copyRecursively(linkFile, overwrite = true)
                    } else {
                        java.nio.file.Files.copy(targetPath, linkPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            } catch (e: Exception) {
                KromiumLogger.w(TAG, "Could not resolve symlink fallback for: $linkPath -> $targetPath", e)
            }
        }

        // If the archive unpacked into a single nested subdirectory, flatten it
        flattenIfSingleChild(safeDestination)

        // Ensure all executables and native libraries have proper execute permissions
        ensureExecutablePermissions(safeDestination)

        // Ensure macOS framework symlinks are present
        if (PlatformDetector.current().os.isMacOS) {
            OperatingSystem.MacOS.ensureMacFrameworkLinks(safeDestination)
        }
    }

    fun extractTarGz(archiveFile: File, destinationDir: File, bufferSize: Int = 32 * 1024) {
        val safeDestination = FileUtils.sanitizeDirectory(destinationDir)
            ?: throw IllegalArgumentException("Invalid destination directory: ${destinationDir.path}")
        FileUtils.ensureDirectory(safeDestination)
        val destinationBasePath = safeDestination.toPath().toAbsolutePath().normalize()

        val deferredSymlinks = mutableListOf<Pair<java.nio.file.Path, java.nio.file.Path>>()

        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis, bufferSize).use { bis ->
                GzipCompressorInputStream(bis).use { gzis ->
                    TarArchiveInputStream(gzis).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextEntry
                        val buffer = ByteArray(bufferSize)

                        while (entry != null) {
                            val entryName = entry.name

                            // Prevent Zip-Slip directory traversal
                            if (entryName.contains("..") || entryName.contains("\u0000")) {
                                throw KromiumException.MaliciousArchiveEntry(entryName)
                            }

                            val resolvedPath = destinationBasePath.resolve(entryName).normalize()
                            if (!resolvedPath.startsWith(destinationBasePath)) {
                                throw KromiumException.MaliciousArchiveEntry(entryName)
                            }

                            val targetFile = resolvedPath.toFile()

                            if (entry.isDirectory) {
                                targetFile.mkdirs()
                            } else if (entry.isSymbolicLink || entry.isLink) {
                                val linkTarget = entry.linkName
                                if (linkTarget.contains("\u0000")) {
                                    throw KromiumException.MaliciousArchiveEntry("Invalid null in link target: $entryName")
                                }

                                val parentDir = resolvedPath.parent ?: destinationBasePath
                                val targetPath = if (java.nio.file.Path.of(linkTarget).isAbsolute) {
                                    java.nio.file.Path.of(linkTarget).normalize()
                                } else {
                                    parentDir.resolve(linkTarget).normalize()
                                }

                                if (!targetPath.startsWith(destinationBasePath)) {
                                    throw KromiumException.MaliciousArchiveEntry(
                                        "Symlink points outside destination: $entryName -> $linkTarget"
                                    )
                                }

                                targetFile.parentFile?.mkdirs()
                                if (targetFile.exists() || java.nio.file.Files.isSymbolicLink(resolvedPath)) {
                                    try { java.nio.file.Files.delete(resolvedPath) } catch (_: Exception) {}
                                }

                                try {
                                    java.nio.file.Files.createSymbolicLink(resolvedPath, java.nio.file.Path.of(linkTarget))
                                } catch (e: Exception) {
                                    // Fallback for filesystems or OS environments (e.g. Windows without developer mode)
                                    // where symlink creation is restricted
                                    deferredSymlinks.add(Pair(resolvedPath, targetPath))
                                }
                            } else {
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos ->
                                    var count: Int
                                    while (tarIn.read(buffer, 0, buffer.size).also { count = it } != -1) {
                                        fos.write(buffer, 0, count)
                                    }
                                }

                                // Check entry mode for POSIX executable permissions
                                val mode = entry.mode
                                val lowerName = entry.name.lowercase()
                                if ((mode and 0b001_000_000) != 0 || isExecutableBinaryName(lowerName)) {
                                    FileUtils.makeExecutable(targetFile)
                                }
                            }

                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        }

        // Resolve any symlinks that could not be created directly (e.g. Windows symlink restrictions)
        for ((linkPath, targetPath) in deferredSymlinks) {
            try {
                if (java.nio.file.Files.exists(targetPath)) {
                    val linkFile = linkPath.toFile()
                    val target = targetPath.toFile()
                    if (target.isDirectory) {
                        target.copyRecursively(linkFile, overwrite = true)
                    } else {
                        java.nio.file.Files.copy(targetPath, linkPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            } catch (e: Exception) {
                KromiumLogger.w(TAG, "Could not resolve symlink fallback for: $linkPath -> $targetPath", e)
            }
        }

        // If the archive unpacked into a single nested subdirectory, flatten it
        flattenIfSingleChild(safeDestination)

        // Ensure all executables and native libraries have proper execute permissions
        ensureExecutablePermissions(safeDestination)

        // Ensure macOS framework symlinks are present
        if (PlatformDetector.current().os.isMacOS) {
            OperatingSystem.MacOS.ensureMacFrameworkLinks(safeDestination)
        }
    }

    private fun isExecutableBinaryName(lowerName: String): Boolean {
        return lowerName.endsWith(".exe") ||
            lowerName.endsWith(".dylib") ||
            lowerName.endsWith(".so") ||
            lowerName.contains("jcef_helper") ||
            lowerName.contains("jcef helper") ||
            lowerName.contains("cef_server") ||
            lowerName.contains("chrome-sandbox") ||
            lowerName.contains("chrome_crashpad_handler")
    }

    private fun ensureExecutablePermissions(dir: File) {
        val safeDir = FileUtils.sanitizeDirectory(dir) ?: return
        safeDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val lower = file.name.lowercase()
                if (isExecutableBinaryName(lower)) {
                    FileUtils.makeExecutable(file)
                }
            }
        }
    }

    private fun flattenIfSingleChild(dir: File) {
        val safeDir = FileUtils.sanitizeDirectory(dir) ?: return
        val currentBasePath = safeDir.toPath().toAbsolutePath().normalize()
        var children = safeDir.listFiles() ?: return

        val preservedNames = setOf("bin", "lib", "frameworks", "contents", "locales")

        while (children.size == 1 && children[0].isDirectory && !preservedNames.contains(children[0].name.lowercase())) {
            val nestedDir = children[0]
            val nestedChildren = nestedDir.listFiles() ?: return

            for (child in nestedChildren) {
                val childName = child.name
                if (childName.contains("..") || childName.contains("\u0000")) continue
                val destPath = currentBasePath.resolve(childName).normalize()
                if (!destPath.startsWith(currentBasePath)) continue
                val dest = destPath.toFile()

                try {
                    java.nio.file.Files.move(
                        child.toPath(),
                        destPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (e: Exception) {
                    // Fallback for cross-device or locked files
                    child.copyRecursively(dest, overwrite = true)
                    child.deleteRecursively()
                }
            }
            nestedDir.deleteRecursively()
            children = safeDir.listFiles() ?: return
        }
    }
}
