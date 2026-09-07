package dev.daviante.kromium.core.util

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

private const val TAG = "FileUtils"

object FileUtils {

    fun deleteDirectory(dir: File): Boolean {
        if (!dir.exists()) return true
        return try {
            dir.deleteRecursively()
        } catch (e: Exception) {
            KromiumLogger.w(TAG, "Failed to delete directory: ${dir.absolutePath}", e)
            false
        }
    }

    fun ensureDirectory(dir: File): Boolean {
        return if (dir.exists()) {
            dir.isDirectory
        } else {
            val created = dir.mkdirs()
            if (!created) {
                KromiumLogger.w(TAG, "Failed to create directory: ${dir.absolutePath}")
            }
            created
        }
    }

    fun removeMacQuarantine(dir: File) {
        try {
            val process = ProcessBuilder("xattr", "-d", "-r", "com.apple.quarantine", dir.canonicalPath).start()
            process.waitFor()
        } catch (e: Exception) {
            KromiumLogger.d(TAG, "Could not remove macOS quarantine flag (expected on non-macOS): ${e.message}")
        }
    }

    fun makeExecutable(file: File) {
        try {
            file.setExecutable(true, false)
            file.setReadable(true, false)
        } catch (e: SecurityException) {
            KromiumLogger.w(TAG, "Failed to set executable permissions on: ${file.name}", e)
        }
    }
}
