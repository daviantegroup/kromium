package dev.daviante.kromium.demo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daviante.kromium.demo.model.BrowserTab
import dev.daviante.kromium.demo.theme.KromiumColors

@Composable
fun TabBar(
    tabs: List<BrowserTab>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = KromiumColors.Surface,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(width = 1.dp, color = KromiumColors.BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                tabs.forEach { tab ->
                    val isActive = tab.id == activeTabId
                    TabItemView(
                        tab = tab,
                        isActive = isActive,
                        onSelect = { onSelectTab(tab.id) },
                        onClose = { onCloseTab(tab.id) }
                    )
                }
            }

            // New Tab Button
            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(32.dp)
                    .padding(bottom = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = KromiumColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TabItemView(
    tab: BrowserTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (isActive) KromiumColors.SurfaceElevated else Color.Transparent
    )

    val textColor by animateColorAsState(
        if (isActive) KromiumColors.TextPrimary else KromiumColors.TextSecondary
    )

    Box(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 220.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (isActive) KromiumColors.Border else Color.Transparent,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tab.viewState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = KromiumColors.Cyan
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Tab Icon",
                    tint = if (isActive) KromiumColors.Cyan else KromiumColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = tab.displayTitle,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Close tab icon button
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = if (isActive) KromiumColors.TextSecondary else KromiumColors.TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Active indicator line
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(KromiumColors.BrandGradient)
            )
        }
    }
}
