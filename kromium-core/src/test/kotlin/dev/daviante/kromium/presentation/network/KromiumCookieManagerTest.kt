package dev.daviante.kromium.presentation.network

import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KromiumCookieManagerTest {

    @Test
    fun testGetCookiesReturnsFastForNonHttpUrls() = runBlocking {
        // Calling getCookies with about:blank or empty URL should return emptyMap immediately without waiting for timeout
        val elapsed = measureTimeMillis {
            val emptyResult = KromiumCookieManager.getCookies("")
            assertEquals(emptyMap(), emptyResult)

            val blankResult = KromiumCookieManager.getCookies("about:blank")
            assertEquals(emptyMap(), blankResult)

            val dataResult = KromiumCookieManager.getCookies("data:text/html,test")
            assertEquals(emptyMap(), dataResult)
        }

        // Should complete in well under 200ms (timeoutMs is 2000ms)
        assertTrue(elapsed < 500, "Non-http URL cookie query took $elapsed ms, should be instantaneous")
    }
}
