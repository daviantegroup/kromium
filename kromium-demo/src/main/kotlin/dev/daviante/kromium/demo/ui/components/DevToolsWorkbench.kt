package dev.daviante.kromium.demo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daviante.kromium.demo.model.ConsoleEntryType
import dev.daviante.kromium.demo.model.WorkbenchTab
import dev.daviante.kromium.demo.state.WorkspaceState
import dev.daviante.kromium.demo.theme.KromiumColors
import java.io.File
import javax.imageio.ImageIO

@Composable
fun DevToolsWorkbench(
    workspaceState: WorkspaceState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = KromiumColors.Surface,
        modifier = modifier
            .width(380.dp)
            .fillMaxHeight()
            .border(width = 1.dp, color = KromiumColors.Border)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Dev Workbench",
                        tint = KromiumColors.Cyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Developer Workbench",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = KromiumColors.TextPrimary
                    )
                }

                IconButton(
                    onClick = { workspaceState.isDevDrawerOpen = false },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Workbench",
                        tint = KromiumColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = KromiumColors.Border)

            // Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WorkbenchTab.values().forEach { tab ->
                    val isSelected = workspaceState.activeWorkbenchTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) KromiumColors.SurfaceElevated else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) KromiumColors.Cyan.copy(alpha = 0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { workspaceState.activeWorkbenchTab = tab }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) KromiumColors.Cyan else KromiumColors.TextSecondary
                        )
                    }
                }
            }

            HorizontalDivider(color = KromiumColors.BorderSubtle)

            // Content Area based on selected tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                when (workspaceState.activeWorkbenchTab) {
                    WorkbenchTab.JS_REPL -> JsReplTab(workspaceState)
                    WorkbenchTab.CONSOLE -> ConsoleLogsTab(workspaceState)
                    WorkbenchTab.PAGE_INFO -> PageInfoTab(workspaceState)
                    WorkbenchTab.COOKIES -> CookiesTab(workspaceState)
                    WorkbenchTab.SCREENSHOT -> ScreenshotTab(workspaceState)
                }
            }
        }
    }
}

