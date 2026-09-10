package dev.daviante.kromium.presentation.browser

import dev.daviante.kromium.domain.exception.KromiumException
import dev.daviante.kromium.domain.model.KromiumPaperSize
import dev.daviante.kromium.domain.model.KromiumPdfMargins
import dev.daviante.kromium.domain.model.KromiumPdfSettings
import org.cef.misc.CefPdfPrintSettings
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KromiumPdfSettingsTest {

    @Test
    fun testPaperSizeDimensionsAndConversions() {
        assertEquals(8.27, KromiumPaperSize.A4.widthInches)
        assertEquals(11.69, KromiumPaperSize.A4.heightInches)

        assertEquals(8.5, KromiumPaperSize.Letter.widthInches)
        assertEquals(11.0, KromiumPaperSize.Letter.heightInches)

        assertEquals(8.5, KromiumPaperSize.Legal.widthInches)
        assertEquals(14.0, KromiumPaperSize.Legal.heightInches)

        assertEquals(11.0, KromiumPaperSize.Tabloid.widthInches)
        assertEquals(17.0, KromiumPaperSize.Tabloid.heightInches)

        // Millimeter conversion: 210mm x 297mm (A4)
        val convertedA4 = KromiumPaperSize.fromMillimeters(210.0, 297.0)
        assertTrue(abs(convertedA4.widthInches - 8.2677) < 0.01)
        assertTrue(abs(convertedA4.heightInches - 11.6929) < 0.01)

        // Negative dimensions must fail
        assertFailsWith<IllegalArgumentException> {
            KromiumPaperSize(-1.0, 10.0)
        }
        assertFailsWith<IllegalArgumentException> {
            KromiumPaperSize(8.0, 0.0)
        }
    }

    @Test
    fun testPdfMarginsAndConversions() {
        assertEquals(KromiumPdfMargins.Default, KromiumPdfMargins.Default)
        assertEquals(KromiumPdfMargins.None, KromiumPdfMargins.None)
        assertEquals(KromiumPdfMargins.Minimum, KromiumPdfMargins.Minimum)

        val custom = KromiumPdfMargins.fromMillimeters(10.0, 15.0, 10.0, 15.0)
        assertTrue(abs(custom.topInches - (10.0 / 25.4)) < 0.001)
        assertTrue(abs(custom.rightInches - (15.0 / 25.4)) < 0.001)

        assertFailsWith<IllegalArgumentException> {
            KromiumPdfMargins.Custom(-0.1, 0.5, 0.5, 0.5)
        }
    }

    @Test
    fun testPdfSettingsDefaultsAndDecisions() {
        val settings = KromiumPdfSettings.Default

        // Verify developer-controlled defaults per user decision
        assertFalse(settings.landscape)
        assertFalse(settings.printBackground)
        assertFalse(settings.createDirectories)
        assertEquals(1.0, settings.scale)
        assertEquals(KromiumPaperSize.A4, settings.paperSize)
        assertEquals(KromiumPdfMargins.Default, settings.margins)
        assertEquals("", settings.pageRanges)
        assertFalse(settings.displayHeaderFooter)
        assertFalse(settings.preferCssPageSize)

        assertFailsWith<IllegalArgumentException> {
            KromiumPdfSettings(scale = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            KromiumPdfSettings(scale = -1.5)
        }
    }

    @Test
    fun testToCefPdfPrintSettingsMapping() {
        val customMargins = KromiumPdfMargins.fromInches(0.5, 0.5, 0.5, 0.5)
        val settings = KromiumPdfSettings(
            landscape = true,
            printBackground = true,
            scale = 1.25,
            paperSize = KromiumPaperSize.Letter,
            preferCssPageSize = true,
            margins = customMargins,
            pageRanges = "1-3, 5",
            displayHeaderFooter = true,
            headerTemplate = "<span class=\"title\"></span>",
            footerTemplate = "<span class=\"pageNumber\"></span>",
            generateTaggedPdf = true,
            generateDocumentOutline = true
        )

        val cef = settings.toCefPdfPrintSettings()

        assertTrue(cef.landscape)
        assertTrue(cef.print_background)
        assertEquals(1.25, cef.scale)
        assertEquals(8.5, cef.paper_width)
        assertEquals(11.0, cef.paper_height)
        assertTrue(cef.prefer_css_page_size)
        assertEquals("1-3, 5", cef.page_ranges)
        assertTrue(cef.display_header_footer)
        assertEquals("<span class=\"title\"></span>", cef.header_template)
        assertEquals("<span class=\"pageNumber\"></span>", cef.footer_template)
        assertTrue(cef.generate_tagged_pdf)
        assertTrue(cef.generate_document_outline)

        assertEquals(CefPdfPrintSettings.MarginType.CUSTOM, cef.margin_type)
        assertEquals(0.5, cef.margin_top)
        assertEquals(0.5, cef.margin_right)
        assertEquals(0.5, cef.margin_bottom)
        assertEquals(0.5, cef.margin_left)

        // Test None margins mapping
        val noneCef = KromiumPdfSettings(margins = KromiumPdfMargins.None).toCefPdfPrintSettings()
        assertEquals(CefPdfPrintSettings.MarginType.NONE, noneCef.margin_type)

        // Test Minimum margins mapping
        val minCef = KromiumPdfSettings(margins = KromiumPdfMargins.Minimum).toCefPdfPrintSettings()
        assertEquals(CefPdfPrintSettings.MarginType.CUSTOM, minCef.margin_type)
        assertEquals(0.1, minCef.margin_top)
    }

    @Test
    fun testJavaBuilderErgonomics() {
        val settings = KromiumPdfSettings.builder()
            .landscape(true)
            .printBackground(true)
            .scale(0.9)
            .paperSize(KromiumPaperSize.Legal)
            .preferCssPageSize(true)
            .margins(KromiumPdfMargins.None)
            .pageRanges("1-10")
            .displayHeaderFooter(true)
            .headerTemplate("<div>Header</div>")
            .footerTemplate("<div>Footer</div>")
            .createDirectories(true)
            .generateTaggedPdf(true)
            .generateDocumentOutline(true)
            .build()

        assertTrue(settings.landscape)
        assertTrue(settings.printBackground)
        assertEquals(0.9, settings.scale)
        assertEquals(KromiumPaperSize.Legal, settings.paperSize)
        assertTrue(settings.preferCssPageSize)
        assertEquals(KromiumPdfMargins.None, settings.margins)
        assertEquals("1-10", settings.pageRanges)
        assertTrue(settings.displayHeaderFooter)
        assertEquals("<div>Header</div>", settings.headerTemplate)
        assertEquals("<div>Footer</div>", settings.footerTemplate)
        assertTrue(settings.createDirectories)
        assertTrue(settings.generateTaggedPdf)
        assertTrue(settings.generateDocumentOutline)
    }

    @Test
    fun testExceptionHierarchy() {
        val ex = KromiumException.PdfPrintFailed("/tmp/test.pdf", RuntimeException("Disk full"))
        assertEquals("/tmp/test.pdf", ex.path)
        assertEquals("Failed to print web page to PDF: /tmp/test.pdf", ex.message)
        assertEquals("Disk full", ex.cause?.message)
    }
}
