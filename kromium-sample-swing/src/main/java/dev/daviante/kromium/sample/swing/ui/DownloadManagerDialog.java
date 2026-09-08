package dev.daviante.kromium.sample.swing.ui;

import dev.daviante.kromium.presentation.browser.Kromium;
import dev.daviante.kromium.presentation.handler.KromiumDownloadItem;
import dev.daviante.kromium.sample.swing.KromiumSwingApp;
import dev.daviante.kromium.sample.swing.model.DownloadEntry;
import dev.daviante.kromium.sample.swing.theme.KromiumTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modeless dialog providing a clean monochrome download manager for active and finished transfers.
 */
public class DownloadManagerDialog extends JDialog {

    private final Map<Integer, DownloadEntry> downloadMap = new ConcurrentHashMap<>();
    private final List<DownloadEntry> downloadList = new ArrayList<>();
    private final DownloadTableModel tableModel = new DownloadTableModel();
    private final JTable table;
    private final JLabel countLabel = new JLabel("0 active transfers");
    private Integer selectedDownloadId = null;

    private final JButton pauseBtn = createActionButton("Pause");
    private final JButton resumeBtn = createActionButton("Resume");
    private final JButton cancelBtn = createActionButton("Cancel");
    private final JButton openFolderBtn = createActionButton("Open in Folder");
    private final JButton clearBtn = createActionButton("Clear Finished");

    public DownloadManagerDialog(Frame owner) {
        this(owner, null);
    }

