package dev.daviante.kromium.presentation.handler

/**
 * A message logged to the browser's JavaScript console.
 */
data class KromiumConsoleMessage(
    val level: KromiumConsoleMessageLevel,
    val message: String,
    val source: String,
    val line: Int
)
