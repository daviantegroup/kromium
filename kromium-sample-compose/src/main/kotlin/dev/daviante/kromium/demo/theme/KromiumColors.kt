package dev.daviante.kromium.demo.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object KromiumColors {
    // Pure Monochrome Design System (Black & White)
    val Background = Color(0xFF0F0F0F)
    val Surface = Color(0xFF181818)
    val SurfaceElevated = Color(0xFF222222)
    val SurfaceHighlight = Color(0xFF2D2D2D)
    val Border = Color(0xFF333333)
    val BorderSubtle = Color(0xFF202020)

    // Crisp White & Neutral Accents
    val Cyan = Color(0xFFFFFFFF)
    val CyanDark = Color(0xFFE5E5E5)
    val Emerald = Color(0xFFFFFFFF)
    val EmeraldDark = Color(0xFFD4D4D4)

    val BrandGradient = Brush.horizontalGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFD4D4D4))
    )

    val BrandVerticalGradient = Brush.verticalGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFD4D4D4))
    )

    val GlowGradient = Brush.radialGradient(
        listOf(Color(0x1AFFFFFF), Color(0x00000000))
    )

    // Typography colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0A0)
    val TextMuted = Color(0xFF666666)

    // Status colors
    val Success = Color(0xFFE5E5E5)
    val Warning = Color(0xFFD4D4D4)
    val Error = Color(0xFFEF4444)
}
