package dev.daviante.kromium.sample.swing.ui;

import dev.daviante.kromium.presentation.browser.KromiumBrowser;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Encapsulates a single browser tab containing its KromiumBrowser instance,
 * UI component, navigation state, and event callbacks.
 */
public class BrowserTab extends JPanel {

    private final KromiumBrowser browser;
    private String currentUrl;
    private String title = "New Tab";
    private boolean loading = false;
    private boolean canGoBack = false;
    private boolean canGoForward = false;
    private double zoomLevel = 0.0;

    private Consumer<BrowserTab> onStateChanged;

    public BrowserTab(KromiumBrowser browser, String initialUrl) {
        super(new BorderLayout());
        this.browser = browser;
        this.currentUrl = initialUrl;

        // Add the CEF rendering surface
        add(browser.getUiComponent(), BorderLayout.CENTER);

        // Register Java event listeners
        browser.onAddressChanged(newUrl -> SwingUtilities.invokeLater(() -> {
            this.currentUrl = newUrl;
            notifyStateChanged();
        }));

        browser.onTitleChanged(newTitle -> SwingUtilities.invokeLater(() -> {
            if (newTitle != null && !newTitle.isBlank()) {
                this.title = newTitle;
                notifyStateChanged();
            }
        }));

        browser.onLoadingChanged((isLoading, back, forward) -> SwingUtilities.invokeLater(() -> {
            this.loading = isLoading;
            this.canGoBack = back;
            this.canGoForward = forward;
            notifyStateChanged();
        }));
    }

    public void setOnStateChanged(Consumer<BrowserTab> listener) {
        this.onStateChanged = listener;
    }

    private void notifyStateChanged() {
        if (onStateChanged != null) {
            onStateChanged.accept(this);
        }
    }

    public KromiumBrowser getBrowser() { return browser; }
    public String getCurrentUrl() { return currentUrl; }
    public String getTitle() { return title; }
    public boolean isLoading() { return loading; }
    public boolean canGoBack() { return canGoBack; }
    public boolean canGoForward() { return canGoForward; }

    public void navigate(String target) {
        String url = target.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://") && !url.startsWith("data:")) {
            if (url.contains(".") && !url.contains(" ")) {
                url = "https://" + url;
            } else {
                url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        browser.loadUrl(url);
    }

    public void reload() {
        if (loading) {
            browser.stopLoad();
        } else {
            browser.reload();
        }
    }

    public void goBack() {
        if (browser.canGoBack()) {
            browser.goBack();
        }
    }

    public void goForward() {
        if (browser.canGoForward()) {
            browser.goForward();
        }
    }

    public void openDevTools() {
        browser.openDevTools();
    }

    public void zoomIn() {
        zoomLevel += 0.5;
        browser.setZoomLevel(zoomLevel);
    }

    public void zoomOut() {
        zoomLevel = Math.max(-5.0, zoomLevel - 0.5);
        browser.setZoomLevel(zoomLevel);
    }

    public void resetZoom() {
        zoomLevel = 0.0;
        browser.setZoomLevel(0.0);
    }

    public double getZoomLevel() {
        return zoomLevel;
    }

    public CompletableFuture<String> evaluateJsAsync(String script) {
        return browser.evaluateJavaScriptAsync(script);
    }

    public void dispose() {
        removeAll();
        browser.dispose();
    }
}
