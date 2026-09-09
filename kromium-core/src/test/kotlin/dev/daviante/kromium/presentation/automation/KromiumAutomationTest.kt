package dev.daviante.kromium.presentation.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class KromiumAutomationTest {

    @Test
    fun `escapeJsString escapes special characters safely`() {
        assertEquals("hello", KromiumAutomation.escapeJsString("hello"))
        assertEquals("hello\\\'world", KromiumAutomation.escapeJsString("hello'world"))
        assertEquals("hello\\\"world", KromiumAutomation.escapeJsString("hello\"world"))
        assertEquals("line1\\nline2", KromiumAutomation.escapeJsString("line1\nline2"))
        assertEquals("line1\\rline2", KromiumAutomation.escapeJsString("line1\rline2"))
        assertEquals("line1\\tline2", KromiumAutomation.escapeJsString("line1\tline2"))
        assertEquals("path\\\\to\\\\file", KromiumAutomation.escapeJsString("path\\to\\file"))
        assertEquals("div[data-val=\\\'test\\\']", KromiumAutomation.escapeJsString("div[data-val='test']"))
        assertEquals("hello\\`world", KromiumAutomation.escapeJsString("hello`world"))
        assertEquals("line\\u2028split", KromiumAutomation.escapeJsString("line\u2028split"))
    }
}
