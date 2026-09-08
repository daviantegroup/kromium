package dev.daviante.kromium.demo.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object KromiumColors {
    val Background = Color(0xFF090D16)
    val Surface = Color(0xFF0F172A)
    val SurfaceElevated = Color(0xFF1E293B)
    val SurfaceHighlight = Color(0xFF273549)
    val Border = Color(0xFF334155)
    val BorderSubtle = Color(0xFF1E293B)

    // Daviante Brand Accents (derived from logo.png)
    val Cyan = Color(0xFF38BDF8)
    val CyanDark = Color(0xFF0284C7)
    val Emerald = Color(0xFF34D399)
    val EmeraldDark = Color(0xFF059669)

    val BrandGradient = Brush.horizontalGradient(
        listOf(Color(0xFF38BDF8), Color(0xFF34D399))
    )

    val BrandVerticalGradient = Brush.verticalGradient(
        listOf(Color(0xFF38BDF8), Color(0xFF34D399))
    )

    val GlowGradient = Brush.radialGradient(
        listOf(Color(0x3338BDF8), Color(0x00090D16))
    )

    // Typography colors
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    // Status colors
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
}
