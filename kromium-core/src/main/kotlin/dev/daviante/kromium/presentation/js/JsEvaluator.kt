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


import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

private const val TAG = "JsEvaluator"



object JsEvaluator {

    /**
     * Default timeout for JavaScript evaluation in milliseconds.
     * Can be overridden per-call via the [timeoutMs] parameter in [evaluate].
     */
    var defaultTimeoutMs: Long = 10_000L

    /**
     * Wraps a JavaScript expression so that its result is sent back through
     * the CEF message router. The queryId is validated to contain only safe characters.
     *
     * **Security note**: The [expression] parameter is injected directly into JavaScript.
     * Never pass untrusted user input as the expression without proper sanitization.
     */
    fun wrapExpression(expression: String, queryId: String, routerQueryName: String = "kromiumQuery"): String {
        // Validate queryId contains only safe characters (alphanumeric + underscore)
        require(queryId.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            "queryId must contain only alphanumeric characters and underscores, got: $queryId"
        }

        // Escape the queryId for safe embedding in a JS string literal
        val safeQueryId = queryId.replace("'", "\\'").replace("\\", "\\\\")

        return """
            (function() {
                try {
                    var __result = (function() { $expression })();
                    var __str = (__result === undefined || __result === null) ? '' : String(__result);
                    window.$routerQueryName({
                        request: '$safeQueryId:::' + __str,
                        onSuccess: function(response) {},
                        onFailure: function(error_code, error_message) {}
                    });
                } catch(e) {
                    window.$routerQueryName({
                        request: '$safeQueryId:::ERROR: ' + e.message,
                        onSuccess: function(response) {},
                        onFailure: function(error_code, error_message) {}
                    });
                }
            })();
        """.trimIndent()
    }

    /**
     * Evaluates a JavaScript expression in the browser and returns the result as a string.
     *
     * Uses [suspendCancellableCoroutine] to properly handle cancellation and prevent leaks.
     * If the evaluation doesn't complete within [timeoutMs], returns `null` and cleans up
     * the pending callback to prevent memory leaks.
     *
     * **Security note**: The [expression] parameter is injected directly into JavaScript.
     * Never pass untrusted user input without proper sanitization.
     *
     * @param browser The CEF browser to evaluate in
     * @param handler The JS message router handler
     * @param expression Raw JavaScript expression to evaluate
     * @param routerQueryName The message router query function name
     * @param timeoutMs Maximum time to wait for the result, or `null` for [defaultTimeoutMs]
     * @return The evaluation result as a string, or `null` if timed out
     * @throws KromiumException.JsEvaluationTimeout if [throwOnTimeout] is `true` and evaluation times out
     */
    suspend fun evaluate(
        browser: CefBrowser,
        handler: KromiumJsHandler,
        expression: String,
        routerQueryName: String = "kromiumQuery",
        timeoutMs: Long? = null,
        throwOnTimeout: Boolean = false
    ): String? {
        val effectiveTimeout = timeoutMs ?: defaultTimeoutMs

        val result = withTimeoutOrNull(effectiveTimeout) {
            suspendCancellableCoroutine { continuation ->
                val queryId = handler.nextQueryId()

                // Register cleanup on cancellation to prevent memory leaks
                continuation.invokeOnCancellation {
                    handler.removePending(queryId)
                    KromiumLogger.d(TAG, "JS evaluation cancelled, cleaned up query: $queryId")
                }

                handler.registerPending(queryId) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                val wrappedScript = wrapExpression(expression, queryId, routerQueryName)
                browser.executeJavaScript(wrappedScript, browser.url ?: "", 0)
            }
        }

        if (result == null && throwOnTimeout) {
            throw KromiumException.JsEvaluationTimeout(effectiveTimeout)
        }

        if (result == null) {
            KromiumLogger.w(TAG, "JS evaluation timed out after ${effectiveTimeout}ms")
        }

        return result
    }
}
