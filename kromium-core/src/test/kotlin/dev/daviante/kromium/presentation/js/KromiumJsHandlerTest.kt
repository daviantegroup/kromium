package dev.daviante.kromium.presentation.js

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import io.mockk.*
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback

class KromiumJsHandlerTest {

    @Test
    fun testHandleQuerySuccess() {
        val handler = KromiumJsHandler()
        val queryId = "query_123"
        val expectedResult = "some result"
        
        var resumeCalled = false
        var resumedValue: String? = null
        
        handler.registerPending(queryId) { result ->
            resumeCalled = true
            resumedValue = result
        }
        
        val mockCallback = mockk<CefQueryCallback>(relaxed = true)
        val mockBrowser = mockk<CefBrowser>(relaxed = true)
        val mockFrame = mockk<CefFrame>(relaxed = true)
        
        val requestString = "$queryId:::$expectedResult"
        val handled = handler.onQuery(mockBrowser, mockFrame, 1L, requestString, false, mockCallback)
        
        assertTrue(handled, "Handler should consume the query")
        assertTrue(resumeCalled, "Pending callback should be triggered")
        assertEquals(expectedResult, resumedValue, "Resumed value should match payload")
        verify { mockCallback.success(eq("")) }
    }

    @Test
    fun testHandleQueryFailure() {
        val handler = KromiumJsHandler()
        val queryId = "query_456"
        val errorMessage = "ERROR: foo is not defined"
        
        var resumeCalled = false
        var resumedValue: String? = null
        
        handler.registerPending(queryId) { result ->
            resumeCalled = true
            resumedValue = result
        }
        
        val mockCallback = mockk<CefQueryCallback>(relaxed = true)
        val requestString = "$queryId:::$errorMessage"
        val handled = handler.onQuery(mockk(), mockk(), 1L, requestString, false, mockCallback)
        
        assertTrue(handled)
        assertTrue(resumeCalled)
        assertEquals(errorMessage, resumedValue) // We just return the error string in the current logic
        verify { mockCallback.success(eq("")) }
    }

    @Test
    fun testUnrecognizedQuery() {
        val handler = KromiumJsHandler()
        val handled = handler.onQuery(mockk(), mockk(), 1L, "some_random_string", false, mockk())
        assertFalse(handled, "Should return false for unrecognized formats")
    }
}
