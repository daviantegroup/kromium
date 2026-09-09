package dev.daviante.kromium.presentation.automation

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.util.FutureBridge
import dev.daviante.kromium.presentation.browser.KromiumBrowser
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture

private const val TAG = "KromiumAutomation"

/**
 * High-level browser automation and content extraction controller.
 *
 * Provides smart auto-waiting, React/Angular/Vue-compatible DOM interactions,
 * network idle synchronization, and desktop environment emulation capabilities.
 */
class KromiumAutomation(private val browser: KromiumBrowser) {

    // =========================================================================
    // Auto-Waiting DOM Actions
    // =========================================================================

    /**
     * Waits until an element matching [selector] appears in the DOM and is ready.
     * Uses a native [MutationObserver] with a high-frequency polling fallback.
     *
     * @param selector CSS selector (e.g., "#submit-btn", ".product-card")
     * @param timeoutMs Maximum time to wait in milliseconds (default: 10,000ms)
     * @return `true` if the element was found within the timeout, `false` otherwise
     */
    @JvmOverloads
    suspend fun waitForSelector(selector: String, timeoutMs: Long = 10_000L): Boolean {
        val safeSelector = escapeJsString(selector)
        val script = """
            new Promise((resolve) => {
                var el = document.querySelector('$safeSelector');
                if (el) return resolve(true);

                var observer = new MutationObserver(function() {
                    if (document.querySelector('$safeSelector')) {
                        observer.disconnect();
                        clearInterval(interval);
                        resolve(true);
                    }
                });
                observer.observe(document.documentElement || document.body, {
                    childList: true,
                    subtree: true,
                    attributes: true
                });

                var interval = setInterval(function() {
                    if (document.querySelector('$safeSelector')) {
                        observer.disconnect();
                        clearInterval(interval);
                        resolve(true);
                    }
                }, 50);

                setTimeout(function() {
                    observer.disconnect();
                    clearInterval(interval);
                    resolve(false);
                }, $timeoutMs);
            })
        """.trimIndent()

        val evaluationTimeout = if (timeoutMs > 0) timeoutMs + 2000L else null
        val result = browser.evaluateJavaScript(script, timeoutMs = evaluationTimeout)
        return result?.trim()?.equals("true", ignoreCase = true) == true
    }

    /**
     * Asynchronously waits until an element matching [selector] appears in the DOM.
     * Returns a Java [CompletableFuture] for seamless Java interop.
     */
    @JvmOverloads
    fun waitForSelectorAsync(selector: String, timeoutMs: Long = 10_000L): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { waitForSelector(selector, timeoutMs) }

    /**
     * Waits for an element matching [selector], scrolls it into view, and simulates
     * a full user click sequence (pointerdown, mousedown, pointerup, mouseup, click).
     *
     * @param selector CSS selector
     * @param timeoutMs Maximum time to wait for element to appear
     * @return `true` if clicked successfully, `false` if not found or timed out
     */
    @JvmOverloads
    suspend fun click(selector: String, timeoutMs: Long = 10_000L): Boolean {
        if (!waitForSelector(selector, timeoutMs)) {
            KromiumLogger.w(TAG, "click: timeout waiting for selector '$selector'")
            return false
        }

        val safeSelector = escapeJsString(selector)
        val script = """
            (function() {
                var el = document.querySelector('$safeSelector');
                if (!el) return false;
                if (el.disabled) return false;

                try {
                    el.scrollIntoView({ behavior: 'instant', block: 'center', inline: 'center' });
                } catch (_) {
                    try { el.scrollIntoView(); } catch (__) {}
                }

                function fire(type) {
                    var ev;
                    if (typeof MouseEvent === 'function') {
                        ev = new MouseEvent(type, { bubbles: true, cancelable: true, view: window });
                    } else {
                        ev = document.createEvent('MouseEvents');
                        ev.initEvent(type, true, true);
                    }
                    el.dispatchEvent(ev);
                }

                fire('pointerdown');
                fire('mousedown');
                fire('pointerup');
                fire('mouseup');
                el.click();
                return true;
            })()
        """.trimIndent()

        val result = browser.evaluateJavaScript(script)
        return result?.trim()?.equals("true", ignoreCase = true) == true
    }

