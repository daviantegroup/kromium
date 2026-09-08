package dev.daviante.kromium.sample.swing.ui;

import dev.daviante.kromium.presentation.browser.KromiumBrowser;
import dev.daviante.kromium.presentation.browser.KromiumClient;
import dev.daviante.kromium.presentation.network.KromiumCookieManager;
import dev.daviante.kromium.sample.swing.KromiumSwingApp;
import dev.daviante.kromium.sample.swing.theme.KromiumTheme;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application window hosting multi-tab browsing, navigation toolbar,
 * status bar, and download management with pure monochrome design matching Compose.
 */
public class BrowserFrame extends JFrame {

    private final KromiumClient client;
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final List<BrowserTab> tabs = new ArrayList<>();
    private final NavigationToolbar toolbar;
    private final StatusBar statusBar = new StatusBar();
    private final DownloadManagerDialog downloadDialog;

    public BrowserFrame(KromiumClient client) {
        super(KromiumSwingApp.APP_NAME);
        this.client = client;
        this.downloadDialog = new DownloadManagerDialog(this, this::triggerTestDownload);

        setIconImages(KromiumSwingApp.getAppIcons());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1360, 860);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        getContentPane().setBackground(KromiumTheme.BACKGROUND);
        setLayout(new BorderLayout());

        // Setup Navigation Toolbar
        this.toolbar = new NavigationToolbar(() -> downloadDialog.setVisible(true));

        // Setup global download handler
        client.setDownloadListener(item -> {
            downloadDialog.updateDownload(item);
            SwingUtilities.invokeLater(() -> {
                toolbar.updateDownloadCount(downloadDialog.getActiveCount());
            });
        });

        // Setup Tabbed Pane Header matching Compose TabBar
        setupTabbedPane();

        // Combine TabBar and Navigation Toolbar in North Panel
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setBackground(KromiumTheme.SURFACE);
        topContainer.add(tabbedPane, BorderLayout.NORTH);
        topContainer.add(toolbar, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // Active Content Area
        JPanel contentArea = new JPanel(new CardLayout());
        contentArea.setBackground(KromiumTheme.BACKGROUND);
        add(contentArea, BorderLayout.CENTER);

        // Synchronize tabbedPane selection with contentArea
        tabbedPane.addChangeListener(e -> {
            int selected = tabbedPane.getSelectedIndex();
            if (selected >= 0 && selected < tabs.size()) {
                BrowserTab activeTab = tabs.get(selected);
                contentArea.removeAll();
                contentArea.add(activeTab, BorderLayout.CENTER);
                contentArea.revalidate();
                contentArea.repaint();
                toolbar.updateState(activeTab);
                statusBar.updateState(activeTab);
                updateWindowTitle(activeTab.getTitle());
            } else {
                toolbar.updateState(null);
                statusBar.updateState(null);
                setTitle(KromiumSwingApp.APP_NAME);
            }
        });

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

    private void setupTabbedPane() {
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setBackground(KromiumTheme.SURFACE);
        tabbedPane.setForeground(KromiumTheme.TEXT_SECONDARY);
        tabbedPane.setBorder(null);

        // Leading Component: Kromium Brand identification
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        brandPanel.setOpaque(false);
        Image icon = KromiumSwingApp.getPrimaryAppIcon();
        if (icon != null) {
            brandPanel.add(new JLabel(new ImageIcon(icon.getScaledInstance(18, 18, Image.SCALE_SMOOTH))));
        }
        JLabel brandLabel = new JLabel("Kromium");
        brandLabel.setFont(KromiumTheme.FONT_TITLE);
        brandLabel.setForeground(Color.WHITE);
        brandPanel.add(brandLabel);
        brandPanel.setBorder(new EmptyBorder(0, 10, 0, 8));
        tabbedPane.putClientProperty("JTabbedPane.leadingComponent", brandPanel);

        // Trailing Component: Plus (New Tab) Button
        JButton newTabButton = new JButton(KromiumIcons.plus(15, Color.WHITE));
        newTabButton.setToolTipText("New Tab (Cmd+T / Ctrl+T)");
        newTabButton.setFocusable(false);
        newTabButton.setContentAreaFilled(false);
        newTabButton.setBorder(new EmptyBorder(4, 8, 4, 12));
        newTabButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newTabButton.addActionListener(e -> addNewTab("https://github.com/daviantegroup/kromium"));
        tabbedPane.putClientProperty("JTabbedPane.trailingComponent", newTabButton);
    }

    public void addNewTab(String url) {
        SwingUtilities.invokeLater(() -> {
            // Use true, true to force OSR + Transparent, which forces JCEF to use a Lightweight GLJPanel
            // instead of a Heavyweight GLCanvas, completely eliminating native focus and Z-ordering bugs.
            KromiumBrowser browser = client.createBrowser(url, true, true);
            BrowserTab tab = new BrowserTab(browser, url);
            tabs.add(tab);

            // Dummy component for tabbedPane index tracking (actual component rendered in contentArea CardLayout)
            JPanel placeholder = new JPanel();
            placeholder.setBackground(KromiumTheme.BACKGROUND);
            tabbedPane.addTab(tab.getTitle(), placeholder);
            int index = tabbedPane.indexOfComponent(placeholder);

            // Custom tab header with title and close icon
            JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            tabHeader.setOpaque(false);
            JLabel titleLabel = new JLabel(truncateTitle(tab.getTitle()));
            titleLabel.setFont(KromiumTheme.FONT_REGULAR);
            titleLabel.setForeground(Color.WHITE);

            JButton closeBtn = new JButton(KromiumIcons.close(12, KromiumTheme.TEXT_SECONDARY));
            closeBtn.setRolloverIcon(KromiumIcons.close(12, Color.WHITE));
            closeBtn.setToolTipText("Close Tab");
            closeBtn.setContentAreaFilled(false);
            closeBtn.setBorder(new EmptyBorder(0, 4, 0, 0));
            closeBtn.setFocusable(false);
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addActionListener(e -> closeTab(tab));

            tabHeader.add(titleLabel);
            tabHeader.add(closeBtn);
            tabbedPane.setTabComponentAt(index, tabHeader);

            tab.setOnStateChanged(updatedTab -> SwingUtilities.invokeLater(() -> {
                int idx = tabs.indexOf(updatedTab);
                if (idx >= 0 && idx < tabbedPane.getTabCount()) {
                    titleLabel.setText(truncateTitle(updatedTab.getTitle()));
                    tabbedPane.setTitleAt(idx, updatedTab.getTitle());
                }
                if (tabbedPane.getSelectedIndex() == idx) {
                    toolbar.updateState(updatedTab);
                    statusBar.updateState(updatedTab);
                    updateWindowTitle(updatedTab.getTitle());
                }
            }));

            tabbedPane.setSelectedIndex(index);
        });
    }

    public void closeTab(BrowserTab tab) {
        int index = tabs.indexOf(tab);
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
            tabs.remove(tab);
            tab.dispose();

            if (tabs.isEmpty()) {
                addNewTab("https://github.com/daviantegroup/kromium");
            }
        }
    }

