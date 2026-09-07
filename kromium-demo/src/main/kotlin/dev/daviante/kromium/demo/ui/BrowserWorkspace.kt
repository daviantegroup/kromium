package dev.daviante.kromium.demo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daviante.kromium.compose.KromiumView
import dev.daviante.kromium.demo.state.WorkspaceState
import dev.daviante.kromium.demo.theme.KromiumColors
import dev.daviante.kromium.demo.ui.components.AppHeader
import dev.daviante.kromium.demo.ui.components.DevToolsWorkbench
import dev.daviante.kromium.demo.ui.components.NavigationToolbar
import dev.daviante.kromium.demo.ui.components.TabBar

@Composable
fun BrowserWorkspace(
    workspaceState: WorkspaceState,
    modifier: Modifier = Modifier
) {
    val activeTab = workspaceState.activeTab
    val viewState = activeTab?.viewState

    Column(modifier = modifier.fillMaxSize()) {
        // App Branded Header with logo.png
        AppHeader()

        // Tab Bar
        TabBar(
            tabs = workspaceState.tabs,
            activeTabId = workspaceState.activeTabId,
            onSelectTab = { workspaceState.selectTab(it) },
            onCloseTab = { workspaceState.closeTab(it) },
            onNewTab = { workspaceState.openTab("https://duckduckgo.com") }
        )

        // Navigation Toolbar (Back/Forward, Omnibox, Zoom, DevTools)
        NavigationToolbar(workspaceState = workspaceState)

        // Viewport & Developer Workbench Split
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Main Chromium Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(KromiumColors.Background)
            ) {
                workspaceState.tabs.forEach { tab ->
                    val isActive = tab.id == workspaceState.activeTabId

                    // Synchronize native AWT component visibility when tab changes
                    LaunchedEffect(isActive, tab.viewState.browser) {
                        tab.viewState.browser?.uiComponent?.isVisible = isActive
                    }

                    key(tab.id) {
                        Box(
                            modifier = if (isActive) Modifier.fillMaxSize() else Modifier.size(0.dp)
                        ) {
                            KromiumView(
                                state = tab.viewState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Slide-out Developer Workbench Drawer
            AnimatedVisibility(
                visible = workspaceState.isDevDrawerOpen,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                DevToolsWorkbench(workspaceState = workspaceState)
            }
        }

        // Bottom Status Bar
        Surface(
            color = KromiumColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .border(1.dp, KromiumColors.BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (viewState?.title?.isNotBlank() == true) {
                        "${viewState.title} — ${viewState.url}"
                    } else {
                        viewState?.url ?: ""
                    },
                    fontSize = 11.sp,
                    color = KromiumColors.TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zoom: ${((workspaceState.currentZoom + 1.0) * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = KromiumColors.TextMuted
                    )

                    Text(
                        text = if (viewState?.isLoading == true) "Loading..." else "Done",
                        fontSize = 11.sp,
                        color = if (viewState?.isLoading == true) KromiumColors.Cyan else KromiumColors.Success,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
