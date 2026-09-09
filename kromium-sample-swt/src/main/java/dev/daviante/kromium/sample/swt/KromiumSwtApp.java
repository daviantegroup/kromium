package dev.daviante.kromium.sample.swt;

import dev.daviante.kromium.core.logging.KromiumLogger;
import dev.daviante.kromium.domain.config.KromiumConfig;
import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.browser.KromiumBrowser;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.awt.BorderLayout;
import java.io.InputStream;

public class KromiumSwtApp {

    public static final String APP_NAME = "Kromium SWT";

    public static void main(String[] args) {
        Display.setAppName(APP_NAME);

        // 1. Initialize SWT Display and Shell on the Main UI thread
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("Kromium - SWT Browser");
        shell.setSize(1200, 800);
        shell.setLayout(new GridLayout(1, false));

        // Set window and dock/taskbar icons
        try (InputStream is = KromiumSwtApp.class.getResourceAsStream("/icon.png")) {
            if (is != null) {
                org.eclipse.swt.graphics.Image swtIcon = new org.eclipse.swt.graphics.Image(display, is);
                shell.setImage(swtIcon);
                shell.addDisposeListener(e -> swtIcon.dispose());
            }
        } catch (Exception ignored) {}

        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    try (InputStream is = KromiumSwtApp.class.getResourceAsStream("/icon.png")) {
                        if (is != null) {
                            java.awt.Image awtImg = javax.imageio.ImageIO.read(is);
                            if (awtImg != null) {
                                taskbar.setIconImage(awtImg);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Create Toolbar Composite
        Composite toolbar = new Composite(shell, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        toolbar.setLayout(new GridLayout(4, false));

        Button backBtn = new Button(toolbar, SWT.PUSH);
        backBtn.setText("Back");

        Button fwdBtn = new Button(toolbar, SWT.PUSH);
        fwdBtn.setText("Forward");

        Button reloadBtn = new Button(toolbar, SWT.PUSH);
        reloadBtn.setText("Reload");

        Text addressBar = new Text(toolbar, SWT.BORDER);
        addressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        addressBar.setText("https://github.com/daviantegroup/kromium");

        // 3. Create Embedded AWT Composite container
        Composite composite = new Composite(shell, SWT.EMBEDDED | SWT.NO_BACKGROUND);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        java.awt.Frame frame = SWT_AWT.new_Frame(composite);
        frame.setLayout(new BorderLayout());

        // 4. Configure and initialize Kromium
        // On macOS Cocoa, SWT_AWT embedded frames return a native window handle of 0,
        // preventing native heavyweight window reparenting. Lightweight OSR renders flawlessly via Java2D.
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        boolean windowless = isMac;

        KromiumConfig config = KromiumConfig.builder()
                .windowlessRendering(windowless)
                .build();

        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> {
                    KromiumBrowser browser = client.createBrowser("https://github.com/daviantegroup/kromium", windowless, windowless);

                    javax.swing.SwingUtilities.invokeLater(() -> {
                        frame.add(browser.getUiComponent(), BorderLayout.CENTER);
                        frame.revalidate();
                        frame.repaint();
                    });

                    display.asyncExec(() -> {
                        if (shell.isDisposed()) return;

                        backBtn.addSelectionListener(new SelectionAdapter() {
                            @Override public void widgetSelected(SelectionEvent e) { browser.goBack(); }
                        });
                        fwdBtn.addSelectionListener(new SelectionAdapter() {
                            @Override public void widgetSelected(SelectionEvent e) { browser.goForward(); }
                        });
                        reloadBtn.addSelectionListener(new SelectionAdapter() {
                            @Override public void widgetSelected(SelectionEvent e) { browser.reload(); }
                        });
                        addressBar.addSelectionListener(new SelectionAdapter() {
                            @Override public void widgetDefaultSelected(SelectionEvent e) { browser.loadUrl(addressBar.getText()); }
                        });

                        browser.onAddressChanged(url -> display.asyncExec(() -> {
                            if (!addressBar.isDisposed()) {
                                addressBar.setText(url);
                            }
                        }));
                    });
                })
                .exceptionally(ex -> {
                    KromiumLogger.e("KromiumSwtApp", "Engine initialization failed", ex);
                    return null;
                });

        shell.open();

        // 5. SWT Event Dispatch Loop on the main thread
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        display.dispose();
        Kromium.dispose();
        System.exit(0);
    }
}