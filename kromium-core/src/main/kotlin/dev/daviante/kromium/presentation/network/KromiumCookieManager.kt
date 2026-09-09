package dev.daviante.kromium.presentation.network

import dev.daviante.kromium.core.logging.KromiumLogger
import dev.daviante.kromium.core.util.FutureBridge

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.net.URI
import java.util.Date
import kotlin.coroutines.resume
import java.util.concurrent.CompletableFuture

private const val TAG = "KromiumCookieManager"

/**
 * Modern Coroutine-based cookie manager wrapping Chromium's native cookie store.
 */
object KromiumCookieManager {

    /** Timeout for cookie retrieval operations in milliseconds. */
    var timeoutMs: Long = 2_000L

    private val rawManager: CefCookieManager
        get() = CefCookieManager.getGlobalManager()

    /**
     * Retrieves all cookies for the specified [url] as a key-value map.
     *
     * If the URL has no cookies, or if the operation times out, returns an empty map.
     * The timeout prevents permanent coroutine suspension when the cookie visitor
     * is never invoked (e.g., zero cookies for the URL).
     */
    @JvmStatic
    @JvmOverloads
    suspend fun getCookies(url: String, includeHttpOnly: Boolean = true): Map<String, String> {
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed.equals("about:blank", ignoreCase = true) || !trimmed.startsWith("http", ignoreCase = true)) {
            return emptyMap()
        }

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val cookies = mutableMapOf<String, String>()
                val manager = try {
                    rawManager
                } catch (e: Throwable) {
                    KromiumLogger.w(TAG, "Could not access global cookie manager", e)
                    null
                }

                if (manager == null) {
                    continuation.resume(emptyMap())
                    return@suspendCancellableCoroutine
                }

                val success = try {
                    manager.visitUrlCookies(url, includeHttpOnly, object : CefCookieVisitor {
                        override fun visit(
                            cookie: CefCookie?,
                            count: Int,
                            total: Int,
                            deleteCookie: BoolRef?
                        ): Boolean {
                            if (cookie != null && cookie.name.isNotBlank()) {
                                cookies[cookie.name] = cookie.value
                            }
                            if (count >= total - 1) {
                                if (continuation.isActive) {
                                    continuation.resume(cookies)
                                }
                            }
                            return true
                        }
                    })
                } catch (e: Throwable) {
                    KromiumLogger.w(TAG, "Failed to visit URL cookies for $url", e)
                    false
                }

                if (!success && continuation.isActive) {
                    // visitUrlCookies returned false — manager rejected the request
                    continuation.resume(emptyMap())
                }
            }
        }

        if (result == null) {
            KromiumLogger.d(TAG, "Cookie retrieval timed out for $url (${timeoutMs}ms), returning empty map")
        }

        return result ?: emptyMap()
    }

    /**
     * Asynchronously retrieves all cookies for [url] returning a Java [CompletableFuture].
     */
    @JvmStatic
    @JvmOverloads
    fun getCookiesAsync(url: String, includeHttpOnly: Boolean = true): CompletableFuture<Map<String, String>> =
        FutureBridge.toCompletableFuture { getCookies(url, includeHttpOnly) }

    /**
     * Retrieves the value of a specific cookie by [name] for [url].
     */
    @JvmStatic
    suspend fun getCookie(url: String, name: String): String? {
        return getCookies(url)[name]
    }

    /**
     * Asynchronously retrieves a specific cookie by [name] for [url] returning a Java [CompletableFuture].
     */
    @JvmStatic
    fun getCookieAsync(url: String, name: String): CompletableFuture<String?> =
        FutureBridge.toCompletableFuture { getCookie(url, name) }

    /**
     * Retrieves all cookies across all domains stored in Chromium's global cookie store.
     * Useful for privacy audits, session inspection, and GDPR compliance validation.
     */
    @JvmStatic
    suspend fun getAllCookies(): List<CefCookie> {
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val cookies = mutableListOf<CefCookie>()
                val manager = try {
                    rawManager
                } catch (e: Throwable) {
                    KromiumLogger.w(TAG, "Could not access global cookie manager", e)
                    null
                }

                if (manager == null) {
                    continuation.resume(emptyList())
                    return@suspendCancellableCoroutine
                }

                val success = try {
                    manager.visitAllCookies(object : CefCookieVisitor {
                        override fun visit(
                            cookie: CefCookie?,
                            count: Int,
                            total: Int,
                            deleteCookie: BoolRef?
                        ): Boolean {
                            if (cookie != null) {
                                cookies.add(cookie)
                            }
                            if (count >= total - 1) {
                                if (continuation.isActive) {
                                    continuation.resume(cookies)
                                }
                            }
                            return true
                        }
                    })
                } catch (e: Throwable) {
                    KromiumLogger.w(TAG, "Failed to visit all cookies", e)
                    false
                }

                if (!success && continuation.isActive) {
                    continuation.resume(emptyList())
                }
            }
        }

        if (result == null) {
            KromiumLogger.d(TAG, "All-cookies retrieval timed out (${timeoutMs}ms), returning empty list")
        }

        return result ?: emptyList()
    }

    /**
     * Asynchronously retrieves all cookies across all domains stored in the global cookie store,
     * returning a Java [CompletableFuture].
     */
    @JvmStatic
    fun getAllCookiesAsync(): CompletableFuture<List<CefCookie>> =
        FutureBridge.toCompletableFuture { getAllCookies() }

    /**
     * Sets a cookie for the specified [url].
     */
    @JvmStatic
    @JvmOverloads
    fun setCookie(
        url: String,
        name: String,
        value: String,
        domain: String? = null,
        path: String = "/",
        isSecure: Boolean = false,
        isHttpOnly: Boolean = false,
        expires: Date? = null
    ): Boolean {
        val resolvedDomain = domain ?: try {
            URI(url).host
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Could not parse domain from URL: $url", e)
            null
        }

        val cookie = CefCookie(
            name,
            value,
            resolvedDomain,
            path,
            isSecure,
            isHttpOnly,
            null,
            null,
            expires != null,
            expires
        )
        return try {
            rawManager.setCookie(url, cookie)
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Failed to set cookie '$name' for $url", e)
            false
        }
    }

    /**
     * Deletes a specific cookie for the specified [url].
     */
    @JvmStatic
    fun deleteCookie(url: String, name: String): Boolean {
        return try {
            rawManager.deleteCookies(url, name)
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Failed to delete cookie '$name' for $url", e)
            false
        }
    }

    /**
     * Deletes all cookies from Chromium's cookie store.
     */
    @JvmStatic
    fun clearCookies(): Boolean {
        return try {
            rawManager.deleteCookies("", "")
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Failed to clear all cookies", e)
            false
        }
    }

    /**
     * Flushes the cookie store to disk to ensure session persistence.
     */
    @JvmStatic
    fun flush(): Boolean {
        return try {
            rawManager.flushStore(null)
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Failed to flush cookie store", e)
            false
        }
    }
}
