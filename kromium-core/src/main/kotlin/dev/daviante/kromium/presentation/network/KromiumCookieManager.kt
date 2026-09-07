package dev.daviante.kromium.presentation.network

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
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.net.URI
import java.util.Date
import kotlin.coroutines.resume

private const val TAG = "KromiumCookieManager"

/**
 * Modern Coroutine-based cookie manager wrapping Chromium's native cookie store.
 */
object KromiumCookieManager {

    /** Timeout for cookie retrieval operations in milliseconds. */
    var timeoutMs: Long = 5_000L

    private val rawManager: CefCookieManager
        get() = CefCookieManager.getGlobalManager()

    /**
     * Retrieves all cookies for the specified [url] as a key-value map.
     *
     * If the URL has no cookies, or if the operation times out, returns an empty map.
     * The timeout prevents permanent coroutine suspension when the cookie visitor
     * is never invoked (e.g., zero cookies for the URL).
     */
    suspend fun getCookies(url: String, includeHttpOnly: Boolean = true): Map<String, String> {
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

                val success = manager.visitUrlCookies(url, includeHttpOnly, object : CefCookieVisitor {
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
     * Retrieves the value of a specific cookie by [name] for [url].
     */
    suspend fun getCookie(url: String, name: String): String? {
        return getCookies(url)[name]
    }

    /**
     * Sets a cookie for the specified [url].
     */
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
    fun flush(): Boolean {
        return try {
            rawManager.flushStore(null)
        } catch (e: Throwable) {
            KromiumLogger.w(TAG, "Failed to flush cookie store", e)
            false
        }
    }
}
