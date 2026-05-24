import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

// RemotePanel - Center panel showing remote FTP file browser
public class RemotePanel extends JPanel {
    private JLabel pathLabel;
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private String currentPath = "/";

    // Callbacks for remote file operations
    interface RemoteListener {
        void onChangeDir(String path);
        void onDownload(String filename);
        void onDelete(String filename);
        void onMkdir(String dirName);
        void onRmdir(String dirName);
        void onRefresh();
    }

    private RemoteListener listener;

    public RemotePanel() {
        setLayout(new BorderLayout(0, 5));
        setBorder(BorderFactory.createTitledBorder("Remote Files"));

        // Path label at top
        pathLabel = new JLabel("/");
        pathLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        add(pathLabel, BorderLayout.NORTH);

        // File table with columns
        String[] columns = {"Name", "Size", "Type", "Date"};
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        fileTable = new JTable(tableModel);
        fileTable.setRowHeight(20);
        fileTable.setFillsViewportHeight(true);

        // Double-click to navigate into directory
        fileTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateSelected();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(fileTable);
        add(scrollPane, BorderLayout.CENTER);

        // Button panel at bottom
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> goToParent());

        JButton downloadBtn = new JButton("Download");
        downloadBtn.addActionListener(e -> downloadSelected());

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton mkdirBtn = new JButton("New Folder");
        mkdirBtn.addActionListener(e -> makeDirectory());

        JButton rmdirBtn = new JButton("Remove Folder");
        rmdirBtn.addActionListener(e -> removeDirectory());

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> {
            if (listener != null) listener.onRefresh();
        });

        btnPanel.add(backBtn);
        btnPanel.add(downloadBtn);
        btnPanel.add(deleteBtn);
        btnPanel.add(mkdirBtn);
        btnPanel.add(rmdirBtn);
        btnPanel.add(refreshBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    // Update the file listing from raw LIST output
    public void updateListing(java.util.List<String> lines, String path) {
        tableModel.setRowCount(0);
        currentPath = path;
        pathLabel.setText(path);

        for (String line : lines) {
            String[] parsed = parseListLine(line);
            if (parsed != null) {
                tableModel.addRow(parsed);
            }
        }
    }

    // Parse one line of Unix-style LIST output
    // Format: drwxr-xr-x  2 user group  4096 Jan 01 12:00 filename
    private String[] parseListLine(String line) {
        if (line == null || line.length() < 10) return null;

        String type = line.charAt(0) == 'd' ? "[DIR]" : "[FILE]";

        // Split by whitespace, filename is everything after the 8th token
        String[] tokens = line.trim().split("\\s+", 9);
        if (tokens.length < 9) return null;

        String name = tokens[8];
        String size = type.equals("[DIR]") ? "" : formatSize(tokens[4]);
        String date = tokens[5] + " " + tokens[6] + " " + tokens[7];

        // Skip . and .. entries
        if (name.equals(".") || name.equals("..")) return null;

        return new String[]{name, size, type, date};
    }

    // Format size string to human-readable
    private String formatSize(String sizeStr) {
        try {
            long bytes = Long.parseLong(sizeStr);
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } catch (NumberFormatException e) {
            return sizeStr;
        }
    }

    // Navigate into selected directory
    private void navigateSelected() {
        int row = fileTable.getSelectedRow();
        if (row < 0) return;
        String type = (String) tableModel.getValueAt(row, 2);
        String name = (String) tableModel.getValueAt(row, 0);
        if ("[DIR]".equals(type) && listener != null) {
            listener.onChangeDir(name);
        }
    }

    // Go to parent directory
    private void goToParent() {
        if (listener != null) listener.onChangeDir("..");
    }

    // Download selected file
    private void downloadSelected() {
        int row = fileTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                "Select a file to download", "Info",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String type = (String) tableModel.getValueAt(row, 2);
        if ("[DIR]".equals(type)) {
            JOptionPane.showMessageDialog(this,
                "Cannot download a directory", "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        String name = (String) tableModel.getValueAt(row, 0);
        if (listener != null) listener.onDownload(name);
    }

    // Delete selected file
    private void deleteSelected() {
        int row = fileTable.getSelectedRow();
        if (row < 0) return;
        String name = (String) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete '" + name + "'?", "Confirm Delete",
            JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION && listener != null) {
            listener.onDelete(name);
        }
    }

    // Create new directory
    private void makeDirectory() {
        String name = JOptionPane.showInputDialog(this,
            "Directory name:", "New Directory",
            JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty() && listener != null) {
            listener.onMkdir(name.trim());
        }
    }

    // Remove selected directory
    private void removeDirectory() {
        int row = fileTable.getSelectedRow();
        if (row < 0) return;
        String name = (String) tableModel.getValueAt(row, 0);
        String type = (String) tableModel.getValueAt(row, 2);
        if (!"[DIR]".equals(type)) {
            JOptionPane.showMessageDialog(this,
                "Selected item is not a directory", "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove directory '" + name + "'?", "Confirm",
            JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION && listener != null) {
            listener.onRmdir(name);
        }
    }

    // Get current remote path
    public String getCurrentPath() {
        return currentPath;
    }

    public void setRemoteListener(RemoteListener listener) {
        this.listener = listener;
    }
}
