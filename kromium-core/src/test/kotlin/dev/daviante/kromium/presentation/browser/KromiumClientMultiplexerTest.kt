package dev.daviante.kromium.presentation.browser

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandler
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KromiumClientMultiplexerTest {

    @Test
    fun testLoadHandlerMultiplexing() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val loadHandlerSlot = slot<CefLoadHandler>()

        val client = KromiumClient(mockRawClient)

        // Verify that rawClient was only registered once with the master composite handler
        verify(exactly = 1) { mockRawClient.addLoadHandler(capture(loadHandlerSlot)) }
        val composite = loadHandlerSlot.captured

        // Register two distinct handlers on KromiumClient
        val events1 = mutableListOf<String>()
        val events2 = mutableListOf<String>()

        val handler1 = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                events1.add("h1:end")
            }
        }
        val handler2 = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                events2.add("h2:end")
            }
        }

        client.addLoadHandler(handler1)
        client.addLoadHandler(handler2)

        // Trigger event via the composite handler installed on rawClient
        composite.onLoadEnd(null, null, 200)

        // Both handlers must receive the event!
        assertEquals(listOf("h1:end"), events1, "Handler 1 must receive onLoadEnd")
        assertEquals(listOf("h2:end"), events2, "Handler 2 must receive onLoadEnd")

        // Unregister handler 1
        client.removeLoadHandler(handler1)
        composite.onLoadEnd(null, null, 200)

        assertEquals(listOf("h1:end"), events1, "Handler 1 should not receive events after removal")
        assertEquals(listOf("h2:end", "h2:end"), events2, "Handler 2 should continue receiving events")

        // Clear all handlers
        client.removeLoadHandler()
        composite.onLoadEnd(null, null, 200)
        assertEquals(listOf("h2:end", "h2:end"), events2, "Handler 2 should not receive events after clear")
    }

    @Test
    fun testDisplayHandlerMultiplexing() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val displayHandlerSlot = slot<CefDisplayHandler>()

        val client = KromiumClient(mockRawClient)

        verify(exactly = 1) { mockRawClient.addDisplayHandler(capture(displayHandlerSlot)) }
        val composite = displayHandlerSlot.captured

        val titles1 = mutableListOf<String>()
        val titles2 = mutableListOf<String>()

        val handler1 = object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(browser: CefBrowser?, title: String?) {
                title?.let(titles1::add)
            }
        }
        val handler2 = object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(browser: CefBrowser?, title: String?) {
                title?.let(titles2::add)
            }
        }

        client.addDisplayHandler(handler1)
        client.addDisplayHandler(handler2)

        composite.onTitleChange(null, "Page Title")

        assertEquals(listOf("Page Title"), titles1)
        assertEquals(listOf("Page Title"), titles2)

        client.removeDisplayHandler(handler1)
        composite.onTitleChange(null, "New Title")

        assertEquals(listOf("Page Title"), titles1)
        assertEquals(listOf("Page Title", "New Title"), titles2)
    }

    @Test
    fun testRawClientHandlerRegistrationsAreNotDuplicated() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val client = KromiumClient(mockRawClient)

        // Multiple calls to client.addLoadHandler should never invoke rawClient.addLoadHandler again
        client.addLoadHandler(object : CefLoadHandlerAdapter() {})
        client.addLoadHandler(object : CefLoadHandlerAdapter() {})
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {})
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {})

        verify(exactly = 1) { mockRawClient.addLoadHandler(any()) }
        verify(exactly = 1) { mockRawClient.addDisplayHandler(any()) }
        verify(exactly = 1) { mockRawClient.addLifeSpanHandler(any()) }
        verify(exactly = 1) { mockRawClient.addContextMenuHandler(any()) }
        verify(exactly = 1) { mockRawClient.addFocusHandler(any()) }
        verify(exactly = 1) { mockRawClient.addKeyboardHandler(any()) }
    }

    @Test
    fun testRegisterHtmlPayloadCapAndEviction() {
        val mockRawClient = mockk<CefClient>(relaxed = true)
        val client = KromiumClient(mockRawClient)

        // Register 3 payloads with a max capacity of 2
        client.registerHtmlPayload("http://local/1", "<h1>1</h1>", maxCapacity = 2)
        client.registerHtmlPayload("http://local/2", "<h1>2</h1>", maxCapacity = 2)
        assertEquals(2, client.htmlPayloads.size)

        client.registerHtmlPayload("http://local/3", "<h1>3</h1>", maxCapacity = 2)
        assertEquals(2, client.htmlPayloads.size)
        // Oldest (1) must be evicted!
        assertEquals(null, client.htmlPayloads["http://local/1"])
        assertEquals("<h1>2</h1>", client.htmlPayloads["http://local/2"])
        assertEquals("<h1>3</h1>", client.htmlPayloads["http://local/3"])

        // Re-registering existing key should update and not cause desynchronized eviction
        client.registerHtmlPayload("http://local/2", "<h1>2-updated</h1>", maxCapacity = 2)
        assertEquals(2, client.htmlPayloads.size)
        assertEquals("<h1>2-updated</h1>", client.htmlPayloads["http://local/2"])
    }
}
