package dev.daviante.kromium.presentation.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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

    @Test
    fun `download control methods handle nonexistent downloads gracefully and track pause state`() {
        // ID 99999 does not exist, should return false gracefully
        assertFalse(dev.daviante.kromium.presentation.browser.Kromium.cancelDownload(99999))
        assertFalse(dev.daviante.kromium.presentation.browser.Kromium.pauseDownload(99999))
        assertFalse(dev.daviante.kromium.presentation.browser.Kromium.resumeDownload(99999))
        assertFalse(dev.daviante.kromium.presentation.browser.KromiumClient.isDownloadPausedGlobally(99999))

        // Verify isPaused property on KromiumDownloadItem
        val item = KromiumDownloadItem(
            id = 10,
            url = "https://example.com/file.zip",
            suggestedFileName = "file.zip",
            totalBytes = 1000L,
            receivedBytes = 500L,
            percentComplete = 50,
            speed = 0L,
            isInProgress = true,
            isComplete = false,
            isCanceled = false,
            isPaused = true
        )
        assertTrue(item.isPaused)
    }

    @Test
    fun `download lifecycle with pause, resume, cancel and cleanup operates correctly`() {
        val mockRawClient = io.mockk.mockk<org.cef.CefClient>(relaxed = true)
        val downloadHandlerSlot = io.mockk.slot<org.cef.handler.CefDownloadHandler>()

        val client = dev.daviante.kromium.presentation.browser.KromiumClient(mockRawClient)
        verify(exactly = 1) { mockRawClient.addDownloadHandler(capture(downloadHandlerSlot)) }
        val handler = downloadHandlerSlot.captured

        val mockItem = io.mockk.mockk<org.cef.callback.CefDownloadItem>(relaxed = true)
        val mockCallback = io.mockk.mockk<org.cef.callback.CefDownloadItemCallback>(relaxed = true)

        io.mockk.every { mockItem.id } returns 101
        io.mockk.every { mockItem.isInProgress } returns true
        io.mockk.every { mockItem.isComplete } returns false
        io.mockk.every { mockItem.isCanceled } returns false

        var emittedItem: KromiumDownloadItem? = null
        client.downloadListener = KromiumDownloadListener { emittedItem = it }

        // Trigger update
        handler.onDownloadUpdated(null, mockItem, mockCallback)
        assertEquals(101, emittedItem?.id)
        assertFalse(emittedItem!!.isPaused)

        // Pause
        assertTrue(client.pauseDownload(101))
        io.mockk.verify(exactly = 1) { mockCallback.pause() }
        assertTrue(client.isDownloadPaused(101))

        // Trigger update while paused
        handler.onDownloadUpdated(null, mockItem, mockCallback)
        assertTrue(emittedItem!!.isPaused)

        // Resume
        assertTrue(client.resumeDownload(101))
        io.mockk.verify(exactly = 1) { mockCallback.resume() }
        assertFalse(client.isDownloadPaused(101))

        // Cancel
        assertTrue(client.cancelDownload(101))
        io.mockk.verify(exactly = 1) { mockCallback.cancel() }
        assertFalse(client.isDownloadPaused(101))

        // Complete download
        io.mockk.every { mockItem.isInProgress } returns false
        io.mockk.every { mockItem.isComplete } returns true
        handler.onDownloadUpdated(null, mockItem, mockCallback)

        // Subsequent controls should return false as callback was deregistered
        assertFalse(client.cancelDownload(101))
        assertFalse(client.pauseDownload(101))
        assertFalse(client.resumeDownload(101))
    }

    @Test
    fun `onBeforeDownload cancels download cleanly when customPath is blank`() {
        val mockRawClient = io.mockk.mockk<org.cef.CefClient>(relaxed = true)
        val downloadHandlerSlot = io.mockk.slot<org.cef.handler.CefDownloadHandler>()

        val client = dev.daviante.kromium.presentation.browser.KromiumClient(mockRawClient)
        verify(exactly = 1) { mockRawClient.addDownloadHandler(capture(downloadHandlerSlot)) }
        val handler = downloadHandlerSlot.captured

        val mockItem = io.mockk.mockk<org.cef.callback.CefDownloadItem>(relaxed = true)
        val mockBeforeCallback = io.mockk.mockk<org.cef.callback.CefBeforeDownloadCallback>(relaxed = true)
        io.mockk.every { mockItem.id } returns 202
        io.mockk.every { mockItem.url } returns "https://example.com/file.pdf"
        io.mockk.every { mockItem.suggestedFileName } returns "file.pdf"

        // Cancel download by returning blank string
        client.onBeforeDownloadListener = { _, _ -> "" }
        val allowed = handler.onBeforeDownload(null, mockItem, "file.pdf", mockBeforeCallback)

        assertFalse(allowed)
        io.mockk.verify(exactly = 1) { mockBeforeCallback.Continue("", false) }
    }
}