    /**
     * Asynchronously clicks an element matching [selector] returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun clickAsync(selector: String, timeoutMs: Long = 10_000L): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { click(selector, timeoutMs) }

    /**
     * Fills an input or textarea with [value].
     *
     * Compatible with React, Angular, Vue, and vanilla HTML forms:
     * - Uses the HTML prototype value setter to properly update synthetic event systems.
     * - Dispatches `input` and `change` bubbling events.
     *
     * @param selector CSS selector
     * @param value Text to enter
     * @param timeoutMs Maximum time to wait for the input to appear
     * @return `true` if input was found and filled, `false` otherwise
     */
    @JvmOverloads
    suspend fun fill(selector: String, value: String, timeoutMs: Long = 10_000L): Boolean {
        if (!waitForSelector(selector, timeoutMs)) {
            KromiumLogger.w(TAG, "fill: timeout waiting for selector '$selector'")
            return false
        }

        val safeSelector = escapeJsString(selector)
        val safeValue = escapeJsString(value)
        val script = """
            (function() {
                var el = document.querySelector('$safeSelector');
                if (!el) return false;

                el.focus();
                var proto = Object.getPrototypeOf(el);
                var descriptor = Object.getOwnPropertyDescriptor(proto, 'value') ||
                                 Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value') ||
                                 Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value');

                if (descriptor && descriptor.set) {
                    descriptor.set.call(el, '$safeValue');
                } else {
                    el.value = '$safeValue';
                }

                function fire(type) {
                    var ev;
                    if (typeof Event === 'function') {
                        ev = new Event(type, { bubbles: true, cancelable: true });
                    } else {
                        ev = document.createEvent('Event');
                        ev.initEvent(type, true, true);
                    }
                    el.dispatchEvent(ev);
                }

                fire('input');
                fire('change');
                return true;
            })()
        """.trimIndent()

        val result = browser.evaluateJavaScript(script)
        return result?.trim()?.equals("true", ignoreCase = true) == true
    }

    /**
     * Asynchronously fills an input or textarea returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun fillAsync(selector: String, value: String, timeoutMs: Long = 10_000L): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { fill(selector, value, timeoutMs) }

    /**
     * Types [text] character-by-character into the selected input element with an optional [delayMs] between strokes.
     * Useful for triggering autocomplete or live search dropdowns.
     *
     * @param selector CSS selector
     * @param text Text to type
     * @param delayMs Delay between each character in milliseconds (default: 20ms)
     * @param timeoutMs Maximum time to wait for the element to appear
     */
    @JvmOverloads
    suspend fun type(selector: String, text: String, delayMs: Long = 20L, timeoutMs: Long = 10_000L): Boolean {
        if (!waitForSelector(selector, timeoutMs)) return false

        val safeSelector = escapeJsString(selector)

        // Clear and focus input once at the beginning
        val clearScript = """
            (function() {
                var el = document.querySelector('$safeSelector');
                if (!el) return false;
                el.focus();
                var proto = Object.getPrototypeOf(el);
                var descriptor = Object.getOwnPropertyDescriptor(proto, 'value') ||
                                 Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value') ||
                                 Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value');
                if (descriptor && descriptor.set) {
                    descriptor.set.call(el, '');
                } else {
                    el.value = '';
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                return true;
            })()
        """.trimIndent()
        browser.evaluateJavaScript(clearScript)

        var accumulated = ""
        for (char in text) {
            accumulated += char
            val safeAccumulated = escapeJsString(accumulated)
            val strokeScript = """
                (function() {
                    var el = document.querySelector('$safeSelector');
                    if (!el) return false;
                    var proto = Object.getPrototypeOf(el);
                    var descriptor = Object.getOwnPropertyDescriptor(proto, 'value') ||
                                     Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value') ||
                                     Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value');
                    if (descriptor && descriptor.set) {
                        descriptor.set.call(el, '$safeAccumulated');
                    } else {
                        el.value = '$safeAccumulated';
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    return true;
                })()
            """.trimIndent()
            browser.evaluateJavaScript(strokeScript)
            if (delayMs > 0) delay(delayMs)
        }

        // Dispatch final change event when typing finishes
        val finalScript = """
            (function() {
                var el = document.querySelector('$safeSelector');
                if (el) el.dispatchEvent(new Event('change', { bubbles: true }));
            })()
        """.trimIndent()
        browser.evaluateJavaScript(finalScript)
        return true
    }

