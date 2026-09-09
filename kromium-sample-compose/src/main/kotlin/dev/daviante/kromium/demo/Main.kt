package dev.daviante.kromium.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
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
import dev.daviante.kromium.presentation.keyboard.KromiumShortcutHandler
import kotlinx.coroutines.launch
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

private val isMac = KromiumShortcutHandler.isMac

private fun isBrowserFocused(workspaceState: WorkspaceState?): Boolean {
    val browser = workspaceState?.activeTab?.viewState?.browser ?: return false
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
    val comp = browser.uiComponent
    return focusOwner === comp || SwingUtilities.isDescendingFrom(focusOwner, comp)
}

fun main() = application {
    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)
    var workspaceState by remember { mutableStateOf<WorkspaceState?>(null) }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Kromium",
        icon = LogoAsset.painter
    ) {
        // Native macOS System Menu Bar / Desktop Menu Bar providing platform-standard shortcuts
        MenuBar {
            Menu("File", mnemonic = 'F') {
                Item("New Tab", shortcut = KeyShortcut(Key.T, meta = isMac, ctrl = !isMac)) {
                    workspaceState?.openTab()
                }
                Item("Close Tab", shortcut = KeyShortcut(Key.W, meta = isMac, ctrl = !isMac)) {
                    workspaceState?.let { ws ->
                        if (ws.activeTabId.isNotBlank()) ws.closeTab(ws.activeTabId)
                    }
                }
                Separator()
                Item("Quit Kromium", shortcut = KeyShortcut(Key.Q, meta = isMac, ctrl = !isMac)) {
                    exitApplication()
                }
            }
            Menu("Edit", mnemonic = 'E') {
                Item("Undo", shortcut = KeyShortcut(Key.Z, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(workspaceState)) {
                        workspaceState?.activeTab?.viewState?.browser?.undo()
                    }
                }
                Item("Redo", shortcut = KeyShortcut(Key.Z, meta = isMac, ctrl = !isMac, shift = true)) {
                    if (isBrowserFocused(workspaceState)) {
                        workspaceState?.activeTab?.viewState?.browser?.redo()
                    }
                }
                Separator()
                Item("Cut", shortcut = KeyShortcut(Key.X, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(workspaceState)) {
                        workspaceState?.activeTab?.viewState?.browser?.cut()
                    }
                }
                Item("Copy", shortcut = KeyShortcut(Key.C, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(workspaceState)) {
                        workspaceState?.activeTab?.viewState?.browser?.copy()
                    }
                }
                Item("Paste", shortcut = KeyShortcut(Key.V, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(workspaceState)) {
                        workspaceState?.activeTab?.viewState?.browser?.paste()
                    }
                }
                Item("Select All", shortcut = KeyShortcut(Key.A, meta = isMac, ctrl = !isMac)) {
                    if (isBrowserFocused(workspaceState)) {
                        workspaceState?.activeTab?.viewState?.browser?.selectAll()
                    }
                }
            }
            Menu("View", mnemonic = 'V') {
                Item("Reload", shortcut = KeyShortcut(Key.R, meta = isMac, ctrl = !isMac)) {
                    workspaceState?.activeTab?.viewState?.reload()
                }
                Item("Force Reload", shortcut = KeyShortcut(Key.R, meta = isMac, ctrl = !isMac, shift = true)) {
                    workspaceState?.activeTab?.viewState?.browser?.reloadIgnoreCache()
                }
                Separator()
                Item("Zoom In", shortcut = KeyShortcut(Key.Equals, meta = isMac, ctrl = !isMac)) {
                    workspaceState?.adjustZoom(0.25)
                }
                Item("Zoom Out", shortcut = KeyShortcut(Key.Minus, meta = isMac, ctrl = !isMac)) {
                    workspaceState?.adjustZoom(-0.25)
                }
                Item("Actual Size", shortcut = KeyShortcut(Key.Zero, meta = isMac, ctrl = !isMac)) {
                    workspaceState?.resetZoom()
                }
                Separator()
                Item("Toggle DevTools", shortcut = KeyShortcut(Key.I, meta = isMac, ctrl = !isMac, alt = isMac, shift = !isMac)) {
                    workspaceState?.let { it.isDevDrawerOpen = !it.isDevDrawerOpen }
                }
            }
            Menu("History", mnemonic = 'H') {
                Item("Back", shortcut = KeyShortcut(Key.DirectionLeft, meta = isMac, alt = !isMac)) {
                    workspaceState?.activeTab?.viewState?.goBack()
                }
                Item("Forward", shortcut = KeyShortcut(Key.DirectionRight, meta = isMac, alt = !isMac)) {
                    workspaceState?.activeTab?.viewState?.goForward()
                }
            }
        }

        KromiumTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KromiumColors.Background)
            ) {
                DemoApp(onWorkspaceReady = { workspaceState = it })
            }
        }
    }
}

@Composable
fun DemoApp(onWorkspaceReady: (WorkspaceState) -> Unit = {}) {
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
            val ws = remember(scope) {
                WorkspaceState(scope).also(onWorkspaceReady)
            }
            BrowserWorkspace(workspaceState = ws)
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
