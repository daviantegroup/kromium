package dev.daviante.kromium.presentation.scheme

import org.cef.callback.CefCallback
import org.cef.callback.CefResourceReadCallback
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.InputStream
import java.util.HashMap

/**
 * JCEF resource handler adapter that bridges Chromium internal request streaming
 * to a [KromiumAssetHandler].
 */
class KromiumResourceHandler(
    private val assetHandler: KromiumAssetHandler
) : CefResourceHandlerAdapter() {

    @Volatile
    private var currentResponse: KromiumAssetResponse? = null

    @Volatile
    private var currentStream: InputStream? = null

    override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
        return try {
            val assetReq = KromiumAssetRequest.fromCef(request)
            val res = assetHandler.handle(assetReq)
            if (res == null) {
                callback.cancel()
                false
            } else {
                currentResponse = res
                currentStream = res.openStream()
                callback.Continue()
                true
            }
        } catch (_: Throwable) {
            callback.cancel()
            false
        }
    }

    override fun open(request: CefRequest, handleRequest: BoolRef, callback: CefCallback): Boolean {
        val result = processRequest(request, callback)
        handleRequest.set(result)
        return result
    }

    override fun getResponseHeaders(
        response: CefResponse,
        responseLength: IntRef,
        redirectUrl: StringRef
    ) {
        val res = currentResponse
        if (res == null) {
            response.status = 404
            response.statusText = "Not Found"
            responseLength.set(0)
            return
        }

        response.status = res.statusCode
        response.statusText = res.statusText
        response.mimeType = res.mimeType

        if (res.contentLength != null && res.contentLength >= 0) {
            responseLength.set(res.contentLength.toInt())
        } else {
            responseLength.set(-1)
        }

        if (res.headers.isNotEmpty()) {
            val headerMap = HashMap<String, String>(res.headers)
            response.setHeaderMap(headerMap)
        }
    }

    override fun readResponse(
        dataOut: ByteArray,
        bytesToRead: Int,
        bytesRead: IntRef,
        callback: CefCallback
    ): Boolean {
        val stream = currentStream ?: run {
            bytesRead.set(0)
            return false
        }

        return try {
            val read = stream.read(dataOut, 0, bytesToRead)
            if (read > 0) {
                bytesRead.set(read)
                true
            } else {
                bytesRead.set(0)
                closeStream()
                false
            }
        } catch (_: Throwable) {
            bytesRead.set(0)
            closeStream()
            false
        }
    }

    override fun read(
        dataOut: ByteArray,
        bytesToRead: Int,
        bytesRead: IntRef,
        callback: CefResourceReadCallback
    ): Boolean {
        val stream = currentStream ?: run {
            bytesRead.set(0)
            return false
        }

        return try {
            val read = stream.read(dataOut, 0, bytesToRead)
            if (read > 0) {
                bytesRead.set(read)
                true
            } else {
                bytesRead.set(0)
                closeStream()
                false
            }
        } catch (_: Throwable) {
            bytesRead.set(0)
            closeStream()
            false
        }
    }

    override fun cancel() {
        closeStream()
    }

    private fun closeStream() {
        try {
            currentStream?.close()
        } catch (_: Throwable) {}
        currentStream = null
    }
}
