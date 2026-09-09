package dev.daviante.kromium.sample.awt;

import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;

public class KromiumAwtApp {

    public static void main(String[] args) {
        // 1. Create Frame and UI on Main/EDT thread
        Frame frame = new Frame("Kromium AWT Heavyweight Sample");
        frame.setLayout(new BorderLayout());
        frame.setSize(1200, 800);
        frame.setLocationRelativeTo(null);

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
        KromiumConfig config = KromiumConfig.builder()
                .windowlessRendering(false) 
                .build();

        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> {
                    KromiumBrowser browser = client.createBrowser("https://github.com/daviantegroup/kromium", false, false);

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