    /**
     * Asynchronously types [text] character-by-character returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun typeAsync(
        selector: String,
        text: String,
        delayMs: Long = 20L,
        timeoutMs: Long = 10_000L
    ): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { type(selector, text, delayMs, timeoutMs) }

    /**
     * Selects an `<option>` within a `<select>` element by its value attribute or visible text.
     *
     * @param selector CSS selector for the `<select>` element
     * @param value Option value or visible text
     * @param timeoutMs Maximum time to wait for the select element to appear
     */
    @JvmOverloads
    suspend fun selectOption(selector: String, value: String, timeoutMs: Long = 10_000L): Boolean {
        if (!waitForSelector(selector, timeoutMs)) return false

        val safeSelector = escapeJsString(selector)
        val safeValue = escapeJsString(value)
        val script = """
            (function() {
                var el = document.querySelector('$safeSelector');
                if (!el || el.tagName.toLowerCase() !== 'select') return false;

                var options = Array.from(el.options);
                var match = options.find(function(o) { return o.value === '$safeValue' || o.text.trim() === '$safeValue'; });
                if (!match) return false;

                el.value = match.value;
                match.selected = true;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
            })()
        """.trimIndent()

        val result = browser.evaluateJavaScript(script)
        return result?.trim()?.equals("true", ignoreCase = true) == true
    }

    /**
     * Asynchronously selects an `<option>` within a `<select>` element returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun selectOptionAsync(selector: String, value: String, timeoutMs: Long = 10_000L): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { selectOption(selector, value, timeoutMs) }

    // =========================================================================
    // Data Extraction & State Inspection
    // =========================================================================

    /**
     * Gets the visible inner text or textContent of an element matching [selector].
     */
    @JvmOverloads
    suspend fun getTextContent(selector: String, timeoutMs: Long = 10_000L): String? {
        if (!waitForSelector(selector, timeoutMs)) return null
        val safeSelector = escapeJsString(selector)
        val script = """
            (function() {
                var el = document.querySelector('$safeSelector');
                return el ? (el.innerText || el.textContent || '').trim() : null;
            })()
        """.trimIndent()
        return browser.evaluateJavaScript(script)
    }

    /**
     * Asynchronously retrieves the inner text of an element returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun getTextContentAsync(selector: String, timeoutMs: Long = 10_000L): CompletableFuture<String?> =
        FutureBridge.toCompletableFuture { getTextContent(selector, timeoutMs) }

    /**
     * Gets the specified attribute value (e.g., "href", "src", "data-id") of an element.
     */
    @JvmOverloads
    suspend fun getAttribute(selector: String, attribute: String, timeoutMs: Long = 10_000L): String? {
        if (!waitForSelector(selector, timeoutMs)) return null
        val safeSelector = escapeJsString(selector)
        val safeAttr = escapeJsString(attribute)
        val script = """
            (function() {
                var el = document.querySelector('$safeSelector');
                return el ? el.getAttribute('$safeAttr') : null;
            })()
        """.trimIndent()
        return browser.evaluateJavaScript(script)
    }

    /**
     * Asynchronously gets an attribute value returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun getAttributeAsync(selector: String, attribute: String, timeoutMs: Long = 10_000L): CompletableFuture<String?> =
        FutureBridge.toCompletableFuture { getAttribute(selector, attribute, timeoutMs) }

    /**
     * Checks whether an element is currently visible on the page (exists, not hidden via CSS, has dimensions).
     */
    suspend fun isVisible(selector: String): Boolean {
        val safeSelector = escapeJsString(selector)
        val script = """
            (function() {
                var el = document.querySelector('$safeSelector');
                if (!el) return false;
                var style = window.getComputedStyle(el);
                if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                var rect = el.getBoundingClientRect();
                return !!(rect.width || rect.height || el.getClientRects().length);
            })()
        """.trimIndent()
        return browser.evaluateJavaScript(script)?.trim()?.equals("true", ignoreCase = true) == true
    }

    /**
     * Asynchronously checks whether an element is visible returning a Java [CompletableFuture].
     */
    fun isVisibleAsync(selector: String): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { isVisible(selector) }

    /**
     * Checks whether a checkbox or radio button element is checked.
     */
    suspend fun isChecked(selector: String): Boolean {
        val safeSelector = escapeJsString(selector)
        val script = """
            (function() {
                var el = document.querySelector('$safeSelector');
                return !!(el && el.checked);
            })()
        """.trimIndent()
        return browser.evaluateJavaScript(script)?.trim()?.equals("true", ignoreCase = true) == true
    }

