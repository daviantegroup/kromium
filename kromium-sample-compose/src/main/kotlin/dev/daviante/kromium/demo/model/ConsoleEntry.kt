package dev.daviante.kromium.demo.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConsoleEntryType {
    INFO,
    WARNING,
    ERROR,
    JS_INPUT,
    JS_OUTPUT
}

data class ConsoleEntry(
    val type: ConsoleEntryType,
    val message: String,
    val source: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}
