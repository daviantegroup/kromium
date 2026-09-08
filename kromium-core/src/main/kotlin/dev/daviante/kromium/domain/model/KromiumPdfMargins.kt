package dev.daviante.kromium.domain.model

/**
 * Page margin configuration for PDF printing.
 */
sealed class KromiumPdfMargins {

    /** Standard Chromium default margins (0.4 inches / ~10 mm). */
    data object Default : KromiumPdfMargins()

    /** Borderless printing with zero margins. */
    data object None : KromiumPdfMargins()

    /** Minimum allowable margins (~0.1 inches / ~2.5 mm). */
    data object Minimum : KromiumPdfMargins()

    /**
     * Custom margins specified in inches.
     *
     * @property topInches Top margin in inches.
     * @property rightInches Right margin in inches.
     * @property bottomInches Bottom margin in inches.
     * @property leftInches Left margin in inches.
     */
    data class Custom(
        val topInches: Double,
        val rightInches: Double,
        val bottomInches: Double,
        val leftInches: Double
    ) : KromiumPdfMargins() {
        init {
            require(topInches >= 0.0) { "topInches must be >= 0, got: $topInches" }
            require(rightInches >= 0.0) { "rightInches must be >= 0, got: $rightInches" }
            require(bottomInches >= 0.0) { "bottomInches must be >= 0, got: $bottomInches" }
            require(leftInches >= 0.0) { "leftInches must be >= 0, got: $leftInches" }
        }
    }

    companion object {
        private const val MM_PER_INCH = 25.4

        /**
         * Creates custom margins with values specified in millimeters.
         */
        @JvmStatic
        fun fromMillimeters(topMm: Double, rightMm: Double, bottomMm: Double, leftMm: Double): Custom {
            return Custom(
                topInches = topMm / MM_PER_INCH,
                rightInches = rightMm / MM_PER_INCH,
                bottomInches = bottomMm / MM_PER_INCH,
                leftInches = leftMm / MM_PER_INCH
            )
        }

        /**
         * Creates custom margins with values specified in inches.
         */
        @JvmStatic
        fun fromInches(topInches: Double, rightInches: Double, bottomInches: Double, leftInches: Double): Custom {
            return Custom(topInches, rightInches, bottomInches, leftInches)
        }
    }
}
