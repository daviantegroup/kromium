package dev.daviante.kromium.domain.model

import org.cef.misc.CefPdfPrintSettings

/**
 * Comprehensive configuration settings for exporting web pages to vector PDF documents.
 *
 * @property landscape True for landscape orientation, false for portrait. Defaults to false.
 * @property printBackground True to print CSS background graphics, gradients, and colors. Defaults to false.
 * @property scale Scaling of the webpage rendering (1.0 = 100%). Defaults to 1.0.
 * @property paperSize Dimensions of the output paper. Defaults to [KromiumPaperSize.A4].
 * @property preferCssPageSize True to prefer page size defined by CSS `@page` rule. Defaults to false.
 * @property margins Margin specifications for the page. Defaults to [KromiumPdfMargins.Default].
 * @property pageRanges Page ranges to print (e.g. "1-5, 8, 11-13"). Empty prints all pages.
 * @property displayHeaderFooter True to display HTML header and footer. Defaults to false.
 * @property headerTemplate HTML template for the print header.
 * @property footerTemplate HTML template for the print footer.
 * @property createDirectories True to automatically create missing parent directories for the target file. Defaults to false.
 * @property generateTaggedPdf True to generate a tagged (accessible) PDF. Defaults to false.
 * @property generateDocumentOutline True to generate document outline (bookmarks) from headers. Defaults to false.
 */
data class KromiumPdfSettings @JvmOverloads constructor(
    val landscape: Boolean = false,
    val printBackground: Boolean = false,
    val scale: Double = 1.0,
    val paperSize: KromiumPaperSize = KromiumPaperSize.A4,
    val preferCssPageSize: Boolean = false,
    val margins: KromiumPdfMargins = KromiumPdfMargins.Default,
    val pageRanges: String = "",
    val displayHeaderFooter: Boolean = false,
    val headerTemplate: String = "",
    val footerTemplate: String = "",
    val createDirectories: Boolean = false,
    val generateTaggedPdf: Boolean = false,
    val generateDocumentOutline: Boolean = false
) {
    init {
        require(scale > 0.0) { "scale must be positive, got: $scale" }
    }

    /**
     * Converts these settings to native JCEF [CefPdfPrintSettings].
     */
    fun toCefPdfPrintSettings(): CefPdfPrintSettings {
        val cefSettings = CefPdfPrintSettings()
        cefSettings.landscape = landscape
        cefSettings.print_background = printBackground
        cefSettings.scale = scale
        cefSettings.paper_width = paperSize.widthInches
        cefSettings.paper_height = paperSize.heightInches
        cefSettings.prefer_css_page_size = preferCssPageSize
        cefSettings.page_ranges = pageRanges
        cefSettings.display_header_footer = displayHeaderFooter
        cefSettings.header_template = headerTemplate
        cefSettings.footer_template = footerTemplate
        cefSettings.generate_tagged_pdf = generateTaggedPdf
        cefSettings.generate_document_outline = generateDocumentOutline

        when (val m = margins) {
            is KromiumPdfMargins.Default -> {
                cefSettings.margin_type = CefPdfPrintSettings.MarginType.DEFAULT
            }
            is KromiumPdfMargins.None -> {
                cefSettings.margin_type = CefPdfPrintSettings.MarginType.NONE
            }
            is KromiumPdfMargins.Minimum -> {
                cefSettings.margin_type = CefPdfPrintSettings.MarginType.CUSTOM
                cefSettings.margin_top = 0.1
                cefSettings.margin_right = 0.1
                cefSettings.margin_bottom = 0.1
                cefSettings.margin_left = 0.1
            }
            is KromiumPdfMargins.Custom -> {
                cefSettings.margin_type = CefPdfPrintSettings.MarginType.CUSTOM
                cefSettings.margin_top = m.topInches
                cefSettings.margin_right = m.rightInches
                cefSettings.margin_bottom = m.bottomInches
                cefSettings.margin_left = m.leftInches
            }
        }

        return cefSettings
    }

    companion object {
        /** Default PDF print settings. */
        @JvmField
        val Default = KromiumPdfSettings()

        /**
         * Creates a new fluent [Builder] for Java callers.
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * Fluent builder for constructing [KromiumPdfSettings] in Java or Kotlin.
     */
    class Builder {
        private var landscape: Boolean = false
        private var printBackground: Boolean = false
        private var scale: Double = 1.0
        private var paperSize: KromiumPaperSize = KromiumPaperSize.A4
        private var preferCssPageSize: Boolean = false
        private var margins: KromiumPdfMargins = KromiumPdfMargins.Default
        private var pageRanges: String = ""
        private var displayHeaderFooter: Boolean = false
        private var headerTemplate: String = ""
        private var footerTemplate: String = ""
        private var createDirectories: Boolean = false
        private var generateTaggedPdf: Boolean = false
        private var generateDocumentOutline: Boolean = false

        fun landscape(landscape: Boolean): Builder = apply { this.landscape = landscape }
        fun printBackground(printBackground: Boolean): Builder = apply { this.printBackground = printBackground }
        fun scale(scale: Double): Builder = apply { this.scale = scale }
        fun paperSize(paperSize: KromiumPaperSize): Builder = apply { this.paperSize = paperSize }
        fun preferCssPageSize(preferCssPageSize: Boolean): Builder = apply { this.preferCssPageSize = preferCssPageSize }
        fun margins(margins: KromiumPdfMargins): Builder = apply { this.margins = margins }
        fun pageRanges(pageRanges: String): Builder = apply { this.pageRanges = pageRanges }
        fun displayHeaderFooter(displayHeaderFooter: Boolean): Builder = apply { this.displayHeaderFooter = displayHeaderFooter }
        fun headerTemplate(headerTemplate: String): Builder = apply { this.headerTemplate = headerTemplate }
        fun footerTemplate(footerTemplate: String): Builder = apply { this.footerTemplate = footerTemplate }
        fun createDirectories(createDirectories: Boolean): Builder = apply { this.createDirectories = createDirectories }
        fun generateTaggedPdf(generateTaggedPdf: Boolean): Builder = apply { this.generateTaggedPdf = generateTaggedPdf }
        fun generateDocumentOutline(generateDocumentOutline: Boolean): Builder = apply { this.generateDocumentOutline = generateDocumentOutline }

        fun build(): KromiumPdfSettings = KromiumPdfSettings(
            landscape = landscape,
            printBackground = printBackground,
            scale = scale,
            paperSize = paperSize,
            preferCssPageSize = preferCssPageSize,
            margins = margins,
            pageRanges = pageRanges,
            displayHeaderFooter = displayHeaderFooter,
            headerTemplate = headerTemplate,
            footerTemplate = footerTemplate,
            createDirectories = createDirectories,
            generateTaggedPdf = generateTaggedPdf,
            generateDocumentOutline = generateDocumentOutline
        )
    }
}
