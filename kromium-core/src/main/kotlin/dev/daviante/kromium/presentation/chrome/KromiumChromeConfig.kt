package dev.daviante.kromium.presentation.chrome

/**
 * Configuration options for embedding custom browser tab strips and window headers
 * into the native OS titlebar area.
 *
 * All parameters are optional and fully configurable by the host application.
 * By default, [enabled] is `false`, ensuring zero alterations to standard window behavior.
 *
 * @property enabled Whether custom window chrome styling is active. Defaults to false.
 * @property macTrafficLightsWidth Custom width in points/pixels allocated for macOS traffic lights. Defaults to 76.
 * @property macTrafficLightsHeight Custom height in points/pixels allocated for macOS traffic lights. Defaults to 38.
 * @property transparentTitleBar Whether the titlebar background should be transparent on macOS. Defaults to true.
 * @property hideWindowTitle Whether the default OS window title text is hidden. Defaults to true.
 */
data class KromiumChromeConfig(
    val enabled: Boolean = false,
    val macTrafficLightsWidth: Int = KromiumWindowChrome.DEFAULT_MAC_TRAFFIC_LIGHTS_WIDTH,
    val macTrafficLightsHeight: Int = KromiumWindowChrome.DEFAULT_MAC_TRAFFIC_LIGHTS_HEIGHT,
    val transparentTitleBar: Boolean = true,
    val hideWindowTitle: Boolean = true
) {
    /**
     * Fluent builder for pure Java applications.
     */
    class Builder {
        private var enabled: Boolean = true
        private var macTrafficLightsWidth: Int = KromiumWindowChrome.DEFAULT_MAC_TRAFFIC_LIGHTS_WIDTH
        private var macTrafficLightsHeight: Int = KromiumWindowChrome.DEFAULT_MAC_TRAFFIC_LIGHTS_HEIGHT
        private var transparentTitleBar: Boolean = true
        private var hideWindowTitle: Boolean = true

        fun enabled(enabled: Boolean): Builder = apply { this.enabled = enabled }
        fun macTrafficLightsWidth(width: Int): Builder = apply { this.macTrafficLightsWidth = width }
        fun macTrafficLightsHeight(height: Int): Builder = apply { this.macTrafficLightsHeight = height }
        fun transparentTitleBar(transparent: Boolean): Builder = apply { this.transparentTitleBar = transparent }
        fun hideWindowTitle(hide: Boolean): Builder = apply { this.hideWindowTitle = hide }

        fun build(): KromiumChromeConfig = KromiumChromeConfig(
            enabled = enabled,
            macTrafficLightsWidth = macTrafficLightsWidth,
            macTrafficLightsHeight = macTrafficLightsHeight,
            transparentTitleBar = transparentTitleBar,
            hideWindowTitle = hideWindowTitle
        )
    }

    companion object {
        /** Creates a new builder instance for Java callers. */
        @JvmStatic
        fun builder(): Builder = Builder()

        /** Preconfigured instance with custom chrome completely disabled. */
        @JvmField
        val DISABLED: KromiumChromeConfig = KromiumChromeConfig(enabled = false)

        /** Default baseline configuration (disabled by default). */
        @JvmField
        val DEFAULT: KromiumChromeConfig = KromiumChromeConfig(enabled = false)
    }
}
