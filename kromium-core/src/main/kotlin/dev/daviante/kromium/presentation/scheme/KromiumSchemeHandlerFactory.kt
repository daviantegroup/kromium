package dev.daviante.kromium.presentation.scheme

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefSchemeHandlerFactory
import org.cef.handler.CefResourceHandler
import org.cef.network.CefRequest

/**
 * JCEF scheme handler factory that instantiates [KromiumResourceHandler]
 * for requests routed to a [KromiumAssetHandler].
 */
class KromiumSchemeHandlerFactory(
    private val assetHandler: KromiumAssetHandler
) : CefSchemeHandlerFactory {

    override fun create(
        browser: CefBrowser?,
        frame: CefFrame?,
        schemeName: String?,
        request: CefRequest?
    ): CefResourceHandler {
        return KromiumResourceHandler(assetHandler)
    }
}