@Composable
private fun JsReplTab(workspaceState: WorkspaceState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Quick Presets",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = KromiumColors.TextSecondary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            JsSnippetButton("Title") { workspaceState.evaluateJs("document.title") }
            JsSnippetButton("URL") { workspaceState.evaluateJs("window.location.href") }
            JsSnippetButton("User-Agent") { workspaceState.evaluateJs("navigator.userAgent") }
            JsSnippetButton("Dark Filter") {
                workspaceState.evaluateJs("document.body.style.filter = 'invert(1) hue-rotate(180deg)'; 'Applied Dark Filter'")
            }
            JsSnippetButton("Reset Filter") {
                workspaceState.evaluateJs("document.body.style.filter = ''; 'Reset Filter'")
            }
        }

        // Custom JS Input
        OutlinedTextField(
            value = workspaceState.jsInputText,
            onValueChange = { workspaceState.jsInputText = it },
            placeholder = { Text("Enter JavaScript (e.g. document.title)...", fontSize = 12.sp, color = KromiumColors.TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = KromiumColors.SurfaceElevated,
                unfocusedContainerColor = KromiumColors.SurfaceElevated,
                focusedBorderColor = KromiumColors.Cyan,
                unfocusedBorderColor = KromiumColors.Border,
                focusedTextColor = KromiumColors.TextPrimary,
                unfocusedTextColor = KromiumColors.TextPrimary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    workspaceState.evaluateJs(workspaceState.jsInputText)
                },
                enabled = !workspaceState.jsIsEvaluating && workspaceState.jsInputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Cyan),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Run JavaScript", fontSize = 11.sp, color = KromiumColors.Background, fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = { workspaceState.clearConsoleLogs() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Clear History", fontSize = 11.sp, color = KromiumColors.TextSecondary)
            }
        }

        // Live Log Stream
        Text(
            text = "Console Stream",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = KromiumColors.TextSecondary
        )

        val logs = workspaceState.activeTab?.consoleLogs ?: emptyList()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(KromiumColors.SurfaceElevated)
                .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (logs.isEmpty()) {
                Text("No output yet. Run a snippet or navigate to generate logs.", fontSize = 11.sp, color = KromiumColors.TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    logs.forEach { entry ->
                        ConsoleEntryView(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleLogsTab(workspaceState: WorkspaceState) {
    val logs = workspaceState.activeTab?.consoleLogs ?: emptyList()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${logs.size} Messages Captured",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = KromiumColors.TextSecondary
            )

            TextButton(
                onClick = { workspaceState.clearConsoleLogs() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Clear", fontSize = 11.sp, color = KromiumColors.TextSecondary)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(KromiumColors.SurfaceElevated)
                .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (logs.isEmpty()) {
                Text("No console logs intercepted yet.", fontSize = 11.sp, color = KromiumColors.TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    logs.forEach { entry ->
                        ConsoleEntryView(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageInfoTab(workspaceState: WorkspaceState) {
    val tab = workspaceState.activeTab
    val viewState = tab?.viewState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoRow("Title", viewState?.title?.ifBlank { "Untitled" } ?: "N/A")
        InfoRow("URL", viewState?.url ?: "N/A")
        InfoRow("Protocol", if (viewState?.url?.startsWith("https://") == true) "HTTPS (Secure)" else "HTTP (Insecure)")
        InfoRow("Loading Status", if (viewState?.isLoading == true) "Loading..." else "Complete")

        HorizontalDivider(color = KromiumColors.BorderSubtle)

        Button(
            onClick = { workspaceState.inspectPageText() },
            colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Cyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Extract DOM Plain Text", fontSize = 12.sp, color = KromiumColors.Background, fontWeight = FontWeight.Bold)
        }

        if (workspaceState.extractedPageText.isNotBlank()) {
            Text(
                text = "Extracted Text (${workspaceState.extractedPageText.length} chars)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = KromiumColors.TextSecondary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KromiumColors.SurfaceElevated)
                    .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = workspaceState.extractedPageText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = KromiumColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun CookiesTab(workspaceState: WorkspaceState) {
    val tab = workspaceState.activeTab
    val cookies = workspaceState.cookiesMap

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Host Cookies (${cookies.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = KromiumColors.TextPrimary
            )

            Button(
                onClick = { workspaceState.refreshCookies() },
                colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Cyan),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Refresh", fontSize = 11.sp, color = KromiumColors.Background, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(KromiumColors.SurfaceElevated)
                .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (cookies.isEmpty()) {
                Text(
                    text = "No cookies recorded for current page. Click Refresh to inspect.",
                    fontSize = 11.sp,
                    color = KromiumColors.TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cookies.forEach { (name, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(KromiumColors.Surface)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KromiumColors.Cyan)
                                Text(value.take(40) + if (value.length > 40) "..." else "", fontSize = 10.sp, color = KromiumColors.TextSecondary)
                            }
                            IconButton(
                                onClick = { workspaceState.deleteCookie(name) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = KromiumColors.Error, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotTab(workspaceState: WorkspaceState) {
    val tab = workspaceState.activeTab
    val screenshot = tab?.capturedScreenshot
    var savedPath by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                workspaceState.captureScreenshot()
                savedPath = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Emerald),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = KromiumColors.Background)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Capture Framebuffer", fontSize = 12.sp, color = KromiumColors.Background, fontWeight = FontWeight.Bold)
        }

        if (screenshot != null) {
            val composeBitmap = remember(screenshot) { screenshot.toComposeImageBitmap() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KromiumColors.SurfaceElevated)
                    .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = composeBitmap,
                    contentDescription = "Captured Screenshot",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = "Dimensions: ${screenshot.width} x ${screenshot.height} px",
                fontSize = 11.sp,
                color = KromiumColors.TextSecondary
            )

            Button(
                onClick = {
                    try {
                        val file = File.createTempFile("kromium_capture_", ".png")
                        ImageIO.write(screenshot, "PNG", file)
                        savedPath = file.absolutePath
                    } catch (e: Exception) {
                        savedPath = "Error saving: ${e.message}"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.SurfaceElevated),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save to Disk", fontSize = 11.sp, color = KromiumColors.TextPrimary)
            }

            if (savedPath != null) {
                Text(
                    text = "Saved to:\n$savedPath",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = KromiumColors.Emerald
                )
            }
        } else {
            Text(
                text = "Click 'Capture Framebuffer' to take a real-time native snapshot of the CEF rendering surface.",
                fontSize = 11.sp,
                color = KromiumColors.TextMuted
            )
        }
    }
}

@Composable
private fun JsSnippetButton(label: String, onClick: () -> Unit) {
    Surface(
        color = KromiumColors.SurfaceElevated,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(1.dp, KromiumColors.Border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = KromiumColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = KromiumColors.TextMuted, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 12.sp, color = KromiumColors.TextPrimary)
    }
}

@Composable
private fun ConsoleEntryView(entry: dev.daviante.kromium.demo.model.ConsoleEntry) {
    val (color, prefix) = when (entry.type) {
        ConsoleEntryType.ERROR -> KromiumColors.Error to "✕"
        ConsoleEntryType.WARNING -> KromiumColors.Warning to "⚠"
        ConsoleEntryType.JS_INPUT -> KromiumColors.Cyan to "›"
        ConsoleEntryType.JS_OUTPUT -> KromiumColors.Emerald to "‹"
        ConsoleEntryType.INFO -> KromiumColors.TextSecondary to "ℹ"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = prefix,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.message,
                color = color,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            if (entry.source != null) {
                Text(
                    text = "${entry.formattedTime} [${entry.source}]",
                    color = KromiumColors.TextMuted,
                    fontSize = 9.sp
                )
            }
        }
    }
}
