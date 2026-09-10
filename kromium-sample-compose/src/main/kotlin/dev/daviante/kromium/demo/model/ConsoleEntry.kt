package dev.daviante.kromium.demo.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConsoleEntry(
    val type: ConsoleEntryType,
    val message: String,
    val source: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}
