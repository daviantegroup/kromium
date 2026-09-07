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

    fun extractTarGz(archiveFile: File, destinationDir: File, bufferSize: Int = 32 * 1024) {
        val safeDestination = FileUtils.sanitizeDirectory(destinationDir)
            ?: throw IllegalArgumentException("Invalid destination directory: ${destinationDir.path}")
        FileUtils.ensureDirectory(safeDestination)
        val destinationBasePath = safeDestination.toPath().toAbsolutePath().normalize()

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
                                if ((mode and 0b001_000_000) != 0 || entry.name.endsWith(".exe") || entry.name.contains("jcef_helper")) {
                                    FileUtils.makeExecutable(targetFile)
                                }
                            }

                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        }

        // If the archive unpacked into a single nested subdirectory, flatten it
        flattenIfSingleChild(safeDestination)
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
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
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
