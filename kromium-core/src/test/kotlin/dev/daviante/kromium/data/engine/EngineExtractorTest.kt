package dev.daviante.kromium.data.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import dev.daviante.kromium.domain.exception.KromiumException

class EngineExtractorTest {

    private fun createSampleTarGz(archiveFile: File, entries: List<Pair<String, ByteArray>>) {
        FileOutputStream(archiveFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                GzipCompressorOutputStream(bos).use { gzos ->
                    TarArchiveOutputStream(gzos).use { tarOut ->
                        for ((name, content) in entries) {
                            val entry = TarArchiveEntry(name)
                            entry.size = content.size.toLong()
                            tarOut.putArchiveEntry(entry)
                            tarOut.write(content)
                            tarOut.closeArchiveEntry()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testExtractAndFlattenArchive() {
        val tempDir = Files.createTempDirectory("extractor_test").toFile()
        val archiveFile = File(tempDir, "sample.tar.gz")
        val extractDir = File(tempDir, "extracted")
        extractDir.mkdirs()

        try {
            // Nested inside single root dir: root/bin/jcef_helper.exe and root/lib/libcef.so
            val entries = listOf(
                "jbr_bundle/bin/jcef_helper.exe" to "binary-content".toByteArray(),
                "jbr_bundle/lib/libcef.so" to "cef-content".toByteArray()
            )
            createSampleTarGz(archiveFile, entries)

            EngineExtractor.extractTarGz(archiveFile, extractDir)

            // Verify single root folder was flattened
            val helper = File(extractDir, "bin/jcef_helper.exe")
            val libcef = File(extractDir, "lib/libcef.so")

            assertTrue(helper.exists(), "Helper executable should exist after flattening")
            assertTrue(libcef.exists(), "libcef should exist after flattening")
            assertEquals("binary-content", helper.readText())
            assertEquals("cef-content", libcef.readText())

            // Executable permissions should be enabled
            assertTrue(helper.canExecute(), "Binary should be marked executable")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRejectZipSlipEntry() {
        val tempDir = Files.createTempDirectory("extractor_zipslip_test").toFile()
        val archiveFile = File(tempDir, "malicious.tar.gz")
        val extractDir = File(tempDir, "extracted")
        extractDir.mkdirs()

        try {
            val entries = listOf(
                "../../evil.txt" to "malicious".toByteArray()
            )
            createSampleTarGz(archiveFile, entries)

            assertFailsWith<KromiumException.MaliciousArchiveEntry> {
                EngineExtractor.extractTarGz(archiveFile, extractDir)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
