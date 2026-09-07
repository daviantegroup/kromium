package dev.daviante.kromium.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    Surface(
        color = KromiumColors.Surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, KromiumColors.Border)
    ) {
        Column {
            // Main toolbar controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Back Button
                IconButton(
                    onClick = { viewState?.goBack() },
                    enabled = viewState?.canGoBack == true
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (viewState?.canGoBack == true) KromiumColors.TextPrimary else KromiumColors.TextMuted
                    )
                }

                // Forward Button
                IconButton(
                    onClick = { viewState?.goForward() },
                    enabled = viewState?.canGoForward == true
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (viewState?.canGoForward == true) KromiumColors.TextPrimary else KromiumColors.TextMuted
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
                    }
                ) {
                    Icon(
                        imageVector = if (viewState?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (viewState?.isLoading == true) "Stop" else "Reload",
                        tint = KromiumColors.TextPrimary
                    )
                }

                // Home Button
                IconButton(
                    onClick = {
                        val homeUrl = "https://duckduckgo.com"
                        inputUrl = homeUrl
                        workspaceState.navigate(homeUrl)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = KromiumColors.TextPrimary
                    )
                }

                // Smart Omnibox URL Bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KromiumColors.SurfaceElevated)
                        .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // SSL Lock indicator
                        val isHttps = inputUrl.startsWith("https://")
                        Icon(
                            imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.Language,
                            contentDescription = "Security",
                            tint = if (isHttps) KromiumColors.Emerald else KromiumColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = { workspaceState.navigate(inputUrl) }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = KromiumColors.TextPrimary,
                                unfocusedTextColor = KromiumColors.TextPrimary,
                                cursorColor = KromiumColors.Cyan
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Clear input button
                        if (inputUrl.isNotBlank() && inputUrl != currentUrl) {
                            IconButton(
                                onClick = { inputUrl = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = KromiumColors.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Go Button
                        IconButton(
                            onClick = { workspaceState.navigate(inputUrl) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go",
                                tint = KromiumColors.Cyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Zoom Controls Segment
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KromiumColors.SurfaceElevated)
                        .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { workspaceState.adjustZoom(-0.5) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = KromiumColors.TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "${((workspaceState.currentZoom + 1.0) * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = KromiumColors.TextSecondary,
                        modifier = Modifier
                            .clickable { workspaceState.resetZoom() }
                            .padding(horizontal = 6.dp)
                    )

                    IconButton(
                        onClick = { workspaceState.adjustZoom(0.5) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = KromiumColors.TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Native DevTools button (Chromium Inspect)
                IconButton(
                    onClick = { viewState?.openDevTools() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Open Native DevTools",
                        tint = KromiumColors.TextPrimary
                    )
                }

                // Developer Workbench Drawer Toggle
                IconButton(
                    onClick = { workspaceState.isDevDrawerOpen = !workspaceState.isDevDrawerOpen }
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Toggle Dev Workbench",
                        tint = if (workspaceState.isDevDrawerOpen) KromiumColors.Cyan else KromiumColors.TextPrimary
                    )
                }
            }

            // Quick Preset Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Launch:",
                    fontSize = 11.sp,
                    color = KromiumColors.TextMuted,
                    fontWeight = FontWeight.SemiBold
                )

                PresetChip("DuckDuckGo", "https://duckduckgo.com") { workspaceState.navigate(it) }
                PresetChip("Google", "https://google.com") { workspaceState.navigate(it) }
                PresetChip("GitHub", "https://github.com") { workspaceState.navigate(it) }
                PresetChip("YouTube", "https://youtube.com") { workspaceState.navigate(it) }
                PresetChip("HTML5 Test", "https://html5test.co") { workspaceState.navigate(it) }
                PresetChip("Speedometer 3.0", "https://browserbench.org/Speedometer3.0") { workspaceState.navigate(it) }
                PresetChip("⚡ Live HTML Demo", "") { workspaceState.loadSampleShowcaseHtml() }
            }

            // Loading Bar
            if (viewState?.isLoading == true) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = KromiumColors.Cyan,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    name: String,
    url: String,
    onClick: (String) -> Unit
) {
    Surface(
        color = KromiumColors.SurfaceElevated,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(1.dp, KromiumColors.Border, RoundedCornerShape(6.dp))
            .clickable { onClick(url) }
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            color = KromiumColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