    private void updateWindowTitle(String pageTitle) {
        if (pageTitle != null && !pageTitle.isBlank()) {
            setTitle(pageTitle + " — " + KromiumSwingApp.APP_NAME);
        } else {
            setTitle(KromiumSwingApp.APP_NAME);
        }
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(KromiumTheme.SURFACE);
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, KromiumTheme.BORDER_SUBTLE));

        // File Menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setForeground(Color.WHITE);
        JMenuItem newTabItem = new JMenuItem("New Tab");
        newTabItem.setAccelerator(KeyStroke.getKeyStroke("meta T"));
        newTabItem.addActionListener(e -> addNewTab("https://github.com/daviantegroup/kromium"));

        JMenuItem closeTabItem = new JMenuItem("Close Tab");
        closeTabItem.setAccelerator(KeyStroke.getKeyStroke("meta W"));
        closeTabItem.addActionListener(e -> {
            int selected = tabbedPane.getSelectedIndex();
            if (selected >= 0 && selected < tabs.size()) {
                closeTab(tabs.get(selected));
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
        viewMenu.setForeground(Color.WHITE);
        JMenuItem reloadItem = new JMenuItem("Reload");
        reloadItem.setAccelerator(KeyStroke.getKeyStroke("meta R"));
        reloadItem.addActionListener(e -> {
            int selected = tabbedPane.getSelectedIndex();
            if (selected >= 0 && selected < tabs.size()) tabs.get(selected).reload();
        });

        JMenuItem devToolsItem = new JMenuItem("Developer Tools");
        devToolsItem.setAccelerator(KeyStroke.getKeyStroke("meta alt I"));
        devToolsItem.addActionListener(e -> {
            int selected = tabbedPane.getSelectedIndex();
            if (selected >= 0 && selected < tabs.size()) tabs.get(selected).openDevTools();
        });

        viewMenu.add(reloadItem);
        viewMenu.addSeparator();
        viewMenu.add(devToolsItem);

        // Tools Menu
        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setForeground(Color.WHITE);
        JMenuItem clearCookiesItem = new JMenuItem("Clear Cookies");
        clearCookiesItem.addActionListener(e -> {
            boolean cleared = KromiumCookieManager.clearCookies();
            JOptionPane.showMessageDialog(this, cleared ? "Cookies cleared." : "Failed to clear cookies.", "Cookies", JOptionPane.INFORMATION_MESSAGE);
        });

        JMenuItem openDownloadsFolder = new JMenuItem("Open Downloads Folder");
        openDownloadsFolder.addActionListener(e -> {
            File dir = client.getDownloadDirectory();
            openDirectory(this, dir);
        });

        JMenuItem testDownloadItem = new JMenuItem("Start Test Download (50 MB)");
        testDownloadItem.addActionListener(e -> triggerTestDownload());
        toolsMenu.add(testDownloadItem);
        toolsMenu.add(clearCookiesItem);
        toolsMenu.add(openDownloadsFolder);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(toolsMenu);
        setJMenuBar(menuBar);
    }

    public BrowserTab getActiveTab() {
        int selected = tabbedPane.getSelectedIndex();
        if (selected >= 0 && selected < tabs.size()) {
            return tabs.get(selected);
        }
        return !tabs.isEmpty() ? tabs.get(0) : null;
    }

    public void triggerTestDownload() {
        BrowserTab active = getActiveTab();
        if (active != null) {
            active.getBrowser().startDownload("https://speed.cloudflare.com/__down?bytes=50000000");
            downloadDialog.setVisible(true);
        }
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
        return title.length() > 24 ? title.substring(0, 22) + "…" : title;
    }

    public static void openDirectory(Component parent, File dir) {
        if (dir != null && dir.exists() && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(dir);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Could not open folder: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
