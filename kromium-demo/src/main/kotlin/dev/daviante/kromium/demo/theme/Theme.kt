package dev.daviante.kromium.demo.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = KromiumColors.Cyan,
    secondary = KromiumColors.Emerald,
    background = KromiumColors.Background,
    surface = KromiumColors.Surface,
    surfaceVariant = KromiumColors.SurfaceElevated,
    onPrimary = KromiumColors.Background,
    onSecondary = KromiumColors.Background,
    onBackground = KromiumColors.TextPrimary,
    onSurface = KromiumColors.TextPrimary,
    onSurfaceVariant = KromiumColors.TextSecondary,
    outline = KromiumColors.Border,
    error = KromiumColors.Error
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun KromiumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        shapes = AppShapes,
        content = content
    )
}
