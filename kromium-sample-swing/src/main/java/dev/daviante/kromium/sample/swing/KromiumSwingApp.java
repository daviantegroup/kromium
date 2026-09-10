package dev.daviante.kromium.sample.swing;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.domain.model.KromiumState;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.sample.swing.ui.BrowserFrame;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the Kromium Pure Java Swing desktop browser showcase.
 *
 * Provides seamless cross-platform native OS integration with official Kromium branding,
 * taskbar/dock icon dispatching, multi-resolution scaling, and asynchronous engine coordination.
 */
public class KromiumSwingApp {

    public static final String APP_NAME = "Kromium";
    private static List<Image> appIcons;

    public static void main(String[] args) {
        // 1. Set system properties for macOS / platform naming before AWT initializes
        System.setProperty("apple.awt.application.name", APP_NAME);
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        // 2. Initialize modern FlatLaf dark look and feel
        FlatDarkLaf.setup();

        // 3. Set native OS Taskbar / Dock icon across platforms
        setupNativeTaskbarIcon();

        // 4. Show sleek branded startup splash screen
        JWindow splash = createSplashWindow();
        splash.setVisible(true);

        // 5. Register JVM shutdown hook for clean engine disposal
        Runtime.getRuntime().addShutdownHook(new Thread(Kromium::dispose));

        // 6. Configure Kromium using fluent Java builder
        KromiumConfig config = KromiumConfig.builder()
                .remoteDebuggingPort(9222)
                .build();

        // 7. Track initialization state
        Kromium.addStateListener(state -> {
            if (state instanceof KromiumState.Downloading dl) {
                // Background download progress logged by Kromium
            }
        });

        // 8. Asynchronously initialize engine and await client
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
                                APP_NAME + " - Initialization Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                        System.exit(1);
                    });
                    return null;
                });
    }

    /**
     * Loads and caches multi-resolution Kromium branding icons for window titlebars,
     * taskbar buttons, and OS desktop switchers.
     */
    public static synchronized List<Image> getAppIcons() {
        if (appIcons == null) {
            appIcons = new ArrayList<>();
            try {
                InputStream is = KromiumSwingApp.class.getResourceAsStream("/icon.png");
                if (is == null) {
                    is = KromiumSwingApp.class.getResourceAsStream("/logo.png");
                }
                if (is != null) {
                    BufferedImage base = ImageIO.read(is);
                    if (base != null) {
                        int[] resolutions = {16, 24, 32, 48, 64, 128, 256, 512};
                        for (int res : resolutions) {
                            appIcons.add(base.getScaledInstance(res, res, Image.SCALE_SMOOTH));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return appIcons;
    }

    public static Image getPrimaryAppIcon() {
        List<Image> icons = getAppIcons();
        return icons.isEmpty() ? null : icons.get(icons.size() - 1);
    }

    private static void setupNativeTaskbarIcon() {
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    Image icon = getPrimaryAppIcon();
                    if (icon != null) {
                        taskbar.setIconImage(icon);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Taskbar API not supported on this specific window manager
        }
    }

    private static JWindow createSplashWindow() {
        JWindow splash = new JWindow();
        splash.setSize(400, 220);
        splash.setLocationRelativeTo(null);

        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)
        ));

        // Header with Kromium logo and title
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        Image icon = getPrimaryAppIcon();
        if (icon != null) {
            JLabel logoLabel = new JLabel(new ImageIcon(icon.getScaledInstance(40, 40, Image.SCALE_SMOOTH)));
            headerPanel.add(logoLabel);
        }
        JLabel titleLabel = new JLabel(APP_NAME);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        headerPanel.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Starting Chromium Embedded Framework...");
        subtitleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        centerPanel.add(subtitleLabel);
        centerPanel.add(progressBar);

        content.add(headerPanel, BorderLayout.NORTH);
        content.add(centerPanel, BorderLayout.CENTER);

        splash.setContentPane(content);
        return splash;
    }
}

