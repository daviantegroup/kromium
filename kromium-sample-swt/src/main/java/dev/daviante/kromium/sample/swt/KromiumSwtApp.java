package dev.daviante.kromium.sample.swt;

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

public class KromiumSwtApp {

    public static void main(String[] args) {
        // 1. Initialize SWT Display and Shell on the Main UI thread
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("Kromium SWT Bridged Sample");
        shell.setSize(1200, 800);
        shell.setLayout(new GridLayout(1, false));

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
        KromiumConfig config = KromiumConfig.builder()
                .windowlessRendering(false)
                .build();

        Kromium.initializeAsync(config)
                .thenCompose(v -> Kromium.awaitClientAsync())
                .thenAccept(client -> {
                    KromiumBrowser browser = client.createBrowser("https://github.com/daviantegroup/kromium", false, false);

                    display.asyncExec(() -> {
                        if (shell.isDisposed()) return;

                        frame.add(browser.getUiComponent(), BorderLayout.CENTER);
                        frame.revalidate();
                        frame.repaint();

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
                    ex.printStackTrace();
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