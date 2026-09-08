package dev.daviante.kromium.sample.swing.ui;

import dev.daviante.kromium.presentation.browser.KromiumBrowser;
import dev.daviante.kromium.presentation.browser.KromiumClient;
import dev.daviante.kromium.presentation.network.KromiumCookieManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application window hosting multi-tab browsing, navigation toolbar,
 * status bar, menus, and download management.
 */
public class BrowserFrame extends JFrame {

    private final KromiumClient client;
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final List<BrowserTab> tabs = new ArrayList<>();
    private final NavigationToolbar toolbar;
    private final StatusBar statusBar = new StatusBar();
    private final DownloadManagerDialog downloadDialog;

    public BrowserFrame(KromiumClient client) {
        super(dev.daviante.kromium.sample.swing.KromiumSwingApp.APP_NAME);
        this.client = client;
        this.downloadDialog = new DownloadManagerDialog(this);

        setIconImages(dev.daviante.kromium.sample.swing.KromiumSwingApp.getAppIcons());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Setup Navigation Toolbar
        this.toolbar = new NavigationToolbar(() -> downloadDialog.setVisible(true));
        add(toolbar, BorderLayout.NORTH);

        // Setup global download handler
        client.setDownloadListener(item -> {
            downloadDialog.updateDownload(item);
            SwingUtilities.invokeLater(() -> {
                toolbar.updateDownloadCount(downloadDialog.getActiveCount());
            });
        });

        // Setup Tabbed Pane
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.addChangeListener(e -> onTabSelectionChanged());
        add(tabbedPane, BorderLayout.CENTER);

        // Setup Status Bar
        add(statusBar, BorderLayout.SOUTH);

        // Setup Menus
        setupMenuBar();

        // Window Closing Listener
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disposeBrowserAndExit();
            }
        });
    }

    public void addNewTab(String url) {
        SwingUtilities.invokeLater(() -> {
            KromiumBrowser browser = client.createBrowser(url, false, false);
            BrowserTab tab = new BrowserTab(browser, url);
            tabs.add(tab);

            tabbedPane.addTab(tab.getTitle(), tab);
            int index = tabbedPane.indexOfComponent(tab);

            // Custom tab header with title and close button
            JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            tabHeader.setOpaque(false);
            JLabel titleLabel = new JLabel(tab.getTitle());
            JButton closeBtn = new JButton("✕");
            closeBtn.setMargin(new Insets(0, 4, 0, 4));
            closeBtn.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            closeBtn.setContentAreaFilled(false);
            closeBtn.setFocusable(false);
            closeBtn.addActionListener(e -> closeTab(tab));

            tabHeader.add(titleLabel);
            tabHeader.add(closeBtn);
            tabbedPane.setTabComponentAt(index, tabHeader);

            tab.setOnStateChanged(updatedTab -> SwingUtilities.invokeLater(() -> {
                int idx = tabbedPane.indexOfComponent(updatedTab);
                if (idx >= 0) {
                    titleLabel.setText(truncateTitle(updatedTab.getTitle()));
                    tabbedPane.setTitleAt(idx, updatedTab.getTitle());
                }
                if (tabbedPane.getSelectedComponent() == updatedTab) {
                    toolbar.updateState(updatedTab);
                    statusBar.updateState(updatedTab);
                    updateWindowTitle(updatedTab.getTitle());
                }
            }));

            tabbedPane.setSelectedComponent(tab);
        });
    }

    public void closeTab(BrowserTab tab) {
        int index = tabbedPane.indexOfComponent(tab);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            tabs.remove(tab);
            tab.dispose();

            if (tabs.isEmpty()) {
                addNewTab("https://github.com/daviantegroup/kromium");
            }
        }
    }

    private void onTabSelectionChanged() {
        Component selected = tabbedPane.getSelectedComponent();
        if (selected instanceof BrowserTab activeTab) {
            toolbar.updateState(activeTab);
            statusBar.updateState(activeTab);
            updateWindowTitle(activeTab.getTitle());
        } else {
            toolbar.updateState(null);
            statusBar.updateState(null);
            setTitle(dev.daviante.kromium.sample.swing.KromiumSwingApp.APP_NAME);
        }
    }

    private void updateWindowTitle(String pageTitle) {
        if (pageTitle != null && !pageTitle.isBlank()) {
            setTitle(pageTitle + " — " + dev.daviante.kromium.sample.swing.KromiumSwingApp.APP_NAME);
        } else {
            setTitle(dev.daviante.kromium.sample.swing.KromiumSwingApp.APP_NAME);
        }
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem newTabItem = new JMenuItem("New Tab");
        newTabItem.setAccelerator(KeyStroke.getKeyStroke("meta T"));
        newTabItem.addActionListener(e -> addNewTab("https://google.com"));

        JMenuItem closeTabItem = new JMenuItem("Close Tab");
        closeTabItem.setAccelerator(KeyStroke.getKeyStroke("meta W"));
        closeTabItem.addActionListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected instanceof BrowserTab activeTab) {
                closeTab(activeTab);
            }
        });

        JMenuItem downloadsItem = new JMenuItem("Downloads");
        downloadsItem.setAccelerator(KeyStroke.getKeyStroke("meta J"));
        downloadsItem.addActionListener(e -> downloadDialog.setVisible(true));

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setAccelerator(KeyStroke.getKeyStroke("meta Q"));
        exitItem.addActionListener(e -> disposeBrowserAndExit());

        fileMenu.add(newTabItem);
        fileMenu.add(closeTabItem);
        fileMenu.addSeparator();
        fileMenu.add(downloadsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // View Menu
        JMenu viewMenu = new JMenu("View");
        JMenuItem reloadItem = new JMenuItem("Reload");
        reloadItem.setAccelerator(KeyStroke.getKeyStroke("meta R"));
        reloadItem.addActionListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected instanceof BrowserTab tab) tab.reload();
        });

        JMenuItem devToolsItem = new JMenuItem("Developer Tools");
        devToolsItem.setAccelerator(KeyStroke.getKeyStroke("meta alt I"));
        devToolsItem.addActionListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected instanceof BrowserTab tab) tab.openDevTools();
        });

        viewMenu.add(reloadItem);
        viewMenu.addSeparator();
        viewMenu.add(devToolsItem);

        // Tools Menu
        JMenu toolsMenu = new JMenu("Tools");
        JMenuItem clearCookiesItem = new JMenuItem("Clear Cookies");
        clearCookiesItem.addActionListener(e -> {
            boolean cleared = KromiumCookieManager.clearCookies();
            JOptionPane.showMessageDialog(this, cleared ? "Cookies cleared successfully." : "Failed to clear cookies.", "Cookies", JOptionPane.INFORMATION_MESSAGE);
        });

        JMenuItem openDownloadsFolder = new JMenuItem("Open Downloads Folder");
        openDownloadsFolder.addActionListener(e -> {
            File dir = client.getDownloadDirectory();
            if (dir != null && dir.exists() && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().open(dir);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Could not open folder: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        toolsMenu.add(clearCookiesItem);
        toolsMenu.add(openDownloadsFolder);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(toolsMenu);
        setJMenuBar(menuBar);
    }

    private void disposeBrowserAndExit() {
        for (BrowserTab tab : tabs) {
            tab.dispose();
        }
        tabs.clear();
        downloadDialog.dispose();
        dispose();
        System.exit(0);
    }

    private static String truncateTitle(String title) {
        if (title == null) return "New Tab";
        return title.length() > 20 ? title.substring(0, 18) + "…" : title;
    }
}
