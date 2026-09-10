package dev.daviante.kromium.presentation.network

import org.cef.callback.CefCallback
import org.cef.callback.CefResourceReadCallback
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefResponse
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KromiumHtmlResourceHandlerTest {

    private fun createDummyResponse(headers: MutableMap<String, String>): CefResponse {
        return object : CefResponse() {
            private var status = 0
            private var mime = ""

            override fun dispose() {}
            override fun isReadOnly(): Boolean = false
            override fun getError(): org.cef.handler.CefLoadHandler.ErrorCode = org.cef.handler.CefLoadHandler.ErrorCode.ERR_NONE
            override fun setError(errorCode: org.cef.handler.CefLoadHandler.ErrorCode?) {}
            override fun getStatus(): Int = status
            override fun setStatus(s: Int) { status = s }
            override fun getStatusText(): String = "OK"
            override fun setStatusText(statusText: String?) {}
            override fun getMimeType(): String = mime
            override fun setMimeType(mimeType: String?) { mime = mimeType ?: "" }
            override fun getHeaderByName(name: String?): String? = headers[name?.lowercase()]
            override fun setHeaderByName(name: String?, value: String?, overwrite: Boolean) {
                if (name != null && value != null) {
                    headers[name.lowercase()] = value
                }
            }
            override fun getHeaderMap(headerMap: MutableMap<String, String>?) {
                headerMap?.putAll(headers)
            }
            override fun setHeaderMap(headerMap: MutableMap<String, String>?) {
                if (headerMap != null) headers.putAll(headerMap)
            }
        }
    }

    private fun createDummyCallback(onContinued: () -> Unit): CefCallback {
        return object : CefCallback {
            override fun Continue() = onContinued()
            override fun cancel() {}
        }
    }

    private fun createDummyReadCallback(): CefResourceReadCallback {
        return object : CefResourceReadCallback {
            override fun Continue(bytesRead: Int) {}
            override fun getBuffer(): ByteArray = ByteArray(0)
        }
    }

    @Test
    fun testOpenAndProcessRequest() {
        val html = "<h1>Test</h1>"
        val handler = KromiumHtmlResourceHandler(html)

        val handleRequest = BoolRef()
        var continued = false
        val callback = createDummyCallback { continued = true }

        val openResult = handler.open(null, handleRequest, callback)
        assertTrue(openResult)
        assertTrue(handleRequest.get())
        assertTrue(continued)

        continued = false
        val processResult = handler.processRequest(null, callback)
        assertTrue(processResult)
        assertTrue(continued)
    }

    @Test
    fun testGetResponseHeaders() {
        val html = "<!DOCTYPE html><html><body>Kromium</body></html>"
        val handler = KromiumHtmlResourceHandler(html)

        val headers = mutableMapOf<String, String>()
        val response = createDummyResponse(headers)
        val lengthRef = IntRef()
        val redirectRef = StringRef()

        handler.getResponseHeaders(response, lengthRef, redirectRef)

        assertEquals(200, response.status)
        assertEquals("text/html", response.mimeType)
        assertEquals("text/html; charset=utf-8", headers["content-type"])
        assertEquals("no-cache, no-store, must-revalidate", headers["cache-control"])
        assertEquals(html.toByteArray(Charsets.UTF_8).size, lengthRef.get())
    }

    @Test
    fun testReadResponseLegacyStreaming() {
        val html = "<html><body>" + "A".repeat(100) + "</body></html>"
        val expectedBytes = html.toByteArray(Charsets.UTF_8)
        val handler = KromiumHtmlResourceHandler(html)

        val buffer = ByteArray(16)
        val bytesRead = IntRef()
        val callback = createDummyCallback {}
        val accumulated = mutableListOf<Byte>()

        while (true) {
            val hasMore = handler.readResponse(buffer, buffer.size, bytesRead, callback)
            val count = bytesRead.get()
            if (count > 0) {
                for (i in 0 until count) {
                    accumulated.add(buffer[i])
                }
            }
            if (!hasMore || count == 0) break
        }

        assertEquals(expectedBytes.size, accumulated.size)
        assertEquals(html, String(accumulated.toByteArray(), StandardCharsets.UTF_8))
    }

    @Test
    fun testReadModernCef150Streaming() {
        val html = "<html><body>" + "B".repeat(250) + "</body></html>"
        val expectedBytes = html.toByteArray(Charsets.UTF_8)
        val handler = KromiumHtmlResourceHandler(html)

        val buffer = ByteArray(32)
        val bytesRead = IntRef()
        val readCallback = createDummyReadCallback()
        val accumulated = mutableListOf<Byte>()

        while (true) {
            val hasMore = handler.read(buffer, buffer.size, bytesRead, readCallback)
            val count = bytesRead.get()
            if (count > 0) {
                for (i in 0 until count) {
                    accumulated.add(buffer[i])
                }
            }
            if (!hasMore || count == 0) break
        }

        assertEquals(expectedBytes.size, accumulated.size)
        assertEquals(html, String(accumulated.toByteArray(), StandardCharsets.UTF_8))
    }

    @Test
    fun testBufferLargerThanPayload() {
        val html = "<p>Short</p>"
        val handler = KromiumHtmlResourceHandler(html)

        val buffer = ByteArray(1024)
        val bytesRead = IntRef()
        val readCallback = createDummyReadCallback()

        val firstRead = handler.read(buffer, buffer.size, bytesRead, readCallback)
        assertTrue(firstRead)
        assertEquals(html.toByteArray(Charsets.UTF_8).size, bytesRead.get())

        val secondRead = handler.read(buffer, buffer.size, bytesRead, readCallback)
        assertFalse(secondRead)
        assertEquals(0, bytesRead.get())
    }

    @Test
    fun testEmptyHtml() {
        val handler = KromiumHtmlResourceHandler("")
        val buffer = ByteArray(64)
        val bytesRead = IntRef()
        val readCallback = createDummyReadCallback()

        val result = handler.read(buffer, buffer.size, bytesRead, readCallback)
        assertFalse(result)
        assertEquals(0, bytesRead.get())
    }

    @Test
    fun testNullBufferSafety() {
        val handler = KromiumHtmlResourceHandler("<h1>Hello</h1>")
        val bytesRead = IntRef()
        assertFalse(handler.read(null, 10, bytesRead, null))
        assertFalse(handler.read(ByteArray(10), 10, null, null))
        assertFalse(handler.readResponse(null, 10, bytesRead, null))
        assertFalse(handler.readResponse(ByteArray(10), 10, null, null))
    }
}
