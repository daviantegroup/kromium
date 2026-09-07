package dev.daviante.kromium.demo.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.daviante.kromium.compose.KromiumViewState
import dev.daviante.kromium.presentation.handler.KromiumConsoleMessageLevel
import java.awt.image.BufferedImage
import java.util.UUID

class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String = "about:blank"
) {
    val viewState: KromiumViewState = KromiumViewState(initialUrl)
    val consoleLogs = mutableStateListOf<ConsoleEntry>()
    var capturedScreenshot by mutableStateOf<BufferedImage?>(null)
    var isInitialized by mutableStateOf(false)

    val displayTitle: String
        get() = when {
            viewState.title.isNotBlank() -> viewState.title
            viewState.url.isBlank() || viewState.url == "about:blank" -> "New Tab"
            else -> viewState.url.removePrefix("https://").removePrefix("http://").take(32)
        }

    init {
        viewState.onConsoleMessage = { msg ->
            val type = when (msg.level) {
                KromiumConsoleMessageLevel.ERROR -> ConsoleEntryType.ERROR
                KromiumConsoleMessageLevel.WARNING -> ConsoleEntryType.WARNING
                else -> ConsoleEntryType.INFO
            }
            consoleLogs.add(
                ConsoleEntry(
                    type = type,
                    message = msg.message,
                    source = if (msg.source.isNotBlank()) "${msg.source}:${msg.line}" else null
                )
            )
        }
    }
}
