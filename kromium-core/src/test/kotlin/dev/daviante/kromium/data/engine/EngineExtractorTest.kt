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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import dev.daviante.kromium.core.util.JvmModuleOpener
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

    private fun createSampleZip(archiveFile: File, entries: List<Pair<String, ByteArray>>) {
        FileOutputStream(archiveFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                ZipArchiveOutputStream(bos).use { zipOut ->
                    for ((name, content) in entries) {
                        val entry = ZipArchiveEntry(name)
                        zipOut.putArchiveEntry(entry)
                        zipOut.write(content)
                        zipOut.closeArchiveEntry()
                    }
                }
            }
        }
    }

    @Test
    fun testExtractAndFlattenTarGzArchive() {
        val tempDir = Files.createTempDirectory("extractor_targz_test").toFile()
        val archiveFile = File(tempDir, "sample.tar.gz")
        val extractDir = File(tempDir, "extracted")
        extractDir.mkdirs()

        try {
            val entries = listOf(
                "jbr_bundle/bin/jcef_helper.exe" to "binary-content".toByteArray(),
                "jbr_bundle/lib/libcef.so" to "cef-content".toByteArray()
            )
            createSampleTarGz(archiveFile, entries)

            EngineExtractor.extractArchive(archiveFile, extractDir)

            val helper = File(extractDir, "bin/jcef_helper.exe")
            val libcef = File(extractDir, "lib/libcef.so")

            assertTrue(helper.exists(), "Helper executable should exist after flattening")
            assertTrue(libcef.exists(), "libcef should exist after flattening")
            assertEquals("binary-content", helper.readText())
            assertEquals("cef-content", libcef.readText())
            assertTrue(helper.canExecute(), "Binary should be marked executable")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testExtractAndFlattenZipArchive() {
        val tempDir = Files.createTempDirectory("extractor_zip_test").toFile()
        val archiveFile = File(tempDir, "sample.zip")
        val extractDir = File(tempDir, "extracted")
        extractDir.mkdirs()

        try {
            val entries = listOf(
                "windows_bundle/bin/jcef_helper.exe" to "windows-helper".toByteArray(),
                "windows_bundle/bin/libcef.dll" to "windows-libcef".toByteArray()
            )
            createSampleZip(archiveFile, entries)

            EngineExtractor.extractArchive(archiveFile, extractDir)

            val helper = File(extractDir, "bin/jcef_helper.exe")
            val libcef = File(extractDir, "bin/libcef.dll")

            assertTrue(helper.exists(), "Helper executable should exist after zip flattening")
            assertTrue(libcef.exists(), "libcef.dll should exist after zip flattening")
            assertEquals("windows-helper", helper.readText())
            assertEquals("windows-libcef", libcef.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRejectZipSlipEntryInTarGz() {
        val tempDir = Files.createTempDirectory("extractor_zipslip_targz").toFile()
        val archiveFile = File(tempDir, "malicious.tar.gz")
        val extractDir = File(tempDir, "extracted")
        extractDir.mkdirs()

        try {
            val entries = listOf(
                "../../evil.txt" to "malicious".toByteArray()
            )
            createSampleTarGz(archiveFile, entries)

            assertFailsWith<KromiumException.MaliciousArchiveEntry> {
                EngineExtractor.extractArchive(archiveFile, extractDir)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRejectZipSlipEntryInZip() {
        val tempDir = Files.createTempDirectory("extractor_zipslip_zip").toFile()
        val archiveFile = File(tempDir, "malicious.zip")
        val extractDir = File(tempDir, "extracted")
        extractDir.mkdirs()

        try {
            val entries = listOf(
                "../../evil.txt" to "malicious".toByteArray()
            )
            createSampleZip(archiveFile, entries)

            assertFailsWith<KromiumException.MaliciousArchiveEntry> {
                EngineExtractor.extractArchive(archiveFile, extractDir)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testJvmModuleOpenerRunsCleanly() {
        // Should execute idempotently without throwing any exceptions
        JvmModuleOpener.ensureModulesOpened()
        JvmModuleOpener.ensureModulesOpened()
    }
}
