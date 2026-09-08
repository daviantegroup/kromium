package dev.daviante.kromium.demo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daviante.kromium.demo.theme.KromiumColors

@Composable
fun AppHeader(
    modifier: Modifier = Modifier
) {
    Surface(
        color = KromiumColors.Surface,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, KromiumColors.BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand identification
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Daviante Kromium Logo
                Image(
                    painter = LogoAsset.painter,
                    contentDescription = "Kromium Logo",
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Text(
                    text = "KROMIUM",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = KromiumColors.TextPrimary,
                    letterSpacing = 1.2.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(KromiumColors.SurfaceElevated)
                        .border(1.dp, KromiumColors.Border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "CEF 150 • Compose Desktop",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = KromiumColors.Cyan
                    )
                }
            }

            // Right header status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(KromiumColors.Success)
                )
                Text(
                    text = "CEF Engine Ready",
                    fontSize = 11.sp,
                    color = KromiumColors.TextSecondary
                )
            }
        }
    }
}
