package dev.daviante.kromium.presentation.js

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import io.mockk.*
import org.cef.browser.CefBrowser

class JsEvaluatorTest {

    @Test
    fun testExpressionWrapping() {
        val expression = "document.title"
        val queryId = "test_query_1"
        
        val wrapped = JsEvaluator.wrapExpression(expression, queryId)
        
        assertTrue(wrapped.contains(queryId), "Wrapped expression must contain the query ID")
        assertTrue(wrapped.contains(expression), "Wrapped expression must contain the original expression")
        assertTrue(wrapped.contains("window.kromiumQuery"), "Wrapped expression must use kromiumQuery to report back")
        assertTrue(wrapped.contains("catch"), "Wrapped expression must catch exceptions")
    }

    @Test
    fun testEvaluateJavaScriptSuccess() = runTest {
        val mockBrowser = mockk<CefBrowser>(relaxed = true)
        val handler = KromiumJsHandler()
        val expectedResult = "Test Page Title"
        
        // Mock the executeJavaScript to simulate the JS calling the routing function back
        every { mockBrowser.executeJavaScript(any(), any(), any()) } answers {
            // Simulate the CEF query callback asynchronously
            launch {
                delay(10) // Small delay to simulate execution time
                val queryId = handler.pendingCallbacks.keys().toList().firstOrNull() ?: ""
                val request = "$queryId:::$expectedResult"
                handler.onQuery(mockBrowser, mockk(relaxed = true), 1L, request, false, mockk(relaxed = true))
            }
        }
        
        val result = JsEvaluator.evaluate(mockBrowser, handler, "document.title", timeoutMs = 1000L)
        
        assertEquals(expectedResult, result, "Result should match the mocked response payload")
        verify(exactly = 1) { mockBrowser.executeJavaScript(any(), any(), eq(0)) }
    }

    @Test
    fun testEvaluateJavaScriptTimeout() = runTest {
        val mockBrowser = mockk<CefBrowser>(relaxed = true)
        val handler = KromiumJsHandler()
        
        // Mock executeJavaScript to do NOTHING (simulating a timeout/no callback)
        every { mockBrowser.executeJavaScript(any(), any(), any()) } returns Unit
        
        val result = JsEvaluator.evaluate(mockBrowser, handler, "while(true);", timeoutMs = 50L)
        
        assertNull(result, "Result should be null on timeout")
        assertEquals(0, handler.pendingCount, "Handler should be cleaned up after timeout")
    }
}
