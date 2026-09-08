package dev.daviante.kromium.sample.swing.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Top navigation toolbar containing history buttons, URL omnibox,
 * page controls, zoom, DevTools, and Download manager launcher.
 */
public class NavigationToolbar extends JToolBar {

    private final JButton backButton = new JButton("◀");
    private final JButton forwardButton = new JButton("▶");
    private final JButton reloadButton = new JButton("↻");
    private final JTextField urlField = new JTextField();
    private final JButton goButton = new JButton("Go");
    private final JButton devToolsButton = new JButton("Inspect");
    private final JButton jsButton = new JButton("JS");
    private final JButton downloadsButton = new JButton("Downloads (0)");

    private BrowserTab activeTab;
    private final Runnable onOpenDownloads;

    public NavigationToolbar(Runnable onOpenDownloads) {
        this.onOpenDownloads = onOpenDownloads;
        setFloatable(false);
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        // Styling
        backButton.setToolTipText("Back");
        forwardButton.setToolTipText("Forward");
        reloadButton.setToolTipText("Reload / Stop");
        devToolsButton.setToolTipText("Open Chromium Developer Tools");
        jsButton.setToolTipText("Execute JavaScript in page");
        downloadsButton.setToolTipText("Open Download Manager");

        urlField.putClientProperty("JTextField.placeholderText", "Enter URL or search terms...");
        urlField.setPreferredSize(new Dimension(400, 32));

        // History Actions
        backButton.addActionListener(e -> {
            if (activeTab != null) activeTab.goBack();
        });

        forwardButton.addActionListener(e -> {
            if (activeTab != null) activeTab.goForward();
        });

        reloadButton.addActionListener(e -> {
            if (activeTab != null) activeTab.reload();
        });

        // Navigation Actions
        goButton.addActionListener(e -> navigateFromField());
        urlField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateFromField();
                }
            }
        });

        // DevTools
        devToolsButton.addActionListener(e -> {
            if (activeTab != null) activeTab.openDevTools();
        });

        // JS Evaluator Dialog
        jsButton.addActionListener(e -> showJsEvaluationDialog());

        // Downloads
        downloadsButton.addActionListener(e -> {
            if (onOpenDownloads != null) onOpenDownloads.run();
        });

        // Layout Components
        add(backButton);
        add(Box.createHorizontalStrut(4));
        add(forwardButton);
        add(Box.createHorizontalStrut(4));
        add(reloadButton);
        add(Box.createHorizontalStrut(8));
        add(urlField);
        add(Box.createHorizontalStrut(4));
        add(goButton);
        add(Box.createHorizontalStrut(8));
        add(devToolsButton);
        add(Box.createHorizontalStrut(4));
        add(jsButton);
        add(Box.createHorizontalStrut(4));
        add(downloadsButton);

        updateState(null);
    }

    public void updateState(BrowserTab tab) {
        this.activeTab = tab;
        if (tab == null) {
            backButton.setEnabled(false);
            forwardButton.setEnabled(false);
            reloadButton.setEnabled(false);
            urlField.setEnabled(false);
            goButton.setEnabled(false);
            devToolsButton.setEnabled(false);
            jsButton.setEnabled(false);
            return;
        }

        backButton.setEnabled(tab.canGoBack());
        forwardButton.setEnabled(tab.canGoForward());
        reloadButton.setEnabled(true);
        reloadButton.setText(tab.isLoading() ? "✕" : "↻");
        reloadButton.setToolTipText(tab.isLoading() ? "Stop loading" : "Reload page");

        urlField.setEnabled(true);
        if (!urlField.hasFocus()) {
            urlField.setText(tab.getCurrentUrl() != null ? tab.getCurrentUrl() : "");
        }

        goButton.setEnabled(true);
        devToolsButton.setEnabled(true);
        jsButton.setEnabled(true);
    }

    public void updateDownloadCount(int activeCount) {
        downloadsButton.setText("Downloads (" + activeCount + ")");
    }

    private void navigateFromField() {
        String text = urlField.getText().trim();
        if (!text.isEmpty() && activeTab != null) {
            activeTab.navigate(text);
        }
    }

    private void showJsEvaluationDialog() {
        if (activeTab == null) return;

        String script = JOptionPane.showInputDialog(
                this,
                "Enter JavaScript expression to execute:",
                "JavaScript REPL",
                JOptionPane.PLAIN_MESSAGE
        );

        if (script != null && !script.isBlank()) {
            activeTab.evaluateJsAsync(script).whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                if (error != null) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Error: " + error.getMessage(),
                            "JavaScript Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                } else {
                    JOptionPane.showMessageDialog(
                            this,
                            result != null ? result : "undefined / null",
                            "JavaScript Result",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            }));
        }
    }
}
