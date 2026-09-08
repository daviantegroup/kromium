package dev.daviante.kromium.demo.util

import kotlin.jvm.JvmName

/**
 * Formats a byte count into a human-readable representation (B, KB, MB).
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}

/**
 * Extension wrapper for [formatBytes].
 */
@JvmName("formatBytesExt")
fun Long.formatBytes(): String = formatBytes(this)
