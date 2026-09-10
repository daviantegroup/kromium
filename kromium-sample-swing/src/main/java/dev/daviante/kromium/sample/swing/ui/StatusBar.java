package dev.daviante.kromium.sample.swing.ui;

import dev.daviante.kromium.data.engine.KromiumEngine;
import dev.daviante.kromium.sample.swing.theme.KromiumTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Bottom status bar providing engine status, zoom controls, and CEF version
 * matching the Kromium Compose layout.
 */
public class StatusBar extends JPanel {

    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel zoomLabel = new JLabel("100%");
    private final JButton zoomOutBtn = createTextButton("-", "Zoom Out");
    private final JButton zoomInBtn = createTextButton("+", "Zoom In");
    private final JButton zoomResetBtn = createTextButton("Reset", "Reset Zoom");
    private final JLabel engineLabel = new JLabel();

    private BrowserTab activeTab;

    public StatusBar() {
        super(new BorderLayout());
        setBackground(KromiumTheme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, KromiumTheme.BORDER_SUBTLE),
                new EmptyBorder(3, 12, 3, 12)
        ));
        setPreferredSize(new Dimension(800, 26));

        // Left Status
        statusLabel.setForeground(KromiumTheme.TEXT_SECONDARY);
        statusLabel.setFont(KromiumTheme.FONT_SMALL);
        add(statusLabel, BorderLayout.WEST);

        // Right Controls
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setOpaque(false);

        // Zoom Controls
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

        zoomLabel.setForeground(KromiumTheme.TEXT_SECONDARY);
        zoomLabel.setFont(KromiumTheme.FONT_SMALL);

        rightPanel.add(zoomOutBtn);
        rightPanel.add(zoomLabel);
        rightPanel.add(zoomInBtn);
        rightPanel.add(zoomResetBtn);

        // Engine info
        try {
            engineLabel.setText("CEF " + KromiumEngine.getInfo().getJcefVersion());
        } catch (Throwable t) {
            engineLabel.setText("CEF 150");
        }
        engineLabel.setForeground(KromiumTheme.TEXT_MUTED);
        engineLabel.setFont(KromiumTheme.FONT_SMALL);
        rightPanel.add(Box.createHorizontalStrut(8));
        rightPanel.add(engineLabel);

        add(rightPanel, BorderLayout.EAST);
    }

    public void updateState(BrowserTab tab) {
        this.activeTab = tab;
        if (tab == null) {
            statusLabel.setText("Ready");
            zoomLabel.setText("100%");
            return;
        }

        if (tab.isLoading()) {
            statusLabel.setText("Loading " + (tab.getCurrentUrl() != null ? tab.getCurrentUrl() : "") + "...");
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

    private static JButton createTextButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(KromiumTheme.FONT_SMALL);
        btn.setForeground(KromiumTheme.TEXT_SECONDARY);
        btn.setToolTipText(tooltip);
        btn.setFocusable(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(new EmptyBorder(1, 4, 1, 4));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
