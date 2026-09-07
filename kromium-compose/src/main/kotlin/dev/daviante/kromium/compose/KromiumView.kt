package dev.daviante.kromium.compose

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


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * First-class Compose Multiplatform composable component for rendering Kromium browser instances.
 */
@Composable
fun KromiumView(
    state: KromiumViewState,
    modifier: Modifier = Modifier,
    client: KromiumClient = remember(state) { Kromium.newClient() }
) {
    // Synchronize state configuration to the client on every successful composition
    SideEffect {
        client.customUserAgent = state.userAgent
        client.requestInterceptor = state.requestInterceptor
        client.downloadListener = state.onDownload?.let { cb -> KromiumDownloadListener { item -> cb(item) } }
        client.jsDialogListener = state.onJsDialog?.let { cb -> KromiumJsDialogListener { dialog -> cb(dialog) } }
        client.consoleMessageListener = state.onConsoleMessage
        client.authListener = state.onAuthRequired?.let { cb -> KromiumAuthListener { req -> cb(req) } }
        client.onPopupListener = state.onPopup
        client.onPermissionRequest = state.onPermissionRequest
        client.enableContextMenus = state.enableContextMenus
        client.loadErrorListener = state.onLoadError
        client.shouldOverrideUrlLoading = state.shouldOverrideUrlLoading
        client.sslErrorPolicy = state.sslErrorPolicy
    }

    // Reactively handle URL loading (if loadUrl was called when browser was not ready)
    LaunchedEffect(state) {
        snapshotFlow { state.consumePendingUrl() }
            .collect { pending ->
                if (pending != null) {
                    state.browser?.loadUrl(pending)
                }
            }
    }

    key(state, client) {
        DisposableEffect(state, client) {
            client.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    cefBrowser: CefBrowser?,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean
                ) {
                    state.isLoading = isLoading
                    state.canGoBack = canGoBack
                    state.canGoForward = canGoForward
                }

                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    state.isLoading = false
                }
            })

            client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                override fun onAddressChange(cefBrowser: CefBrowser?, frame: CefFrame?, url: String?) {
                    url?.let { state.url = it }
                }

                override fun onTitleChange(cefBrowser: CefBrowser?, title: String?) {
                    title?.let { state.title = it }
                }
            })

            onDispose {
                state.browser?.dispose()
                state.browser = null
                client.dispose()
            }
        }

        Box(modifier = modifier) {
            SwingPanel(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    JPanel(BorderLayout()).apply {
                        val browser = client.createBrowser(state.url)
                        state.browser = browser

                        val pending = state.consumePendingUrl()
                        if (pending != null) {
                            browser.loadUrl(pending)
                        }

                        add(browser.uiComponent, BorderLayout.CENTER)
                    }
                },
                update = { panel ->
                    state.browser?.let { b ->
                        if (panel.componentCount == 0 || panel.getComponent(0) != b.uiComponent) {
                            panel.removeAll()
                            panel.add(b.uiComponent, BorderLayout.CENTER)
                            panel.revalidate()
                            panel.repaint()
                        }
                    }
                }
            )
        }
    }
}
