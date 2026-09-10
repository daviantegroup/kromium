package dev.daviante.kromium.demo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daviante.kromium.demo.model.ConsoleEntryType
import dev.daviante.kromium.demo.model.WorkbenchTab
import dev.daviante.kromium.demo.state.WorkspaceState
import dev.daviante.kromium.demo.theme.KromiumColors
import dev.daviante.kromium.demo.util.formatBytes
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
                    WorkbenchTab.DOWNLOADS -> DownloadsTab(workspaceState)
                    WorkbenchTab.CLEAR_DATA -> ClearDataTab(workspaceState)
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

@Composable
private fun DownloadsTab(workspaceState: WorkspaceState) {
    val downloads = workspaceState.downloads
    val inProgressCount = downloads.count { it.isInProgress }
    var inputDownloadUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Downloads header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Downloads",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = KromiumColors.TextPrimary
                )
                if (downloads.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (inProgressCount > 0) KromiumColors.Cyan.copy(alpha = 0.2f) else KromiumColors.SurfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${downloads.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (inProgressCount > 0) KromiumColors.Cyan else KromiumColors.TextSecondary
                        )
                    }
                }
            }

            if (downloads.isNotEmpty()) {
                IconButton(
                    onClick = { workspaceState.clearDownloads() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear downloads",
                        tint = KromiumColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Direct URL download input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(KromiumColors.SurfaceElevated)
                .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                tint = KromiumColors.Cyan,
                modifier = Modifier.size(16.dp)
            )
            BasicTextField(
                value = inputDownloadUrl,
                onValueChange = { inputDownloadUrl = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = KromiumColors.TextPrimary, fontSize = 12.sp),
                singleLine = true,
                cursorBrush = SolidColor(KromiumColors.Cyan),
                decorationBox = { innerTextField ->
                    if (inputDownloadUrl.isEmpty()) {
                        Text(
                            text = "Enter file or image URL to download...",
                            fontSize = 11.sp,
                            color = KromiumColors.TextMuted
                        )
                    }
                    innerTextField()
                }
            )
            Button(
                onClick = {
                    if (inputDownloadUrl.isNotBlank()) {
                        workspaceState.downloadUrl(inputDownloadUrl)
                        inputDownloadUrl = ""
                    }
                },
                enabled = inputDownloadUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KromiumColors.CyanDark,
                    disabledContainerColor = KromiumColors.Surface
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text("Download", fontSize = 11.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sample download pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Samples:", fontSize = 10.sp, color = KromiumColors.TextMuted)
            SampleDownloadChip("Logo (SVG)") {
                workspaceState.downloadUrl("https://raw.githubusercontent.com/daviantegroup/kromium/master/assets/logo.svg")
            }
            SampleDownloadChip("100MB File") {
                workspaceState.downloadUrl("https://fsn1-speed.hetzner.com/100MB.bin")
            }
            SampleDownloadChip("1GB File") {
                workspaceState.downloadUrl("https://fsn1-speed.hetzner.com/1GB.bin")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(KromiumColors.SurfaceElevated)
                    .border(1.dp, KromiumColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = KromiumColors.TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No Downloads Yet",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KromiumColors.TextSecondary
                    )
                    Text(
                        text = "Enter any URL above or click a download link in a webpage to download files to ~/Downloads.",
                        fontSize = 11.sp,
                        color = KromiumColors.TextMuted
                    )
                    Button(
                        onClick = { workspaceState.triggerSampleDownload() },
                        colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.CyanDark),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Download Sample Asset", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                downloads.forEach { item ->
                    DownloadCard(
                        item = item,
                        workspaceState = workspaceState,
                        onRemove = { workspaceState.removeDownload(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleDownloadChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = KromiumColors.SurfaceElevated,
        modifier = Modifier.border(1.dp, KromiumColors.BorderSubtle, RoundedCornerShape(4.dp))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = KromiumColors.Cyan,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DownloadCard(
    item: dev.daviante.kromium.presentation.handler.KromiumDownloadItem,
    workspaceState: WorkspaceState,
    onRemove: () -> Unit
) {
    Surface(
        color = KromiumColors.SurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            item.isComplete -> Icons.Default.CheckCircle
                            item.isCanceled -> Icons.Default.Cancel
                            item.isPaused -> Icons.Default.Pause
                            else -> Icons.Default.FileDownload
                        },
                        contentDescription = null,
                        tint = when {
                            item.isComplete -> KromiumColors.Success
                            item.isCanceled -> KromiumColors.Error
                            item.isPaused -> KromiumColors.Warning
                            else -> KromiumColors.Cyan
                        },
                        modifier = Modifier.size(18.dp)
                    )

                    Column {
                        Text(
                            text = item.suggestedFileName.ifBlank { "download" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KromiumColors.TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = item.url.take(38),
                            fontSize = 10.sp,
                            color = KromiumColors.TextMuted,
                            maxLines = 1
                        )
                        if (item.fullPath.isNotBlank()) {
                            Text(
                                text = item.fullPath.takeLast(40),
                                fontSize = 9.sp,
                                color = KromiumColors.Cyan.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = KromiumColors.TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            if (item.isInProgress) {
                LinearProgressIndicator(
                    progress = { if (item.totalBytes > 0) (item.receivedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = KromiumColors.Cyan,
                    trackColor = KromiumColors.BorderSubtle
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Meta row: received / total, speed, status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sizeText = if (item.totalBytes > 0) {
                    "${formatBytes(item.receivedBytes)} / ${formatBytes(item.totalBytes)}"
                } else {
                    formatBytes(item.receivedBytes)
                }
                Text(
                    text = sizeText,
                    fontSize = 10.sp,
                    color = KromiumColors.TextSecondary
                )

                if (item.isInProgress && item.speed > 0) {
                    Text(
                        text = "${formatBytes(item.speed)}/s",
                        fontSize = 10.sp,
                        color = KromiumColors.Cyan
                    )
                }

                Text(
                    text = when {
                        item.isComplete -> "Completed"
                        item.isCanceled -> "Canceled"
                        item.isPaused -> "Paused (${item.percentComplete}%)"
                        item.isInProgress -> "${item.percentComplete}%"
                        else -> "Pending"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        item.isComplete -> KromiumColors.Success
                        item.isCanceled -> KromiumColors.Error
                        item.isPaused -> KromiumColors.Warning
                        else -> KromiumColors.Cyan
                    }
                )
            }

            // Action row for active downloads: Cancel / Pause / Resume
            if (item.isInProgress) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { workspaceState.cancelDownload(item.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Error.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Cancel", fontSize = 10.sp, color = KromiumColors.Error, fontWeight = FontWeight.SemiBold)
                    }

                    if (item.isPaused) {
                        Button(
                            onClick = { workspaceState.resumeDownload(item.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Surface),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Resume", fontSize = 10.sp, color = KromiumColors.Cyan)
                        }
                    } else {
                        Button(
                            onClick = { workspaceState.pauseDownload(item.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Surface),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Pause", fontSize = 10.sp, color = KromiumColors.TextSecondary)
                        }
                    }
                }
            }

            // Action row for completed downloads: Open File / Show in Folder
            if (item.isComplete) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { workspaceState.showInFolder(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Surface),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Show in Folder", fontSize = 10.sp, color = KromiumColors.Cyan)
                    }

                    Button(
                        onClick = { workspaceState.openDownloadedFile(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.Surface),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Open File", fontSize = 10.sp, color = KromiumColors.TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearDataTab(workspaceState: WorkspaceState) {
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearDownloads by remember { mutableStateOf(true) }
    var clearLogs by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = KromiumColors.Cyan,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Clear Browsing Data",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = KromiumColors.TextPrimary
            )
        }

        Text(
            text = "Select data types to remove from Chromium engine storage and current session memory.",
            fontSize = 11.5.sp,
            color = KromiumColors.TextSecondary,
            lineHeight = 16.sp
        )

        Surface(
            color = KromiumColors.SurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KromiumColors.Border, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ClearDataCheckbox(
                    title = "Cookies and Site Data",
                    subtitle = "Signs you out of sites and clears stored cookie credentials",
                    checked = clearCookies,
                    onCheckedChange = { clearCookies = it }
                )

                HorizontalDivider(color = KromiumColors.BorderSubtle)

                ClearDataCheckbox(
                    title = "Cached Images and Files",
                    subtitle = "Frees disk cache and forces fresh network loads on next visit",
                    checked = clearCache,
                    onCheckedChange = { clearCache = it }
                )

                HorizontalDivider(color = KromiumColors.BorderSubtle)

                ClearDataCheckbox(
                    title = "Download History",
                    subtitle = "Clears records of downloaded files (${workspaceState.downloads.size} items)",
                    checked = clearDownloads,
                    onCheckedChange = { clearDownloads = it }
                )

                HorizontalDivider(color = KromiumColors.BorderSubtle)

                ClearDataCheckbox(
                    title = "Console & Developer Logs",
                    subtitle = "Clears JavaScript execution and inspection logs across tabs",
                    checked = clearLogs,
                    onCheckedChange = { clearLogs = it }
                )
            }
        }

        if (workspaceState.clearDataStatusMessage != null) {
            Surface(
                color = KromiumColors.Success.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KromiumColors.Success.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = KromiumColors.Success, modifier = Modifier.size(16.dp))
                    Text(
                        text = workspaceState.clearDataStatusMessage ?: "",
                        fontSize = 12.sp,
                        color = KromiumColors.Success,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Button(
            onClick = {
                workspaceState.clearBrowsingData(
                    clearCookies = clearCookies,
                    clearCache = clearCache,
                    clearDownloadHistory = clearDownloads,
                    clearLogs = clearLogs
                )
            },
            enabled = !workspaceState.isClearingData,
            colors = ButtonDefaults.buttonColors(containerColor = KromiumColors.CyanDark),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            if (workspaceState.isClearingData) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clearing...", fontSize = 12.5.sp, color = Color.White)
            } else {
                Text("Clear Selected Data", fontSize = 12.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClearDataCheckbox(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = KromiumColors.Cyan,
                uncheckedColor = KromiumColors.TextMuted,
                checkmarkColor = KromiumColors.Background
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = KromiumColors.TextPrimary)
            Text(text = subtitle, fontSize = 10.5.sp, color = KromiumColors.TextMuted)
        }
    }
}
