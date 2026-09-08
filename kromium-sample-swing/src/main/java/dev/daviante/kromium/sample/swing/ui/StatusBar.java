package dev.daviante.kromium.sample.swing.ui;

import dev.daviante.kromium.data.engine.KromiumEngine;
import dev.daviante.kromium.presentation.browser.Kromium;

import javax.swing.*;
import java.awt.*;

/**
 * Bottom status bar providing engine status, zoom level controls, and proxy status.
 */
public class StatusBar extends JPanel {

    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel zoomLabel = new JLabel("100%");
    private final JButton zoomOutBtn = new JButton("-");
    private final JButton zoomInBtn = new JButton("+");
    private final JButton zoomResetBtn = new JButton("Reset");
    private final JLabel engineLabel = new JLabel();
    private final JLabel proxyLabel = new JLabel();

    private BrowserTab activeTab;

    public StatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        // Left Status
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        add(statusLabel, BorderLayout.WEST);

        // Right Controls
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        // Proxy
        proxyLabel.setText("Proxy: " + Kromium.getActiveProxy().getClass().getSimpleName());
        proxyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        rightPanel.add(proxyLabel);
        rightPanel.add(new JSeparator(SwingConstants.VERTICAL));

        // Zoom Controls
        zoomOutBtn.setToolTipText("Zoom Out");
        zoomInBtn.setToolTipText("Zoom In");
        zoomResetBtn.setToolTipText("Reset Zoom");

        zoomOutBtn.addActionListener(e -> {
            if (activeTab != null) {
                activeTab.zoomOut();
                updateZoom();
            }
        });

        zoomInBtn.addActionListener(e -> {
            if (activeTab != null) {
                activeTab.zoomIn();
                updateZoom();
            }
        });

        zoomResetBtn.addActionListener(e -> {
            if (activeTab != null) {
                activeTab.resetZoom();
                updateZoom();
            }
        });

        rightPanel.add(new JLabel("Zoom:"));
        rightPanel.add(zoomOutBtn);
        rightPanel.add(zoomLabel);
        rightPanel.add(zoomInBtn);
        rightPanel.add(zoomResetBtn);
        rightPanel.add(new JSeparator(SwingConstants.VERTICAL));

        // Engine info
        try {
            engineLabel.setText("CEF v" + KromiumEngine.getInfo().getJcefVersion());
        } catch (Throwable t) {
            engineLabel.setText("CEF 150");
        }
        engineLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        rightPanel.add(engineLabel);

        add(rightPanel, BorderLayout.EAST);
    }

    public void updateState(BrowserTab tab) {
        this.activeTab = tab;
        if (tab == null) {
            statusLabel.setText("No active tab");
            zoomLabel.setText("100%");
            return;
        }

        if (tab.isLoading()) {
            statusLabel.setText("Loading: " + (tab.getCurrentUrl() != null ? tab.getCurrentUrl() : ""));
        } else {
            statusLabel.setText(tab.getTitle() != null && !tab.getTitle().isBlank() ? tab.getTitle() : "Done");
        }

        updateZoom();
    }

    private void updateZoom() {
        if (activeTab == null) return;
        double level = activeTab.getZoomLevel();
        int percent = (int) Math.round(100.0 * Math.pow(1.2, level));
        zoomLabel.setText(percent + "%");
    }
}
