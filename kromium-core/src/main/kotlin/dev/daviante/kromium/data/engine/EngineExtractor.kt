package dev.daviante.kromium.data.engine

import dev.daviante.kromium.domain.model.*
import dev.daviante.kromium.domain.config.*
import dev.daviante.kromium.domain.exception.*
import dev.daviante.kromium.core.logging.*
import dev.daviante.kromium.core.util.*

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object EngineExtractor {

    private const val TAG = "EngineExtractor"

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
                                if ((mode and 0b001_000_000) != 0 ||
                                    lowerName.endsWith(".exe") ||
                                    lowerName.contains("jcef_helper") ||
                                    lowerName.contains("jcef helper") ||
                                    lowerName.contains("cef_server") ||
                                    lowerName.endsWith(".dylib") ||
                                    lowerName.endsWith(".so")
                                ) {
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
    }

    private fun ensureExecutablePermissions(dir: File) {
        val safeDir = FileUtils.sanitizeDirectory(dir) ?: return
        safeDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val lower = file.name.lowercase()
                if (lower.endsWith(".exe") ||
                    lower.endsWith(".dylib") ||
                    lower.endsWith(".so") ||
                    lower == "jcef_helper" ||
                    lower == "jcef helper" ||
                    lower == "cef_server"
                ) {
                    FileUtils.makeExecutable(file)
                }
            }
        }
    }

    private fun flattenIfSingleChild(dir: File) {
        val safeDir = FileUtils.sanitizeDirectory(dir) ?: return
        var currentDir = safeDir
        var children = currentDir.listFiles() ?: return
        val currentBasePath = currentDir.toPath().toAbsolutePath().normalize()

        while (children.size == 1 && children[0].isDirectory) {
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
            children = currentDir.listFiles() ?: return
        }
    }
}
