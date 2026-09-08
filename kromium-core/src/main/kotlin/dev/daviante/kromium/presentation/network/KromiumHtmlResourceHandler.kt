package dev.daviante.kromium.presentation.network

import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse

class KromiumHtmlResourceHandler(
    private val htmlContent: String,
    private val mimeType: String = "text/html"
) : CefResourceHandlerAdapter() {

    private var offset = 0
    private val bytes = htmlContent.toByteArray(Charsets.UTF_8)

    override fun open(request: CefRequest?, handle_request: BoolRef?, callback: CefCallback?): Boolean {
        handle_request?.set(true)
        callback?.Continue()
        return true
    }

    override fun processRequest(request: CefRequest?, callback: CefCallback?): Boolean {
        callback?.Continue()
        return true
    }

    override fun getResponseHeaders(
        response: CefResponse?,
        responseLength: IntRef?,
        redirectUrl: StringRef?
    ) {
        response?.mimeType = mimeType
        response?.status = 200
        response?.setHeaderByName("Content-Type", "$mimeType; charset=utf-8", true)
        response?.setHeaderByName("Cache-Control", "no-cache, no-store, must-revalidate", true)
        responseLength?.set(bytes.size)
    }

    override fun readResponse(
        dataOut: ByteArray?,
        bytesToRead: Int,
        bytesRead: IntRef?,
        callback: CefCallback?
    ): Boolean {
        if (dataOut == null || bytesRead == null) return false
        val available = bytes.size - offset
        if (available <= 0) {
            bytesRead.set(0)
            return false
        }
        val toRead = kotlin.math.min(available, bytesToRead)
        System.arraycopy(bytes, offset, dataOut, 0, toRead)
        offset += toRead
        bytesRead.set(toRead)
        return true
    }

    override fun cancel() {
        // No-op
    }
}
