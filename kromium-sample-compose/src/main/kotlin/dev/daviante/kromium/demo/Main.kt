package dev.daviante.kromium.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.daviante.kromium.demo.state.WorkspaceState
import dev.daviante.kromium.demo.theme.KromiumColors
import dev.daviante.kromium.demo.theme.KromiumTheme
import dev.daviante.kromium.demo.ui.BrowserWorkspace
import dev.daviante.kromium.demo.ui.components.ErrorScreen
import dev.daviante.kromium.demo.ui.components.LogoAsset
import dev.daviante.kromium.demo.ui.components.StartupScreen
import dev.daviante.kromium.domain.model.KromiumState
import dev.daviante.kromium.presentation.browser.Kromium
import kotlinx.coroutines.launch

fun main() = application {
    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Kromium",
        icon = LogoAsset.painter
    ) {
        KromiumTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KromiumColors.Background)
            ) {
                DemoApp()
            }
        }
    }
}

@Composable
fun DemoApp() {
    val engineState by Kromium.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!Kromium.isReady) {
            try {
                Kromium.initialize()
            } catch (_: Exception) {
                // StateFlow will automatically reflect KromiumState.Error
            }
        }
    }

    when (val state = engineState) {
        is KromiumState.Ready -> {
            val workspaceState = remember(scope) { WorkspaceState(scope) }
            BrowserWorkspace(workspaceState = workspaceState)
        }
        is KromiumState.Error -> {
            ErrorScreen(
                errorMessage = state.cause.message ?: "Failed to initialize Kromium engine",
                onRetry = {
                    scope.launch {
                        try {
                            Kromium.initialize()
                        } catch (_: Exception) {}
                    }
                }
            )
        }
        else -> {
            StartupScreen(state = state)
        }
    }
}
