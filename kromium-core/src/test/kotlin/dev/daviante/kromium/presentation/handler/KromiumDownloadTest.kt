package dev.daviante.kromium.presentation.handler

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KromiumDownloadTest {

    @Test
    fun `KromiumDownloadItem default values and properties are correctly maintained`() {
        val item = KromiumDownloadItem(
            id = 42,
            url = "https://kromium.daviante.dev/assets/bundle.zip",
            suggestedFileName = "bundle.zip",
            totalBytes = 1048576L,
            receivedBytes = 524288L,
            percentComplete = 50,
            speed = 102400L,
            isInProgress = true,
            isComplete = false,
            isCanceled = false,
            fullPath = "/Users/test/Downloads/bundle.zip"
        )

        assertEquals(42, item.id)
        assertEquals("https://kromium.daviante.dev/assets/bundle.zip", item.url)
        assertEquals("bundle.zip", item.suggestedFileName)
        assertEquals(1048576L, item.totalBytes)
        assertEquals(524288L, item.receivedBytes)
        assertEquals(50, item.percentComplete)
        assertEquals(102400L, item.speed)
        assertTrue(item.isInProgress)
        assertFalse(item.isComplete)
        assertFalse(item.isCanceled)
        assertEquals("/Users/test/Downloads/bundle.zip", item.fullPath)
    }

    @Test
    fun `download filename sanitization strips directories and invalid characters`() {
        val unsafeNames = listOf(
            "../../etc/passwd" to "passwd",
            "..\\..\\windows\\system32\\calc.exe" to "calc.exe",
            "hello/world:test?.zip" to "world_test_.zip",
            "valid_file.tar.gz" to "valid_file.tar.gz"
        )

        for ((input, expected) in unsafeNames) {
            val clean = input.substringAfterLast('/').substringAfterLast('\\')
                .replace("[?%*:|\"<>]".toRegex(), "_")
                .ifBlank { "download" }
            assertEquals(expected, clean)
        }
    }

    @Test
    fun `non-conflicting file naming increments counter for existing files`() {
        val tempDir = java.nio.file.Files.createTempDirectory("kromium-download-test").toFile()
        try {
            val baseFile = File(tempDir, "sample.txt")
            baseFile.writeText("first")

            // Simulate non-conflicting resolution
            fun resolveNonConflicting(dir: File, fileName: String): File {
                var file = File(dir, fileName)
                if (!file.exists()) return file
                val base = fileName.substringBeforeLast('.', "")
                val ext = fileName.substringAfterLast('.', "")
                val prefix = if (base.isEmpty()) fileName else base
                val suffix = if (ext.isEmpty() || ext == fileName) "" else ".$ext"
                var counter = 1
                while (file.exists()) {
                    file = File(dir, "$prefix ($counter)$suffix")
                    counter++
                }
                return file
            }

            val nextFile = resolveNonConflicting(tempDir, "sample.txt")
            assertEquals("sample (1).txt", nextFile.name)

            nextFile.writeText("second")
            val thirdFile = resolveNonConflicting(tempDir, "sample.txt")
            assertEquals("sample (2).txt", thirdFile.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
