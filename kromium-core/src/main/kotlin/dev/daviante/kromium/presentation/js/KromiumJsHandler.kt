package dev.daviante.kromium.presentation.js

import dev.daviante.kromium.domain.model.*
import dev.daviante.kromium.domain.config.*
import dev.daviante.kromium.domain.exception.*
import dev.daviante.kromium.data.engine.*
import dev.daviante.kromium.data.model.*
import dev.daviante.kromium.presentation.browser.*
import dev.daviante.kromium.presentation.handler.*
import dev.daviante.kromium.presentation.js.*
import dev.daviante.kromium.presentation.network.*
import dev.daviante.kromium.core.logging.*
import dev.daviante.kromium.core.util.*


import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "KromiumJsHandler"

class KromiumJsHandler : CefMessageRouterHandlerAdapter() {

    private val querySequence = AtomicLong(0)
    internal val pendingCallbacks = ConcurrentHashMap<String, (String?) -> Unit>()

    fun registerPending(queryId: String, callback: (String?) -> Unit) {
        pendingCallbacks[queryId] = callback
    }

    fun removePending(queryId: String) {
        pendingCallbacks.remove(queryId)
    }

    fun nextQueryId(): String = "q_${querySequence.incrementAndGet()}"

    /** Returns the count of pending callbacks (for diagnostics / testing). */
    val pendingCount: Int get() = pendingCallbacks.size

    override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: CefQueryCallback?
    ): Boolean {
        if (request == null) return false

        // Format: queryId:::result
        val separatorIndex = request.indexOf(":::")
        if (separatorIndex != -1) {
            val qId = request.substring(0, separatorIndex)
            val payload = request.substring(separatorIndex + 3)
            val cb = pendingCallbacks.remove(qId)
            if (cb != null) {
                cb.invoke(payload)
                callback?.success("")
                return true
            } else {
                KromiumLogger.w(TAG, "Received response for unknown/expired query: $qId")
            }
        }
        return false
    }

    override fun onQueryCanceled(browser: CefBrowser?, frame: CefFrame?, queryId: Long) {
        KromiumLogger.d(TAG, "Query $queryId canceled by CEF")
    }
}
