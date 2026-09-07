package dev.daviante.kromium.core.util

import dev.daviante.kromium.core.logging.*
import java.io.File

private const val TAG = "FileUtils"

object FileUtils {

    /**
     * Validates and sanitizes a [dir] path to prevent path traversal (CWE-022)
     * and accidental/malicious operations on sensitive system directories or roots.
     *
     * @return The canonical, validated [File] or null if validation fails.
     */
    fun sanitizeDirectory(dir: File): File? {
        val rawPath = dir.path
        if (rawPath.contains("..")) {
            KromiumLogger.w(TAG, "Path traversal sequence detected in: $rawPath")
            return null
        }

        val canonical = try {
            dir.canonicalFile
        } catch (e: Exception) {
            KromiumLogger.w(TAG, "Failed to resolve canonical path for: $rawPath", e)
            return null
        }

        val canonicalPath = canonical.canonicalPath
        if (canonicalPath.contains("..")) {
            return null
        }

        // Refuse operations on filesystem roots (e.g. "/" or "C:\")
        val roots = File.listRoots()?.map { it.canonicalPath } ?: emptyList()
        if (canonical.parentFile == null || roots.any { it.equals(canonicalPath, ignoreCase = true) }) {
            KromiumLogger.w(TAG, "Refusing operation on filesystem root: $canonicalPath")
            return null
        }

        // Refuse operations directly on the user's home directory root
        val userHome = System.getProperty("user.home")?.let { File(it).canonicalPath }
        if (userHome != null && canonicalPath == userHome) {
            KromiumLogger.w(TAG, "Refusing operation on user home root directory: $canonicalPath")
            return null
        }

        return canonical
    }

    /**
     * Resolves a child file safely under [baseDir], ensuring it does not escape [baseDir].
     */
    fun resolveChild(baseDir: File, relativePath: String): File? {
        if (relativePath.contains("..")) return null
        val safeBase = sanitizeDirectory(baseDir) ?: return null
        val basePath = safeBase.toPath().toAbsolutePath().normalize()
        val resolved = basePath.resolve(relativePath).normalize()
        if (!resolved.startsWith(basePath)) return null
        return resolved.toFile()
    }

    fun deleteDirectory(dir: File): Boolean {
        val safeDir = sanitizeDirectory(dir) ?: return false
        if (!safeDir.exists()) return true
        if (!safeDir.isDirectory) {
            KromiumLogger.w(TAG, "Path is not a directory: ${safeDir.path}")
            return false
        }
        return try {
            safeDir.deleteRecursively()
        } catch (e: Exception) {
            KromiumLogger.w(TAG, "Failed to delete directory: ${safeDir.path}", e)
            false
        }
    }

    fun ensureDirectory(dir: File): Boolean {
        val safeDir = sanitizeDirectory(dir) ?: return false
        return if (safeDir.exists()) {
            safeDir.isDirectory
        } else {
            val created = safeDir.mkdirs()
            if (!created) {
                KromiumLogger.w(TAG, "Failed to create directory: ${safeDir.path}")
            }
            created
        }
    }

    fun removeMacQuarantine(dir: File) {
        val safeDir = sanitizeDirectory(dir) ?: return
        if (!safeDir.exists() || !safeDir.isDirectory) return

        val canonicalPath = safeDir.canonicalPath
        // Ensure path does not begin with '-' to prevent option injection
        if (canonicalPath.startsWith("-")) {
            KromiumLogger.w(TAG, "Refusing to execute quarantine stripping on path starting with dash: $canonicalPath")
            return
        }

        try {
            // Use '--' argument delimiter to prevent command line argument injection (CWE-88)
            // Discard stdout/stderr to avoid OS pipe buffer saturation and deadlock
            val process = ProcessBuilder("xattr", "-d", "-r", "com.apple.quarantine", "--", canonicalPath)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                KromiumLogger.w(TAG, "Timed out removing macOS quarantine attribute on: $canonicalPath")
            }
        } catch (e: Exception) {
            KromiumLogger.d(TAG, "Could not remove macOS quarantine flag (expected on non-macOS): ${e.message}")
        }
    }

    fun makeExecutable(file: File) {
        val rawPath = file.path
        if (rawPath.contains("..")) return
        val canonical = try {
            file.canonicalFile
        } catch (e: Exception) {
            return
        }
        if (!canonical.exists() || canonical.isDirectory) return
        try {
            canonical.setExecutable(true, false)
            canonical.setReadable(true, false)
        } catch (e: SecurityException) {
            KromiumLogger.w(TAG, "Failed to set executable permissions on: ${canonical.name}", e)
        }
    }
}
