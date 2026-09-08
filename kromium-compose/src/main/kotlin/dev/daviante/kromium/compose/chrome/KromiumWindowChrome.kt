package dev.daviante.kromium.compose.chrome

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.daviante.kromium.presentation.chrome.KromiumWindowChrome

/**
 * Standard reference width allocated for native macOS traffic lights.
 * Completely customizable by the developer.
 */
val DefaultMacTrafficLightsWidth: Dp = KromiumWindowChrome.DEFAULT_MAC_TRAFFIC_LIGHTS_WIDTH.dp

/**
 * Standard reference height allocated for native macOS traffic lights.
 * Completely customizable by the developer.
 */
val DefaultMacTrafficLightsHeight: Dp = KromiumWindowChrome.DEFAULT_MAC_TRAFFIC_LIGHTS_HEIGHT.dp

/**
 * Applies horizontal start padding for macOS traffic lights only when [enabled] is `true`
 * and running on macOS. On all other operating systems or when [enabled] is `false`,
 * this modifier applies zero padding.
 *
 * @param enabled Whether the traffic lights padding is active. Defaults to true.
 * @param width The custom width to allocate if enabled. Defaults to [DefaultMacTrafficLightsWidth] (76.dp).
 */
fun Modifier.macTrafficLightsPadding(
    enabled: Boolean = true,
    width: Dp = DefaultMacTrafficLightsWidth
): Modifier {
    return if (enabled && KromiumWindowChrome.isMac()) {
        this.padding(start = width)
    } else {
        this
    }
}

/**
 * Alias for [macTrafficLightsPadding].
 */
fun Modifier.macTrafficLightsInset(
    enabled: Boolean = true,
    width: Dp = DefaultMacTrafficLightsWidth
): Modifier = macTrafficLightsPadding(enabled = enabled, width = width)

/**
 * A platform-adaptive horizontal spacer that reserves [width] on macOS when [enabled] is `true`,
 * and zero width on Windows and Linux or when [enabled] is `false`.
 *
 * @param enabled Whether the spacer is active. Defaults to true.
 * @param width The custom width to reserve if active. Defaults to [DefaultMacTrafficLightsWidth] (76.dp).
 * @param modifier Additional modifiers for the spacer.
 */
@Composable
fun MacTrafficLightsSpacer(
    enabled: Boolean = true,
    width: Dp = DefaultMacTrafficLightsWidth,
    modifier: Modifier = Modifier
) {
    val effectiveWidth = if (enabled && KromiumWindowChrome.isMac()) width else 0.dp
    Spacer(modifier = modifier.width(effectiveWidth))
}