    /**
     * Asynchronously checks whether a checkbox or radio button is checked returning a Java [CompletableFuture].
     */
    fun isCheckedAsync(selector: String): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { isChecked(selector) }

    /**
     * Returns the count of DOM elements matching [selector].
     */
    suspend fun count(selector: String): Int {
        val safeSelector = escapeJsString(selector)
        val script = "document.querySelectorAll('$safeSelector').length"
        return browser.evaluateJavaScript(script)?.trim()?.toIntOrNull() ?: 0
    }

    /**
     * Asynchronously returns the count of DOM elements matching [selector] returning a Java [CompletableFuture].
     */
    fun countAsync(selector: String): CompletableFuture<Int> =
        FutureBridge.toCompletableFuture { count(selector) }

    // =========================================================================
    // Lifecycle & Network Waiters
    // =========================================================================

    /**
     * Waits until there are zero active in-flight network requests for at least [idleTimeMs].
     * Essential for waiting until single-page applications (SPAs) finish REST/GraphQL hydration.
     *
     * @param idleTimeMs Duration of silence (in ms) required before considering the network idle (default: 500ms)
     * @param maxTimeoutMs Maximum total duration to wait before returning false (default: 15,000ms)
     * @return `true` if network reached idle state, `false` if timed out
     */
    @JvmOverloads
    suspend fun waitForNetworkIdle(idleTimeMs: Long = 500L, maxTimeoutMs: Long = 15_000L): Boolean {
        val startTime = System.currentTimeMillis()
        var idleSince: Long? = null

        while (System.currentTimeMillis() - startTime < maxTimeoutMs) {
            val inFlight = browser.client.inFlightRequestCount
            val now = System.currentTimeMillis()

            if (inFlight == 0) {
                if (idleSince == null) {
                    idleSince = now
                } else if (now - idleSince >= idleTimeMs) {
                    return true // Network has been completely quiet for required duration
                }
            } else {
                idleSince = null // Active network activity detected; reset timer
            }

            delay(50)
        }

        KromiumLogger.w(TAG, "waitForNetworkIdle timed out after ${maxTimeoutMs}ms (inFlight: ${browser.client.inFlightRequestCount})")
        return false
    }

    /**
     * Asynchronously waits for network idle returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun waitForNetworkIdleAsync(idleTimeMs: Long = 500L, maxTimeoutMs: Long = 15_000L): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { waitForNetworkIdle(idleTimeMs, maxTimeoutMs) }

    /**
     * Waits until the browser's current URL matches the specified [pattern].
     *
     * @param pattern URL substring or regex pattern
     * @param isRegex If `true`, treats [pattern] as a regular expression; otherwise performs contains match
     * @param timeoutMs Maximum time to wait in milliseconds (default: 15,000ms)
     * @return `true` if URL matched within timeout, `false` otherwise
     */
    @JvmOverloads
    suspend fun waitForUrl(pattern: String, isRegex: Boolean = false, timeoutMs: Long = 15_000L): Boolean {
        val regex = if (isRegex) Regex(pattern) else null
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val currentUrl = browser.url
            if (currentUrl != null) {
                val matched = if (regex != null) {
                    regex.containsMatchIn(currentUrl)
                } else {
                    currentUrl.contains(pattern, ignoreCase = true)
                }
                if (matched) return true
            }
            delay(50)
        }

        return false
    }

    /**
     * Asynchronously waits for URL match returning a Java [CompletableFuture].
     */
    @JvmOverloads
    fun waitForUrlAsync(pattern: String, isRegex: Boolean = false, timeoutMs: Long = 15_000L): CompletableFuture<Boolean> =
        FutureBridge.toCompletableFuture { waitForUrl(pattern, isRegex, timeoutMs) }

    // =========================================================================
    // Desktop Emulation & Environment Normalization
    // =========================================================================

    /**
     * Normalizes the headless environment and emulates standard desktop browser properties immediately
     * for this page and all future navigations.
     */
    fun emulateDesktopEnvironment() {
        browser.client.emulateDesktopEnvironment = true
        KromiumEmulation.inject(browser.rawBrowser)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    companion object {
        /**
         * Safely escapes strings for embedding into JavaScript string literals.
         */
        fun escapeJsString(value: String): String {
            val sb = StringBuilder(value.length + 16)
            for (ch in value) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '\'' -> sb.append("\\'")
                    '"' -> sb.append("\\\"")
                    '`' -> sb.append("\\`")
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
            return sb.toString()
        }
    }
}
