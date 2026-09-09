package dev.daviante.kromium.sample.awt;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Taskbar;
import java.awt.TextField;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public class KromiumAwtApp {

    public static final String APP_NAME = "Kromium AWT";

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", APP_NAME);

        // 1. Create Frame and UI on Main/EDT thread
        Frame frame = new Frame("Kromium - AWT Browser");
        frame.setLayout(new BorderLayout());
        frame.setSize(1200, 800);
        frame.setLocationRelativeTo(null);

        // Set application icon
        try (InputStream is = KromiumAwtApp.class.getResourceAsStream("/icon.png")) {
            if (is != null) {
                Image icon = ImageIO.read(is);
                if (icon != null) {
                    frame.setIconImage(icon);
                    if (Taskbar.isTaskbarSupported()) {
                        Taskbar taskbar = Taskbar.getTaskbar();
                        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                            taskbar.setIconImage(icon);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Kromium.dispose();
                frame.dispose();
                System.exit(0);
            }
        });

        // 2. Create Toolbar
        Panel toolbar = new Panel(new FlowLayout(FlowLayout.LEFT));
        Button backBtn = new Button("Back");
        Button fwdBtn = new Button("Forward");
        Button reloadBtn = new Button("Reload");
        TextField addressBar = new TextField("https://github.com/daviantegroup/kromium", 60);

        toolbar.add(backBtn);
        toolbar.add(fwdBtn);
        toolbar.add(reloadBtn);
        toolbar.add(addressBar);

        frame.add(toolbar, BorderLayout.NORTH);
        frame.setVisible(true);

        // 3. Configure and initialize Kromium
        // On macOS, native heavyweight embedding directly into an AWT Frame suffers from coordinate displacement
        // and missing CALayer container hierarchies in JCEF; lightweight OSR provides seamless, reliable rendering across all platforms.
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        boolean windowless = isMac;

        KromiumConfig config = KromiumConfig.builder()
                .windowlessRendering(windowless) 
                .build();

        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> {
                    KromiumBrowser browser = client.createBrowser("https://github.com/daviantegroup/kromium", windowless, windowless);

                    // Actions
                    backBtn.addActionListener(e -> browser.goBack());
                    fwdBtn.addActionListener(e -> browser.goForward());
                    reloadBtn.addActionListener(e -> browser.reload());
                    addressBar.addActionListener(e -> browser.loadUrl(addressBar.getText()));

                    // State updates
                    browser.onAddressChanged(url -> SwingUtilities.invokeLater(() -> addressBar.setText(url)));

                    SwingUtilities.invokeLater(() -> {
                        frame.add(browser.getUiComponent(), BorderLayout.CENTER);
                        frame.validate();
                    });
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }
}