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
     * Converts a Kotlin string into a valid, safe JavaScript string literal (including quotes).
     */
    private fun toJsStringLiteral(value: String): String {
        val sb = StringBuilder(value.length + 16)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\u2028' -> sb.append("\\u2028")
                '\u2029' -> sb.append("\\u2029")
                else -> {
                    if (ch < ' ') {
                        sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Wraps a JavaScript expression or script so that its evaluation result (or error)
     * is captured, stringified, and asynchronously sent back through the CEF message router.
     *
     * Supports:
     * - Expressions (`document.title`, `1 + 1`, `document.documentElement.outerHTML`)
     * - Multi-statement blocks (`var a = 1; var b = 2; a + b;`)
     * - Code with top-level `return` statements (`var x = 1; return x * 2;`)
     * - Promises / thenables (automatically awaited)
     * - Objects and JSON structures (automatically serialized via `JSON.stringify`)
     *
     * The queryId is strictly validated to contain only alphanumeric characters and underscores.
     */
    fun wrapExpression(expression: String, queryId: String, routerQueryName: String = "kromiumQuery"): String {
        // Validate queryId contains only safe characters (alphanumeric + underscore)
        require(queryId.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            "queryId must contain only alphanumeric characters and underscores, got: $queryId"
        }

        // Escape backslashes first, then single quotes for safe embedding in JS string literal
        val safeQueryId = queryId.replace("\\", "\\\\").replace("'", "\\'")
        val jsCodeLiteral = toJsStringLiteral(expression)

        return """
            (function() {
                var fn = (typeof window.$routerQueryName === 'function') ? window.$routerQueryName : null;
                if (!fn) return;
                function __send(payload) {
                    try {
                        fn({
                            request: '$safeQueryId:::' + payload,
                            onSuccess: function() {},
                            onFailure: function() {}
                        });
                    } catch(_) {}
                }
                function __format(res) {
                    if (res === undefined || res === null) return '';
                    if (typeof res === 'object') {
                        try { return JSON.stringify(res); } catch(_) { return String(res); }
                    }
                    return String(res);
                }
                try {
                    var __code = $jsCodeLiteral;
                    var __result;
                    try {
                        __result = (0, eval)(__code);
                    } catch(__evalErr) {
                        if (__evalErr instanceof SyntaxError && String(__evalErr).indexOf('return') !== -1) {
                            __result = (new Function(__code))();
                        } else {
                            throw __evalErr;
                        }
                    }
                    if (__result && typeof __result.then === 'function') {
                        Promise.resolve(__result).then(function(__val) {
                            __send(__format(__val));
                        }).catch(function(__err) {
                            __send('ERROR: ' + (__err ? (__err.message || String(__err)) : 'Unknown error'));
                        });
                    } else {
                        __send(__format(__result));
                    }
                } catch(e) {
                    __send('ERROR: ' + (e ? (e.message || String(e)) : 'Unknown error'));
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
