package dev.daviante.kromium.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import dev.daviante.kromium.domain.model.KromiumState
import dev.daviante.kromium.presentation.browser.Kromium
import dev.daviante.kromium.presentation.browser.KromiumClient
import dev.daviante.kromium.presentation.handler.KromiumAuthListener
import dev.daviante.kromium.presentation.handler.KromiumDownloadListener
import dev.daviante.kromium.presentation.handler.KromiumJsDialogListener
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
        state.downloadDirectory?.let { effectiveClient.downloadDirectory = it }
        effectiveClient.onBeforeDownloadListener = state.onBeforeDownload
        effectiveClient.jsDialogListener = state.onJsDialog?.let { cb -> KromiumJsDialogListener { dialog -> cb(dialog) } }
        effectiveClient.consoleMessageListener = state.onConsoleMessage
        effectiveClient.authListener = state.onAuthRequired?.let { cb -> KromiumAuthListener { req -> cb(req) } }
        effectiveClient.onPopupListener = state.onPopup
        effectiveClient.onPermissionRequest = state.onPermissionRequest
        effectiveClient.permissionHandler = state.permissionHandler
        effectiveClient.rememberPermissions = state.rememberPermissions
        effectiveClient.enableContextMenus = state.enableContextMenus
        effectiveClient.contextMenuHandler = state.contextMenuHandler
        effectiveClient.loadErrorListener = state.onLoadError
        effectiveClient.shouldOverrideUrlLoading = state.shouldOverrideUrlLoading
        effectiveClient.sslErrorPolicy = state.sslErrorPolicy
        effectiveClient.assetFilter = state.assetFilter
        effectiveClient.hostLock = state.hostLock
        effectiveClient.hostLockSubresources = state.hostLockSubresources
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
            val loadHandler = object : CefLoadHandlerAdapter() {
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
            }

            val displayHandler = object : CefDisplayHandlerAdapter() {
                override fun onAddressChange(cefBrowser: CefBrowser?, frame: CefFrame?, url: String?) {
                    url?.let { state.url = it }
                }

                override fun onTitleChange(cefBrowser: CefBrowser?, title: String?) {
                    title?.let { state.title = it }
                }
            }

            effectiveClient.addLoadHandler(loadHandler)
            effectiveClient.addDisplayHandler(displayHandler)

            onDispose {
                effectiveClient.removeLoadHandler(loadHandler)
                effectiveClient.removeDisplayHandler(displayHandler)
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
                        val browser = effectiveClient.createBrowser(
                            url = state.url,
                            isOffScreenRendered = false
                        )
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
