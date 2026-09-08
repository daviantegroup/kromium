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

    public DownloadManagerDialog(Frame owner) {
        super(owner, "Downloads — " + KromiumSwingApp.APP_NAME, false);
        setIconImages(KromiumSwingApp.getAppIcons());
        setSize(720, 440);
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

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(KromiumTheme.BACKGROUND);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, KromiumTheme.BORDER_SUBTLE));
        add(scrollPane, BorderLayout.CENTER);

        // Action Toolbar
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        actionPanel.setBackground(KromiumTheme.SURFACE);

        JButton pauseBtn = createActionButton("Pause");
        JButton resumeBtn = createActionButton("Resume");
        JButton cancelBtn = createActionButton("Cancel");
        JButton openFolderBtn = createActionButton("Open in Folder");
        JButton clearBtn = createActionButton("Clear Finished");

        pauseBtn.addActionListener(e -> {
            DownloadEntry entry = getSelectedEntry();
            if (entry != null && entry.isInProgress() && !entry.isPaused()) {
                Kromium.pauseDownload(entry.getId());
            }
        });

        resumeBtn.addActionListener(e -> {
            DownloadEntry entry = getSelectedEntry();
            if (entry != null && entry.isPaused()) {
                Kromium.resumeDownload(entry.getId());
            }
        });

        cancelBtn.addActionListener(e -> {
            DownloadEntry entry = getSelectedEntry();
            if (entry != null && (entry.isInProgress() || entry.isPaused())) {
                Kromium.cancelDownload(entry.getId());
            }
        });

        openFolderBtn.addActionListener(e -> {
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
        });

        clearBtn.addActionListener(e -> {
            synchronized (downloadList) {
                downloadList.removeIf(d -> d.isComplete() || d.isCanceled());
                downloadMap.entrySet().removeIf(entry -> entry.getValue().isComplete() || entry.getValue().isCanceled());
                tableModel.fireTableDataChanged();
                updateCount();
            }
        });

        actionPanel.add(pauseBtn);
        actionPanel.add(resumeBtn);
        actionPanel.add(cancelBtn);
        actionPanel.add(openFolderBtn);
        actionPanel.add(clearBtn);
        add(actionPanel, BorderLayout.SOUTH);
    }

    public void updateDownload(KromiumDownloadItem item) {
        SwingUtilities.invokeLater(() -> {
            synchronized (downloadList) {
                DownloadEntry entry = downloadMap.get(item.getId());
                if (entry == null) {
                    entry = new DownloadEntry(item);
                    downloadMap.put(item.getId(), entry);
                    downloadList.add(0, entry);
                } else {
                    entry.update(item);
                }
                tableModel.fireTableDataChanged();
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
        return null;
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
