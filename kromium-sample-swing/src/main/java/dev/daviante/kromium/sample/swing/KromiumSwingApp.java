package dev.daviante.kromium.sample.swing;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.domain.model.KromiumState;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.sample.swing.ui.BrowserFrame;

import javax.swing.*;
import java.awt.*;

/**
 * Main entry point for the Kromium Pure Java Swing desktop browser showcase.
 *
 * Demonstrates:
 * 1. Non-blocking asynchronous engine initialization via {@link Kromium#initializeAsync}.
 * 2. FlatLaf modern dark UI integration.
 * 3. Client retrieval via {@link Kromium#awaitClientAsync()}.
 * 4. Multi-tab browsing, DevTools inspection, JavaScript evaluation, and download management.
 */
public class KromiumSwingApp {

    public static void main(String[] args) {
        // 1. Initialize modern Swing Look & Feel
        FlatDarkLaf.setup();
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        // 2. Show sleek startup splash screen
        JWindow splash = createSplashWindow();
        splash.setVisible(true);

        // 3. Register shutdown hook for clean engine disposal
        Runtime.getRuntime().addShutdownHook(new Thread(Kromium::dispose));

        // 4. Configure Kromium using fluent Java builder
        KromiumConfig config = KromiumConfig.builder()
                .userAgent("Kromium-Swing/1.0 (Macintosh; Intel Mac OS X)")
                .remoteDebuggingPort(9222)
                .build();

        // 5. Track initialization state
        Kromium.addStateListener(state -> {
            if (state instanceof KromiumState.Downloading dl) {
                // Download progress is already logged by Kromium
            }
        });

        // 6. Asynchronously initialize engine and await client
        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> SwingUtilities.invokeLater(() -> {
                    splash.dispose();
                    BrowserFrame frame = new BrowserFrame(client);
                    frame.addNewTab("https://github.com/daviantegroup/kromium");
                    frame.setVisible(true);
                }))
                .exceptionally(err -> {
                    SwingUtilities.invokeLater(() -> {
                        splash.dispose();
                        JOptionPane.showMessageDialog(
                                null,
                                "Failed to initialize Kromium:\n" + err.getMessage(),
                                "Initialization Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                        System.exit(1);
                    });
                    return null;
                });
    }

    private static JWindow createSplashWindow() {
        JWindow splash = new JWindow();
        splash.setSize(380, 180);
        splash.setLocationRelativeTo(null);

        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)
        ));

        JLabel titleLabel = new JLabel("Kromium Desktop");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitleLabel = new JLabel("Starting Chromium Embedded Framework...");
        subtitleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 6, 6));
        centerPanel.add(subtitleLabel);
        centerPanel.add(progressBar);

        content.add(titleLabel, BorderLayout.NORTH);
        content.add(centerPanel, BorderLayout.CENTER);

        splash.setContentPane(content);
        return splash;
    }
}