    public DownloadManagerDialog(Frame owner, Runnable testDownloadTrigger) {
        super(owner, "Downloads — " + KromiumSwingApp.APP_NAME, false);
        setIconImages(KromiumSwingApp.getAppIcons());
        setSize(760, 440);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(KromiumTheme.BACKGROUND);
        setLayout(new BorderLayout());

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(KromiumTheme.SURFACE);
        headerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel("Downloads");
        titleLabel.setFont(KromiumTheme.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        countLabel.setFont(KromiumTheme.FONT_SMALL);
        countLabel.setForeground(KromiumTheme.TEXT_SECONDARY);
        headerPanel.add(countLabel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Table
        table = new JTable(tableModel);
        table.setBackground(KromiumTheme.BACKGROUND);
        table.setForeground(Color.WHITE);
        table.setSelectionBackground(KromiumTheme.SURFACE_HIGHLIGHT);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(KromiumTheme.BORDER_SUBTLE);
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setBackground(KromiumTheme.SURFACE);
        table.getTableHeader().setForeground(KromiumTheme.TEXT_SECONDARY);
        table.getTableHeader().setFont(KromiumTheme.FONT_SMALL);

        table.getColumnModel().getColumn(2).setCellRenderer(new ProgressCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);

        // Track user selection & keep action button states synchronized
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0 && row < downloadList.size()) {
                    selectedDownloadId = downloadList.get(row).getId();
                }
                updateButtonStates();
            }
        });

        // Double click to open folder & right click context menu
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelectedFolder();
                }
            }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < downloadList.size()) {
                        table.setRowSelectionInterval(row, row);
                        selectedDownloadId = downloadList.get(row).getId();
                        updateButtonStates();
                        showContextMenu(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(KromiumTheme.BACKGROUND);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, KromiumTheme.BORDER_SUBTLE));
        add(scrollPane, BorderLayout.CENTER);

        // Action Toolbar
        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.setBackground(KromiumTheme.SURFACE);

        // Optional left-side test download button for developer testing
        JPanel leftCluster = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        leftCluster.setBackground(KromiumTheme.SURFACE);
        if (testDownloadTrigger != null) {
            JButton testBtn = createActionButton("Test Download (50MB)");
            testBtn.setToolTipText("Start a sample 50 MB download to test pause, resume, and cancel");
            testBtn.addActionListener(e -> testDownloadTrigger.run());
            leftCluster.add(testBtn);
        }
        actionPanel.add(leftCluster, BorderLayout.WEST);

        // Right-side actions
        JPanel rightCluster = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        rightCluster.setBackground(KromiumTheme.SURFACE);

        pauseBtn.addActionListener(e -> pauseSelectedDownload());
        resumeBtn.addActionListener(e -> resumeSelectedDownload());
        cancelBtn.addActionListener(e -> cancelSelectedDownload());
        openFolderBtn.addActionListener(e -> openSelectedFolder());
        clearBtn.addActionListener(e -> clearFinishedDownloads());

        rightCluster.add(pauseBtn);
        rightCluster.add(resumeBtn);
        rightCluster.add(cancelBtn);
        rightCluster.add(openFolderBtn);
        rightCluster.add(clearBtn);
        actionPanel.add(rightCluster, BorderLayout.EAST);

        add(actionPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    public void updateDownload(KromiumDownloadItem item) {
        SwingUtilities.invokeLater(() -> {
            synchronized (downloadList) {
                DownloadEntry entry = downloadMap.get(item.getId());
                boolean isNew = (entry == null);
                if (isNew) {
                    entry = new DownloadEntry(item);
                    downloadMap.put(item.getId(), entry);
                    downloadList.add(0, entry);
                    tableModel.fireTableRowsInserted(0, 0);
                    if (selectedDownloadId == null) {
                        selectedDownloadId = entry.getId();
                        table.setRowSelectionInterval(0, 0);
                    } else {
                        restoreSelection();
                    }
                } else {
                    entry.update(item);
                    int index = downloadList.indexOf(entry);
                    if (index >= 0) {
                        tableModel.fireTableRowsUpdated(index, index);
                    }
                    if (selectedDownloadId != null && selectedDownloadId == item.getId()) {
                        restoreSelection();
                    }
                }
                updateButtonStates();
                updateCount();
            }
        });
    }

    public int getActiveCount() {
        int count = 0;
        for (DownloadEntry entry : downloadMap.values()) {
            if (entry.isInProgress() || entry.isPaused()) {
                count++;
            }
        }
        return count;
    }

    private void updateCount() {
        int active = getActiveCount();
        countLabel.setText(active == 1 ? "1 active transfer" : active + " active transfers");
    }

    private DownloadEntry getSelectedEntry() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < downloadList.size()) {
            return downloadList.get(row);
        }
        if (selectedDownloadId != null) {
            DownloadEntry entry = downloadMap.get(selectedDownloadId);
            if (entry != null) return entry;
        }
        if (downloadList.size() == 1) {
            return downloadList.get(0);
        }
        return null;
    }

    private void restoreSelection() {
        if (selectedDownloadId == null) return;
        for (int i = 0; i < downloadList.size(); i++) {
            if (downloadList.get(i).getId() == selectedDownloadId) {
                if (table.getSelectedRow() != i) {
                    table.setRowSelectionInterval(i, i);
                }
                return;
            }
        }
    }

    private void notifyRowUpdated(DownloadEntry entry) {
        int index = downloadList.indexOf(entry);
        if (index >= 0) {
            tableModel.fireTableRowsUpdated(index, index);
        }
    }

    private void pauseSelectedDownload() {
        DownloadEntry entry = getSelectedEntry();
        if (entry != null && !entry.isPaused() && !entry.isComplete() && !entry.isCanceled()) {
            entry.setPaused(true);
            Kromium.pauseDownload(entry.getId());
            notifyRowUpdated(entry);
            updateButtonStates();
            updateCount();
        }
    }

    private void resumeSelectedDownload() {
        DownloadEntry entry = getSelectedEntry();
        if (entry != null && entry.isPaused()) {
            entry.setPaused(false);
            Kromium.resumeDownload(entry.getId());
            notifyRowUpdated(entry);
            updateButtonStates();
            updateCount();
        }
    }

    private void cancelSelectedDownload() {
        DownloadEntry entry = getSelectedEntry();
        if (entry != null && !entry.isComplete() && !entry.isCanceled()) {
            entry.setCanceled(true);
            Kromium.cancelDownload(entry.getId());
            notifyRowUpdated(entry);
            updateButtonStates();
            updateCount();
        }
    }

    private void openSelectedFolder() {
        DownloadEntry entry = getSelectedEntry();
        if (entry != null && entry.getFullPath() != null) {
            File file = new File(entry.getFullPath());
            File dir = file.isDirectory() ? file : file.getParentFile();
            if (dir != null && dir.exists() && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().open(dir);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Could not open folder: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void clearFinishedDownloads() {
        synchronized (downloadList) {
            downloadList.removeIf(d -> d.isComplete() || d.isCanceled());
            downloadMap.entrySet().removeIf(entry -> entry.getValue().isComplete() || entry.getValue().isCanceled());
            if (selectedDownloadId != null && !downloadMap.containsKey(selectedDownloadId)) {
                selectedDownloadId = null;
            }
            tableModel.fireTableDataChanged();
            if (selectedDownloadId != null) {
                restoreSelection();
            } else if (!downloadList.isEmpty()) {
                table.setRowSelectionInterval(0, 0);
                selectedDownloadId = downloadList.get(0).getId();
            }
            updateButtonStates();
            updateCount();
        }
    }

    private void updateButtonStates() {
        DownloadEntry entry = getSelectedEntry();
        if (entry == null) {
            pauseBtn.setEnabled(false);
            resumeBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            openFolderBtn.setEnabled(false);
            return;
        }

        boolean active = entry.isInProgress() && !entry.isPaused() && !entry.isComplete() && !entry.isCanceled();
        boolean paused = entry.isPaused() && !entry.isComplete() && !entry.isCanceled();
        boolean cancellable = !entry.isComplete() && !entry.isCanceled();
        boolean hasPath = entry.getFullPath() != null && !entry.getFullPath().isBlank();

        pauseBtn.setEnabled(active);
        resumeBtn.setEnabled(paused);
        cancelBtn.setEnabled(cancellable);
        openFolderBtn.setEnabled(hasPath);
    }

    private void showContextMenu(Component comp, int x, int y) {
        DownloadEntry entry = getSelectedEntry();
        if (entry == null) return;

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(KromiumTheme.SURFACE_ELEVATED);
        menu.setBorder(BorderFactory.createLineBorder(KromiumTheme.BORDER, 1));

        boolean active = entry.isInProgress() && !entry.isPaused() && !entry.isComplete() && !entry.isCanceled();
        boolean paused = entry.isPaused() && !entry.isComplete() && !entry.isCanceled();
        boolean cancellable = !entry.isComplete() && !entry.isCanceled();
        boolean hasPath = entry.getFullPath() != null && !entry.getFullPath().isBlank();

        if (active) {
            JMenuItem pauseItem = new JMenuItem("Pause");
            pauseItem.addActionListener(e -> pauseSelectedDownload());
            menu.add(pauseItem);
        }
        if (paused) {
            JMenuItem resumeItem = new JMenuItem("Resume");
            resumeItem.addActionListener(e -> resumeSelectedDownload());
            menu.add(resumeItem);
        }
        if (cancellable) {
            JMenuItem cancelItem = new JMenuItem("Cancel");
            cancelItem.addActionListener(e -> cancelSelectedDownload());
            menu.add(cancelItem);
        }
        if (hasPath) {
            if (active || paused || cancellable) menu.addSeparator();
            JMenuItem folderItem = new JMenuItem("Open in Folder");
            folderItem.addActionListener(e -> openSelectedFolder());
            menu.add(folderItem);
        }

        if (menu.getComponentCount() > 0) {
            menu.show(comp, x, y);
        }
    }

    private static JButton createActionButton(String label) {
        JButton btn = new JButton(label);
        btn.setFont(KromiumTheme.FONT_SMALL);
        btn.setForeground(Color.WHITE);
        btn.setBackground(KromiumTheme.SURFACE_ELEVATED);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(KromiumTheme.BORDER, 1),
                new EmptyBorder(4, 10, 4, 10)
        ));
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private class DownloadTableModel extends AbstractTableModel {
        private final String[] columns = {"File Name", "Status", "Progress", "Target Path"};

        @Override
        public int getRowCount() { return downloadList.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DownloadEntry entry = downloadList.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getFileName();
                case 1 -> entry.getStatusText();
                case 2 -> entry.getPercent();
                case 3 -> entry.getFullPath() != null ? entry.getFullPath() : "";
                default -> "";
            };
        }
    }

    private static class ProgressCellRenderer extends DefaultTableCellRenderer {
        private final JProgressBar progressBar = new JProgressBar(0, 100);

        public ProgressCellRenderer() {
            progressBar.setStringPainted(true);
            progressBar.setBackground(KromiumTheme.SURFACE_ELEVATED);
            progressBar.setForeground(Color.WHITE);
            progressBar.setBorder(new EmptyBorder(2, 6, 2, 6));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value instanceof Integer percent) {
                progressBar.setValue(percent);
                progressBar.setString(percent + "%");
                return progressBar;
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }
}
