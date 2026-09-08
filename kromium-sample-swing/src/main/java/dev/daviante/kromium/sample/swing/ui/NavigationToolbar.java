package dev.daviante.kromium.sample.swing.ui;

import dev.daviante.kromium.sample.swing.theme.KromiumTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Top navigation toolbar with monochrome (black & white) styling and vector icons
 * matching the Kromium Compose layout.
 */
public class NavigationToolbar extends JPanel {

    private final JButton backButton = createIconButton(KromiumIcons.back(18, Color.WHITE), "Back");
    private final JButton forwardButton = createIconButton(KromiumIcons.forward(18, Color.WHITE), "Forward");
    private final JButton reloadButton = createIconButton(KromiumIcons.reload(18, Color.WHITE), "Reload");
    private final JTextField urlField = new JTextField();
    private final JLabel lockIconLabel = new JLabel(KromiumIcons.lock(15, Color.WHITE));
    private final JPanel omniboxPanel = new JPanel(new BorderLayout(8, 0));
    private final JButton devToolsButton = createIconButton(KromiumIcons.inspect(18, Color.WHITE), "Developer Tools");
    private final JButton downloadsButton = createIconButton(KromiumIcons.download(18, Color.WHITE), "Downloads");
    private final JLabel downloadBadge = new JLabel();

    private BrowserTab activeTab;
    private final Runnable onOpenDownloads;

    public NavigationToolbar(Runnable onOpenDownloads) {
        super(new BorderLayout(8, 0));
        this.onOpenDownloads = onOpenDownloads;

        setBackground(KromiumTheme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, KromiumTheme.BORDER_SUBTLE),
                new EmptyBorder(6, 12, 6, 12)
        ));
        setPreferredSize(new Dimension(800, 46));

        // Left Navigation Cluster (Back, Forward, Reload)
        JPanel leftCluster = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftCluster.setOpaque(false);
        leftCluster.add(backButton);
        leftCluster.add(forwardButton);
        leftCluster.add(reloadButton);
        add(leftCluster, BorderLayout.WEST);

        // Center Omnibox
        setupOmnibox();
        add(omniboxPanel, BorderLayout.CENTER);

        // Right Action Cluster (Inspect, Downloads)
        JPanel rightCluster = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightCluster.setOpaque(false);

        // Downloads button with text count
        downloadsButton.setText("Downloads");
        downloadsButton.setFont(KromiumTheme.FONT_SMALL);
        downloadsButton.setForeground(KromiumTheme.TEXT_SECONDARY);
        downloadsButton.addActionListener(e -> {
            if (onOpenDownloads != null) onOpenDownloads.run();
        });

        devToolsButton.addActionListener(e -> {
            if (activeTab != null) activeTab.openDevTools();
        });

        rightCluster.add(downloadsButton);
        rightCluster.add(devToolsButton);
        add(rightCluster, BorderLayout.EAST);

        // Action Listeners
        backButton.addActionListener(e -> { if (activeTab != null) activeTab.goBack(); });
        forwardButton.addActionListener(e -> { if (activeTab != null) activeTab.goForward(); });
        reloadButton.addActionListener(e -> { if (activeTab != null) activeTab.reload(); });

        updateState(null);
    }

    private void setupOmnibox() {
        omniboxPanel.setBackground(KromiumTheme.SURFACE_ELEVATED);
        omniboxPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(KromiumTheme.BORDER, 1, true),
                new EmptyBorder(4, 12, 4, 12)
        ));

        lockIconLabel.setOpaque(false);
        omniboxPanel.add(lockIconLabel, BorderLayout.WEST);

        urlField.setBackground(KromiumTheme.SURFACE_ELEVATED);
        urlField.setForeground(KromiumTheme.TEXT_PRIMARY);
        urlField.setCaretColor(Color.WHITE);
        urlField.setFont(KromiumTheme.FONT_REGULAR);
        urlField.setBorder(null);
        urlField.putClientProperty("JTextField.placeholderText", "Search or enter web address");

        urlField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateFromField();
                }
            }
        });

        urlField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                omniboxPanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.WHITE, 1, true),
                        new EmptyBorder(4, 12, 4, 12)
                ));
            }

            @Override
            public void focusLost(FocusEvent e) {
                omniboxPanel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(KromiumTheme.BORDER, 1, true),
                        new EmptyBorder(4, 12, 4, 12)
                ));
            }
        });

        omniboxPanel.add(urlField, BorderLayout.CENTER);
    }

    public void updateState(BrowserTab tab) {
        this.activeTab = tab;
        if (tab == null) {
            backButton.setEnabled(false);
            forwardButton.setEnabled(false);
            reloadButton.setEnabled(false);
            urlField.setEnabled(false);
            devToolsButton.setEnabled(false);
            return;
        }

        backButton.setEnabled(tab.canGoBack());
        forwardButton.setEnabled(tab.canGoForward());
        reloadButton.setEnabled(true);
        reloadButton.setIcon(tab.isLoading() ? KromiumIcons.close(18, Color.WHITE) : KromiumIcons.reload(18, Color.WHITE));
        reloadButton.setToolTipText(tab.isLoading() ? "Stop loading" : "Reload page");

        urlField.setEnabled(true);
        if (!urlField.hasFocus()) {
            urlField.setText(tab.getCurrentUrl() != null ? tab.getCurrentUrl() : "");
        }

        boolean isHttps = tab.getCurrentUrl() != null && tab.getCurrentUrl().startsWith("https://");
        lockIconLabel.setIcon(KromiumIcons.lock(15, isHttps ? Color.WHITE : KromiumTheme.TEXT_MUTED));
        devToolsButton.setEnabled(true);
    }

    public void updateDownloadCount(int activeCount) {
        if (activeCount > 0) {
            downloadsButton.setText("Downloads (" + activeCount + ")");
            downloadsButton.setForeground(Color.WHITE);
        } else {
            downloadsButton.setText("Downloads");
            downloadsButton.setForeground(KromiumTheme.TEXT_SECONDARY);
        }
    }

    private void navigateFromField() {
        String text = urlField.getText().trim();
        if (!text.isEmpty() && activeTab != null) {
            activeTab.navigate(text);
        }
    }

    private static JButton createIconButton(Icon icon, String toolTip) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(toolTip);
        btn.setFocusable(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(new EmptyBorder(4, 6, 4, 6));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
