package dev.daviante.kromium.demo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daviante.kromium.demo.theme.KromiumColors
import dev.daviante.kromium.demo.util.formatBytes
import dev.daviante.kromium.domain.model.KromiumState

@Composable
fun StartupScreen(
    state: KromiumState,
    modifier: Modifier = Modifier
) {
    // Subtle breathing animation for logo glow
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KromiumColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(480.dp)
                .border(1.dp, KromiumColors.Border, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = KromiumColors.Surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // Logo with glowing pulse
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(KromiumColors.GlowGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = LogoAsset.painter,
                        contentDescription = "Kromium Logo",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                // Brand Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "KROMIUM",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KromiumColors.TextPrimary,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Embedded Chromium for Compose Desktop",
                        fontSize = 13.sp,
                        color = KromiumColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Progress state
                when (state) {
                    is KromiumState.Downloading -> {
                        val progress = state.progress
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading Native Runtime...",
                                    fontSize = 12.sp,
                                    color = KromiumColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${progress.percentage}%",
                                    fontSize = 12.sp,
                                    color = KromiumColors.Cyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LinearProgressIndicator(
                                progress = { progress.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = KromiumColors.Cyan,
                                trackColor = KromiumColors.SurfaceElevated
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val total = progress.totalBytes
                                val bytesStr = if (total != null && total > 0) {
                                    "${formatBytes(progress.bytesRead)} / ${formatBytes(total)}"
                                } else {
                                    formatBytes(progress.bytesRead)
                                }
                                Text(
                                    text = bytesStr,
                                    fontSize = 11.sp,
                                    color = KromiumColors.TextMuted
                                )
                            }
                        }
                    }

                    is KromiumState.Extracting -> {
                        IndeterminateStepIndicator("Extracting native Chromium libraries...")
                    }

                    is KromiumState.Initializing -> {
                        IndeterminateStepIndicator("Bootstrapping CEF core...")
                    }

                    is KromiumState.Locating -> {
                        IndeterminateStepIndicator("Locating local Chromium installation...")
                    }

                    else -> {
                        IndeterminateStepIndicator("Preparing environment...")
                    }
                }
            }
        }
    }
}

@Composable
private fun IndeterminateStepIndicator(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = KromiumColors.Cyan,
            trackColor = KromiumColors.SurfaceElevated
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = KromiumColors.TextSecondary
        )
    }
}
