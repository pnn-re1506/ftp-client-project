package com.ftpclient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Main window of the FTP Client application (Swing version).
 *
 * Layout:
 * ┌─────────────────────────────────────────────────────────────┐
 * │ TOP: Host / Port / User / Pass / [Connect] [Disconnect]    │
 * ├──────────────────────────┬──────────────────────────────────┤
 * │  LOCAL FILES             │  REMOTE FILES                   │
 * │  path label              │  path label                     │
 * │  ┌──────────────────┐    │  ┌──────────────────────┐       │
 * │  │ file list (JList) │   │  │ file list (JList)     │      │
 * │  └──────────────────┘    │  └──────────────────────┘       │
 * │  [Upload]                │  [Download] [Delete] [New Dir]  │
 * ├──────────────────────────┴──────────────────────────────────┤
 * │ BOTTOM: Log console (JTextArea)                             │
 * └─────────────────────────────────────────────────────────────┘
 */
public class MainWindow extends JFrame {

    // ── FTP client instance ─────────────────────────────────────────────────
    private FTPClient ftpClient;

    // ── TOP: connection fields ──────────────────────────────────────────────
    private JTextField hostField;
    private JTextField portField;
    private JTextField userField;
    private JPasswordField passField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JLabel statusLabel;

    // ── LEFT: local file browser ────────────────────────────────────────────
    private JLabel localPathLabel;
    private DefaultListModel<String> localListModel;
    private JList<String> localFileList;
    private JButton uploadButton;
    private File currentLocalDir;

    // ── RIGHT: remote file browser ──────────────────────────────────────────
    private JLabel remotePathLabel;
    private DefaultListModel<String> remoteListModel;
    private JList<String> remoteFileList;
    private JButton downloadButton;
    private JButton deleteButton;
    private JButton newFolderButton;

    // ── BOTTOM: log console ─────────────────────────────────────────────────
    private JTextArea logArea;

    /**
     * Constructs the main window and initializes all UI components.
     */
    public MainWindow() {
        super("FTP Client");
        ftpClient = new FTPClient();

        // Wire FTP log output to our log console
        ftpClient.setLogConsumer(new java.util.function.Consumer<String>() {
            public void accept(String message) {
                log("[RESP] " + message);
            }
        });

        // Start browsing from user's home directory
        currentLocalDir = new File(System.getProperty("user.home"));

        // Build the UI
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null); // center on screen
        setLayout(new BorderLayout());

        // ── Assemble the three main sections ────────────────────────────────
        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        // ── Load initial local file listing ─────────────────────────────────
        refreshLocalList();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UI CONSTRUCTION
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Builds the top panel with connection fields and buttons.
     */
    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // ── Host field ──────────────────────────────────────────────────────
        panel.add(new JLabel("Host:"));
        hostField = new JTextField("ftp.gnu.org", 14);
        panel.add(hostField);

        // ── Port field ──────────────────────────────────────────────────────
        panel.add(new JLabel("Port:"));
        portField = new JTextField("21", 4);
        panel.add(portField);

        // ── Username field ──────────────────────────────────────────────────
        panel.add(new JLabel("User:"));
        userField = new JTextField("anonymous", 10);
        panel.add(userField);

        // ── Password field ──────────────────────────────────────────────────
        panel.add(new JLabel("Pass:"));
        passField = new JPasswordField(10);
        panel.add(passField);

