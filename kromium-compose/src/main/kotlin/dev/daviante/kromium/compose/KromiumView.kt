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
import androidx.compose.foundation.layout.BoxScope
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
    client: KromiumClient? = null,
    loadingContent: @Composable (BoxScope.() -> Unit)? = null
) {
    val engineState by Kromium.state.collectAsState()
    val effectiveClient = remember(state, client, engineState is KromiumState.Ready) {
        client ?: if (Kromium.isReady) {
            try { Kromium.newClient() } catch (_: Exception) { null }
        } else null
    }

    if (effectiveClient == null) {
        Box(modifier = modifier) {
            loadingContent?.invoke(this)
        }
        return
    }

    // Synchronize state configuration to the client on every successful composition
    SideEffect {
        effectiveClient.customUserAgent = state.userAgent
        effectiveClient.requestInterceptor = state.requestInterceptor
        effectiveClient.downloadListener = state.onDownload?.let { cb -> KromiumDownloadListener { item -> cb(item) } }
        effectiveClient.jsDialogListener = state.onJsDialog?.let { cb -> KromiumJsDialogListener { dialog -> cb(dialog) } }
        effectiveClient.consoleMessageListener = state.onConsoleMessage
        effectiveClient.authListener = state.onAuthRequired?.let { cb -> KromiumAuthListener { req -> cb(req) } }
        effectiveClient.onPopupListener = state.onPopup
        effectiveClient.onPermissionRequest = state.onPermissionRequest
        effectiveClient.enableContextMenus = state.enableContextMenus
        effectiveClient.loadErrorListener = state.onLoadError
        effectiveClient.shouldOverrideUrlLoading = state.shouldOverrideUrlLoading
        effectiveClient.sslErrorPolicy = state.sslErrorPolicy
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

    key(state, effectiveClient) {
        DisposableEffect(state, effectiveClient) {
            effectiveClient.addLoadHandler(object : CefLoadHandlerAdapter() {
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

            effectiveClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
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
                if (client == null) {
                    effectiveClient.dispose()
                }
            }
        }

        Box(modifier = modifier) {
            SwingPanel(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    JPanel(BorderLayout()).apply {
                        val browser = effectiveClient.createBrowser(state.url)
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
