import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

public class LocalPanel extends JPanel {
    private JLabel pathLabel;
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private File currentDir;

    interface LocalListener {
        void onUpload(File file);
    }

    private LocalListener listener;

    public LocalPanel() {
        setLayout(new BorderLayout(0, 5));
        setBorder(BorderFactory.createTitledBorder("Local Files"));

        pathLabel = new JLabel(" ");
        pathLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        add(pathLabel, BorderLayout.NORTH);
        String[] columns = {"Name", "Size", "Type"};
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        fileTable = new JTable(tableModel);
        fileTable.setRowHeight(20);
        fileTable.setFillsViewportHeight(true);
        fileTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateSelected();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(fileTable);
        add(scrollPane, BorderLayout.CENTER);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> goToParent());

        JButton uploadBtn = new JButton("Upload");
        uploadBtn.addActionListener(e -> uploadSelected());

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshList());

        btnPanel.add(backBtn);
        btnPanel.add(uploadBtn);
        btnPanel.add(refreshBtn);
        add(btnPanel, BorderLayout.SOUTH);

        currentDir = new File(System.getProperty("user.home"));
        refreshList();
    }
    public void refreshList() {
        tableModel.setRowCount(0);
        pathLabel.setText(currentDir.getAbsolutePath());

        File[] files = currentDir.listFiles();
        if (files == null) return;

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File f : files) {
            String name = f.getName();
            String size = f.isFile() ? formatSize(f.length()) : "";
            String type = f.isDirectory() ? "[DIR]" : "[FILE]";
            tableModel.addRow(new Object[]{name, size, type});
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
    private void navigateSelected() {
        int row = fileTable.getSelectedRow();
        if (row < 0) return;
        String name = (String) tableModel.getValueAt(row, 0);
        File target = new File(currentDir, name);
        if (target.isDirectory()) {
            currentDir = target;
            refreshList();
        }
    }

    private void goToParent() {
        File parent = currentDir.getParentFile();
        if (parent != null) {
            currentDir = parent;
            refreshList();
        }
    }

    private void uploadSelected() {
        int row = fileTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                "Select a file to upload", "Info",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = (String) tableModel.getValueAt(row, 0);
        File file = new File(currentDir, name);
        if (file.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                "Cannot upload a directory", "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (listener != null) listener.onUpload(file);
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public void setLocalListener(LocalListener listener) {
        this.listener = listener;
    }
}
