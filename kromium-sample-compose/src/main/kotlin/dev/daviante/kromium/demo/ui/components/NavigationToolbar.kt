package dev.daviante.kromium.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daviante.kromium.demo.state.WorkspaceState
import dev.daviante.kromium.demo.theme.KromiumColors

@Composable
fun NavigationToolbar(
    workspaceState: WorkspaceState,
    modifier: Modifier = Modifier
) {
    val activeTab = workspaceState.activeTab
    val viewState = activeTab?.viewState
    val currentUrl = viewState?.url ?: ""
    var inputUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
    var isFocused by remember { mutableStateOf(false) }
    var isBookmarked by remember(currentUrl) { mutableStateOf(false) }

    val submitNavigation = {
        val trimmed = inputUrl.trim()
        if (trimmed.isNotBlank()) {
            val target = WorkspaceState.resolveNavigationTarget(trimmed)
            inputUrl = target
            workspaceState.navigate(target)
        }
    }

    Surface(
        color = KromiumColors.SurfaceElevated,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Main Chrome Navigation Bar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Back Button
                IconButton(
                    onClick = { viewState?.goBack() },
                    enabled = viewState?.canGoBack == true,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (viewState?.canGoBack == true) KromiumColors.TextPrimary else KromiumColors.TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Forward Button
                IconButton(
                    onClick = { viewState?.goForward() },
                    enabled = viewState?.canGoForward == true,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (viewState?.canGoForward == true) KromiumColors.TextPrimary else KromiumColors.TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Reload / Stop Loading Button
                IconButton(
                    onClick = {
                        if (viewState?.isLoading == true) {
                            viewState.stopLoading()
                        } else {
                            viewState?.reload()
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (viewState?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (viewState?.isLoading == true) "Stop" else "Reload",
                        tint = KromiumColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Home Button
                IconButton(
                    onClick = {
                        val homeUrl = WorkspaceState.DEFAULT_HOME_URL
                        inputUrl = homeUrl
                        workspaceState.navigate(homeUrl)
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = KromiumColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Chrome Pill Omnibox (Address Bar)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isFocused) KromiumColors.SurfaceElevated else KromiumColors.Surface)
                        .border(
                            width = 1.dp,
                            color = if (isFocused) KromiumColors.TextPrimary else KromiumColors.Border,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // SSL Lock / Scheme indicator
                        val isHttps = inputUrl.startsWith("https://")
                        val isCustomScheme = inputUrl.startsWith("kromium:") || inputUrl.startsWith("chrome:")
                        Icon(
                            imageVector = when {
                                isHttps -> Icons.Default.Lock
                                isCustomScheme -> Icons.Default.Code
                                else -> Icons.Default.Language
                            },
                            contentDescription = "Security Status",
                            tint = when {
                                isHttps -> KromiumColors.Emerald
                                isCustomScheme -> KromiumColors.Cyan
                                else -> KromiumColors.TextMuted
                            },
                            modifier = Modifier.size(15.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // URL Input using BasicTextField (No 56dp min height clipping!)
                        BasicTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = KromiumColors.TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(KromiumColors.Cyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = { submitNavigation() }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isFocused = it.isFocused },
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (inputUrl.isEmpty()) {
                                        Text(
                                            text = "Search DuckDuckGo or enter URL",
                                            color = KromiumColors.TextMuted,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Clear button when editing
                        if (inputUrl.isNotBlank() && inputUrl != currentUrl) {
                            IconButton(
                                onClick = { inputUrl = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = KromiumColors.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Bookmark Star Button
                        IconButton(
                            onClick = { isBookmarked = !isBookmarked },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) KromiumColors.Warning else KromiumColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Quick Download Button for current/input URL
                        IconButton(
                            onClick = {
                                val trimmed = inputUrl.trim()
                                if (trimmed.isNotBlank()) {
                                    workspaceState.downloadUrl(trimmed)
                                    workspaceState.activeWorkbenchTab = dev.daviante.kromium.demo.model.WorkbenchTab.DOWNLOADS
                                    workspaceState.isDevDrawerOpen = true
                                }
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Download URL",
                                tint = KromiumColors.Cyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Go / Navigate Button
                        IconButton(
                            onClick = { submitNavigation() },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go",
                                tint = KromiumColors.Cyan,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Zoom Control Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF131926))
                        .border(1.dp, Color(0xFF263248), RoundedCornerShape(16.dp))
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { workspaceState.adjustZoom(-0.5) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = KromiumColors.TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        text = "${((workspaceState.currentZoom + 1.0) * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = KromiumColors.TextPrimary,
                        modifier = Modifier
                            .clickable { workspaceState.resetZoom() }
                            .padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = { workspaceState.adjustZoom(0.5) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = KromiumColors.TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Chrome Downloads Manager Button with Live Progress Badge
                val inProgressCount = workspaceState.downloads.count { it.isInProgress }
                IconButton(
                    onClick = {
                        workspaceState.activeWorkbenchTab = dev.daviante.kromium.demo.model.WorkbenchTab.DOWNLOADS
                        workspaceState.isDevDrawerOpen = true
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (inProgressCount > 0) {
                                Badge(containerColor = KromiumColors.Cyan) {
                                    Text(inProgressCount.toString(), fontSize = 9.sp, color = KromiumColors.Background)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Downloads Manager",
                            tint = if (inProgressCount > 0) KromiumColors.Cyan else KromiumColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Clear Browsing Data Quick Button
                IconButton(
                    onClick = {
                        workspaceState.activeWorkbenchTab = dev.daviante.kromium.demo.model.WorkbenchTab.CLEAR_DATA
                        workspaceState.isDevDrawerOpen = true
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Browsing Data",
                        tint = KromiumColors.TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Native DevTools Window Button
                IconButton(
                    onClick = { viewState?.openDevTools() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Open Chromium Inspect DevTools",
                        tint = KromiumColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Dev Workbench Drawer Toggle
                IconButton(
                    onClick = { workspaceState.isDevDrawerOpen = !workspaceState.isDevDrawerOpen },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Toggle Developer Workbench",
                        tint = if (workspaceState.isDevDrawerOpen) KromiumColors.Cyan else KromiumColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Chrome Bookmarks Bar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeBookmarkItem("🌐 Kromium Docs", WorkspaceState.DEFAULT_HOME_URL) { workspaceState.navigate(it) }
                ChromeBookmarkItem("🦆 DuckDuckGo", "https://duckduckgo.com") { workspaceState.navigate(it) }
                ChromeBookmarkItem("🔍 Google", "https://google.com") { workspaceState.navigate(it) }
                ChromeBookmarkItem("🐙 GitHub", "https://github.com") { workspaceState.navigate(it) }
                ChromeBookmarkItem("🚀 HTML5 Test", "https://html5test.co") { workspaceState.navigate(it) }
                ChromeBookmarkItem("⏱ Speedometer", "https://browserbench.org/Speedometer3.0") { workspaceState.navigate(it) }
                ChromeBookmarkItem("⚡ Live HTML Showcase", "") { workspaceState.loadSampleShowcaseHtml() }
            }

            // Loading Progress Bar
            if (viewState?.isLoading == true) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = KromiumColors.Cyan,
                    trackColor = Color.Transparent
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(KromiumColors.Border)
                )
            }
        }
    }
}

@Composable
private fun ChromeBookmarkItem(
    name: String,
    url: String,
    onClick: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick(url) }
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = name,
            fontSize = 11.5.sp,
            color = KromiumColors.TextSecondary,
            fontWeight = FontWeight.Normal
        )
    }
}
