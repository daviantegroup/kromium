package dev.daviante.kromium.domain.model

/**
 * Standard paper dimension definitions for PDF printing.
 *
 * @property widthInches Width of the page in inches.
 * @property heightInches Height of the page in inches.
 */
data class KromiumPaperSize(
    val widthInches: Double,
    val heightInches: Double
) {
    init {
        require(widthInches > 0.0) { "widthInches must be positive, got: $widthInches" }
        require(heightInches > 0.0) { "heightInches must be positive, got: $heightInches" }
    }

    companion object {
        private const val MM_PER_INCH = 25.4

        /** Standard ISO A4 (210 mm × 297 mm / 8.27 in × 11.69 in). */
        @JvmField
        val A4 = KromiumPaperSize(8.27, 11.69)

        /** Standard North American Letter (8.5 in × 11.0 in / 215.9 mm × 279.4 mm). */
        @JvmField
        val Letter = KromiumPaperSize(8.5, 11.0)

        /** Standard North American Legal (8.5 in × 14.0 in / 215.9 mm × 355.6 mm). */
        @JvmField
        val Legal = KromiumPaperSize(8.5, 14.0)

        /** Standard Tabloid / Ledger (11.0 in × 17.0 in / 279.4 mm × 431.8 mm). */
        @JvmField
        val Tabloid = KromiumPaperSize(11.0, 17.0)

        /** Standard ISO A3 (297 mm × 420 mm / 11.69 in × 16.54 in). */
        @JvmField
        val A3 = KromiumPaperSize(11.69, 16.54)

        /** Standard ISO A5 (148 mm × 210 mm / 5.83 in × 8.27 in). */
        @JvmField
        val A5 = KromiumPaperSize(5.83, 8.27)

        /**
         * Creates a paper size with dimensions specified in millimeters.
         */
        @JvmStatic
        fun fromMillimeters(widthMm: Double, heightMm: Double): KromiumPaperSize {
            return KromiumPaperSize(widthMm / MM_PER_INCH, heightMm / MM_PER_INCH)
        }

        /**
         * Creates a paper size with dimensions specified in inches.
         */
        @JvmStatic
        fun fromInches(widthInches: Double, heightInches: Double): KromiumPaperSize {
            return KromiumPaperSize(widthInches, heightInches)
        }
    }
}