        // ── Connect button ──────────────────────────────────────────────────
        connectButton = new JButton("Connect");
        connectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onConnect();
            }
        });
        panel.add(connectButton);

        // ── Disconnect button (disabled until connected) ────────────────────
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onDisconnect();
            }
        });
        panel.add(disconnectButton);

        // ── Status label ────────────────────────────────────────────────────
        statusLabel = new JLabel("  Disconnected");
        statusLabel.setForeground(Color.RED);
        panel.add(statusLabel);

        return panel;
    }

    /**
     * Builds the center panel with local (left) and remote (right) file browsers,
     * split by a JSplitPane at 50/50 ratio.
     */
    private JSplitPane buildCenterPanel() {
        JSplitPane splitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildLocalPanel(),
            buildRemotePanel()
        );
        splitPane.setDividerLocation(0.5);
        splitPane.setResizeWeight(0.5); // both sides resize equally
        return splitPane;
    }

    /**
     * Builds the left panel: local file browser.
     */
    private JPanel buildLocalPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 4));

        // ── NORTH: header with path ─────────────────────────────────────────
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Local Files");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        localPathLabel = new JLabel(" ");
        localPathLabel.setFont(localPathLabel.getFont().deriveFont(Font.PLAIN, 11f));
        headerPanel.add(localPathLabel, BorderLayout.SOUTH);

        panel.add(headerPanel, BorderLayout.NORTH);

        // ── CENTER: file list ───────────────────────────────────────────────
        localListModel = new DefaultListModel<String>();
        localFileList = new JList<String>(localListModel);
        localFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double-click to navigate into directories
        localFileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onLocalDoubleClick();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(localFileList);
        panel.add(scrollPane, BorderLayout.CENTER);

        // ── SOUTH: action buttons ───────────────────────────────────────────
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

        uploadButton = new JButton("Upload");
        uploadButton.setEnabled(false); // disabled until connected
        uploadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onUpload();
            }
        });
        buttonPanel.add(uploadButton);

        // Refresh local list button
        JButton localRefreshButton = new JButton("Refresh");
        localRefreshButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refreshLocalList();
            }
        });
        buttonPanel.add(localRefreshButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Builds the right panel: remote file browser.
     */
    private JPanel buildRemotePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 8));

        // ── NORTH: header with path ─────────────────────────────────────────
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Remote Files");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        remotePathLabel = new JLabel(" ");
        remotePathLabel.setFont(remotePathLabel.getFont().deriveFont(Font.PLAIN, 11f));
        headerPanel.add(remotePathLabel, BorderLayout.SOUTH);

        panel.add(headerPanel, BorderLayout.NORTH);

        // ── CENTER: file list ───────────────────────────────────────────────
        remoteListModel = new DefaultListModel<String>();
        remoteFileList = new JList<String>(remoteListModel);
        remoteFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double-click: navigate into directory or show info for files
        remoteFileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onRemoteDoubleClick();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(remoteFileList);
        panel.add(scrollPane, BorderLayout.CENTER);

        // ── SOUTH: action buttons ───────────────────────────────────────────
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

        downloadButton = new JButton("Download");
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onDownload();
            }
        });
        buttonPanel.add(downloadButton);

        deleteButton = new JButton("Delete");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onDelete();
            }
        });
        buttonPanel.add(deleteButton);

        newFolderButton = new JButton("New Folder");
        newFolderButton.setEnabled(false);
        newFolderButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onNewFolder();
            }
        });
        buttonPanel.add(newFolderButton);

        // Refresh remote list button
        JButton remoteRefreshButton = new JButton("Refresh");
        remoteRefreshButton.setEnabled(false);
        remoteRefreshButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refreshRemoteList();
            }
        });
        buttonPanel.add(remoteRefreshButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Builds the bottom panel: scrollable log console.
     */
    private JScrollPane buildBottomPanel() {
        logArea = new JTextArea(8, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Command Log"));
        return scrollPane;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CONNECTION HANDLERS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Handles the Connect button click.
     * Reads connection fields, connects to FTP server, and logs in.
     * Runs on a background thread so the UI doesn't freeze.
     */
    private void onConnect() {
        // Read connection parameters from the text fields
        final String host = hostField.getText().trim();
        final String portText = portField.getText().trim();
        final String user = userField.getText().trim();
        final String pass = new String(passField.getPassword());

        // Validate host
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a host.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validate port
        final int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Disable connect button while connecting
        connectButton.setEnabled(false);
        statusLabel.setText("  Connecting...");
        statusLabel.setForeground(Color.ORANGE);
        log("[CMD]  Connecting to " + host + ":" + port + "...");

        // Run connection on a background thread to keep the UI responsive
        Thread connectThread = new Thread(new Runnable() {
            public void run() {
                try {
                    // Step 1: Open control connection to the FTP server
                    ftpClient.connect(host, port);

                    // Step 2: Authenticate with USER + PASS
                    ftpClient.login(user, pass);

                    // Step 3: Update UI on the EDT after successful connection
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            setConnectedState(true);
                            refreshRemoteList();
                        }
                    });

                } catch (final IOException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[ERROR] Connection failed: " + ex.getMessage());
                            JOptionPane.showMessageDialog(
                                MainWindow.this,
                                "Connection failed:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            );
                            setConnectedState(false);
                        }
                    });
                }
            }
        });
        connectThread.start();
    }

    /**
     * Handles the Disconnect button click.
     * Sends QUIT and resets the UI.
     */
    private void onDisconnect() {
        log("[CMD]  Disconnecting...");

        Thread disconnectThread = new Thread(new Runnable() {
            public void run() {
                try {
                    ftpClient.quit();
                } catch (IOException ex) {
                    log("[ERROR] " + ex.getMessage());
                }

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        setConnectedState(false);
                        remoteListModel.clear();
                        remotePathLabel.setText(" ");
                    }
                });
            }
        });
        disconnectThread.start();
    }

    /**
     * Updates button enable/disable states and status label based on connection state.
     */
    private void setConnectedState(boolean connected) {
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
        uploadButton.setEnabled(connected);
        downloadButton.setEnabled(connected);
        deleteButton.setEnabled(connected);
        newFolderButton.setEnabled(connected);

        if (connected) {
            statusLabel.setText("  Connected");
            statusLabel.setForeground(new Color(0, 150, 0)); // green
        } else {
            statusLabel.setText("  Disconnected");
            statusLabel.setForeground(Color.RED);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LOCAL FILE BROWSER
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Refreshes the local file list to show contents of currentLocalDir.
     * Directories are shown with a trailing "/" and listed before files.
     */
    private void refreshLocalList() {
        if (currentLocalDir == null || !currentLocalDir.exists()) {
            return;
        }

        localPathLabel.setText(currentLocalDir.getAbsolutePath());
        localListModel.clear();

        // Add ".." entry to go to the parent directory
        if (currentLocalDir.getParentFile() != null) {
            localListModel.addElement("[DIR] ..");
        }

        // Get and sort the file listing: directories first, then alphabetically
        File[] files = currentLocalDir.listFiles();
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                public int compare(File a, File b) {
                    if (a.isDirectory() && !b.isDirectory()) {
                        return -1;
                    }
                    if (!a.isDirectory() && b.isDirectory()) {
                        return 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });

            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file.isDirectory()) {
                    localListModel.addElement("[DIR] " + file.getName());
                } else {
                    localListModel.addElement(file.getName() + "  (" + formatSize(file.length()) + ")");
                }
            }
        }
    }

    /**
     * Handles double-click on local file list.
     * If a directory is clicked, navigates into it.
     * If a file is clicked, shows a message to use the Upload button.
     */
    private void onLocalDoubleClick() {
        String selected = localFileList.getSelectedValue();
        if (selected == null) {
            return;
        }

        // Navigate to parent directory
        if (selected.equals("[DIR] ..")) {
            currentLocalDir = currentLocalDir.getParentFile();
            refreshLocalList();
            return;
        }

        // Navigate into a subdirectory
        if (selected.startsWith("[DIR] ")) {
            String dirName = selected.substring(6); // remove "[DIR] " prefix
            File newDir = new File(currentLocalDir, dirName);
            if (newDir.isDirectory()) {
                currentLocalDir = newDir;
                refreshLocalList();
            }
            return;
        }

        // It's a file — prompt user to use Upload button
        JOptionPane.showMessageDialog(
            this,
            "Select the file and click Upload to send it to the server.",
            "Info",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    // REMOTE FILE BROWSER
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Refreshes the remote file list by sending PWD and LIST commands.
     * Parses the Unix-style directory listing from the FTP server.
     * Runs on a background thread.
     */
    private void refreshRemoteList() {
        if (!ftpClient.isConnected()) {
            return;
        }

        log("[CMD]  PWD");
        log("[CMD]  LIST");

        Thread listThread = new Thread(new Runnable() {
            public void run() {
                try {
                    // Get the current remote directory path
                    final String remotePath = ftpClient.pwd();

                    // Get the raw directory listing
                    String rawListing = ftpClient.list();

                    // Parse the listing into display items
                    final DefaultListModel<String> newModel = new DefaultListModel<String>();
                    newModel.addElement("[DIR] ..");

                    String[] lines = rawListing.split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i].trim();
                        if (line.isEmpty()) {
                            continue;
                        }

                        // Unix-style listing:
                        //   drwxr-xr-x  2 user group  4096 Jan  1 12:00 dirname
                        //   -rw-r--r--  1 user group  1234 Jan  1 12:00 file.txt
                        if (line.startsWith("d")) {
                            // Directory entry
                            String name = extractListingName(line);
                            if (!name.equals(".") && !name.equals("..")) {
                                newModel.addElement("[DIR] " + name);
                            }
                        } else if (line.startsWith("-") || line.startsWith("l")) {
                            // File entry or symbolic link
                            String name = extractListingName(line);
                            String size = extractListingSize(line);
                            newModel.addElement(name + "  (" + size + ")");
                        } else if (line.contains("<DIR>")) {
                            // Windows IIS format: "01-01-26  12:00AM       <DIR>  dirname"
                            String name = line.substring(line.lastIndexOf('>') + 1).trim();
                            if (!name.equals(".") && !name.equals("..")) {
                                newModel.addElement("[DIR] " + name);
                            }
                        } else {
                            // Unknown format — show the raw line
                            newModel.addElement(line);
                        }
                    }

                    // Update the UI on the EDT
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            remotePathLabel.setText(remotePath);
                            remoteListModel.clear();
                            for (int j = 0; j < newModel.size(); j++) {
                                remoteListModel.addElement(newModel.get(j));
                            }
                        }
                    });

                } catch (final IOException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[ERROR] Failed to list remote files: " + ex.getMessage());
                            JOptionPane.showMessageDialog(
                                MainWindow.this,
                                "Failed to list remote files:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });
                }
            }
        });
        listThread.start();
    }

    /**
     * Handles double-click on remote file list.
     * If a directory is clicked, navigates into it via CWD.
     * If a file is clicked, shows a message to use the Download button.
     */
    private void onRemoteDoubleClick() {
        String selected = remoteFileList.getSelectedValue();
        if (selected == null) {
            return;
        }

        // Navigate to parent directory
        if (selected.equals("[DIR] ..")) {
            log("[CMD]  CWD ..");
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        ftpClient.cwd("..");
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                refreshRemoteList();
                            }
                        });
                    } catch (final IOException ex) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                log("[ERROR] " + ex.getMessage());
                            }
                        });
                    }
                }
            });
            thread.start();
            return;
        }

        // Navigate into a subdirectory
        if (selected.startsWith("[DIR] ")) {
            final String dirName = selected.substring(6);
            log("[CMD]  CWD " + dirName);
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        ftpClient.cwd(dirName);
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                refreshRemoteList();
                            }
                        });
                    } catch (final IOException ex) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                log("[ERROR] " + ex.getMessage());
                                JOptionPane.showMessageDialog(
                                    MainWindow.this,
                                    "Cannot enter directory:\n" + ex.getMessage(),
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE
                                );
                            }
                        });
                    }
                }
            });
            thread.start();
            return;
        }

        // It's a file — show info message
        JOptionPane.showMessageDialog(
            this,
            "Select the file and click Download to save it locally.",
            "Info",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FILE TRANSFER OPERATIONS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Uploads the selected local file to the remote server.
     * Uses TYPE I (binary) + PASV + STOR via FTPClient.upload().
     */
    private void onUpload() {
        if (!ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Not connected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selected = localFileList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a file to upload.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Don't upload directories
        if (selected.startsWith("[DIR]")) {
            JOptionPane.showMessageDialog(this, "Cannot upload a directory. Select a file.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Extract the filename (remove size suffix)
        final String fileName = extractDisplayName(selected);
        final String localPath = new File(currentLocalDir, fileName).getAbsolutePath();

        log("[CMD]  STOR " + fileName);

        Thread uploadThread = new Thread(new Runnable() {
            public void run() {
                try {
                    ftpClient.upload(localPath, fileName);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[CMD]  Upload complete: " + fileName);
                            refreshRemoteList();
                        }
                    });
                } catch (final IOException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[ERROR] Upload failed: " + ex.getMessage());
                            JOptionPane.showMessageDialog(
                                MainWindow.this,
                                "Upload failed:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });
                }
            }
        });
        uploadThread.start();
    }

    /**
     * Downloads the selected remote file to the current local directory.
     * Uses TYPE I (binary) + PASV + RETR via FTPClient.download().
     */
    private void onDownload() {
        if (!ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Not connected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selected = remoteFileList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a file to download.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Don't download directories
        if (selected.startsWith("[DIR]")) {
            JOptionPane.showMessageDialog(this, "Cannot download a directory. Select a file.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Extract the filename (remove size suffix)
        final String fileName = extractDisplayName(selected);
        final String localPath = new File(currentLocalDir, fileName).getAbsolutePath();

        log("[CMD]  RETR " + fileName);

        Thread downloadThread = new Thread(new Runnable() {
            public void run() {
                try {
                    ftpClient.download(fileName, localPath);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[CMD]  Download complete: " + fileName);
                            refreshLocalList();
                        }
                    });
                } catch (final IOException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[ERROR] Download failed: " + ex.getMessage());
                            JOptionPane.showMessageDialog(
                                MainWindow.this,
                                "Download failed:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });
                }
            }
        });
        downloadThread.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // REMOTE FILE MANAGEMENT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Deletes the selected remote file after confirmation.
     * Uses DELE command via FTPClient.delete().
     */
    private void onDelete() {
        if (!ftpClient.isConnected()) {
            return;
        }

        String selected = remoteFileList.getSelectedValue();
        if (selected == null || selected.equals("[DIR] ..")) {
            JOptionPane.showMessageDialog(this, "Select a file to delete.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        final String name = extractDisplayName(selected);
        final boolean isDir = selected.startsWith("[DIR] ");

        // Ask for confirmation before deleting
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Delete '" + name + "' from the server?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        log("[CMD]  " + (isDir ? "RMD " : "DELE ") + name);

        Thread deleteThread = new Thread(new Runnable() {
            public void run() {
                try {
                    if (isDir) {
                        ftpClient.rmdir(name);
                    } else {
                        ftpClient.delete(name);
                    }
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[CMD]  Deleted: " + name);
                            refreshRemoteList();
                        }
                    });
                } catch (final IOException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[ERROR] Delete failed: " + ex.getMessage());
                            JOptionPane.showMessageDialog(
                                MainWindow.this,
                                "Delete failed:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });
                }
            }
        });
        deleteThread.start();
    }

    /**
     * Creates a new folder on the remote server.
     * Prompts for the folder name via JOptionPane, then sends MKD command.
     */
    private void onNewFolder() {
        if (!ftpClient.isConnected()) {
            return;
        }

        // Prompt for folder name
        final String folderName = JOptionPane.showInputDialog(
            this,
            "Enter new folder name:",
            "New Folder",
            JOptionPane.PLAIN_MESSAGE
        );

        if (folderName == null || folderName.trim().isEmpty()) {
            return;
        }

        log("[CMD]  MKD " + folderName.trim());

        Thread mkdThread = new Thread(new Runnable() {
            public void run() {
                try {
                    ftpClient.mkdir(folderName.trim());
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[CMD]  Folder created: " + folderName.trim());
                            refreshRemoteList();
                        }
                    });
                } catch (final IOException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            log("[ERROR] MKD failed: " + ex.getMessage());
                            JOptionPane.showMessageDialog(
                                MainWindow.this,
                                "Failed to create folder:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            );
                        }
                    });
                }
            }
        });
        mkdThread.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Appends a timestamped message to the log console.
     * Auto-scrolls to the bottom so the latest message is visible.
     */
    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";

        if (SwingUtilities.isEventDispatchThread()) {
            logArea.append(logLine);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        } else {
            final String line = logLine;
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    logArea.append(line);
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }
            });
        }
    }

    /**
     * Extracts the file/directory name from a display string.
     *
     * Input formats:
     *   "[DIR] dirname"            → "dirname"
     *   "filename.txt  (1.2 KB)"   → "filename.txt"
     */
    private String extractDisplayName(String displayItem) {
        String name = displayItem;

        // Remove "[DIR] " prefix if present
        if (name.startsWith("[DIR] ")) {
            name = name.substring(6);
        }

        // Remove size suffix "  (1.2 KB)" if present
        int sizeStart = name.lastIndexOf("  (");
        if (sizeStart > 0) {
            name = name.substring(0, sizeStart);
        }

        return name.trim();
    }

    /**
     * Extracts the file name from a Unix-style ls -l listing line.
     *
     * Format: "drwxr-xr-x 2 user group 4096 Jan  1 12:00 filename"
     * The name starts after the 8th whitespace-delimited field.
     */
    private String extractListingName(String line) {
        // Split into at most 9 parts to preserve filenames with spaces
        String[] parts = line.split("\\s+", 9);
        if (parts.length >= 9) {
            return parts[8];
        }

        // Fallback: return the last token
        String[] tokens = line.trim().split("\\s+");
        return tokens[tokens.length - 1];
    }

    /**
     * Extracts the file size from a Unix-style ls -l listing line.
     * The size is typically in the 5th field.
     */
    private String extractListingSize(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 5) {
            try {
                long size = Long.parseLong(parts[4]);
                return formatSize(size);
            } catch (NumberFormatException ex) {
                return "?";
            }
        }
        return "?";
    }

    /**
     * Formats a byte count into a human-readable size string.
     * Examples: 512 → "512 B", 1536 → "1.5 KB", 2097152 → "2.0 MB"
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
