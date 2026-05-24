import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;

// MainFrame - Main Swing window that ties all panels together
public class MainFrame extends JFrame {
    private FTPClient ftpClient;
    private LoginPanel loginPanel;
    private LocalPanel localPanel;
    private RemotePanel remotePanel;
    private LogPanel logPanel;

    public MainFrame() {
        super("FTP Client");
        ftpClient = new FTPClient();
        initUI();
        setupListeners();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
    }

    // Initialize UI components
    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        loginPanel = new LoginPanel();
        localPanel = new LocalPanel();
        remotePanel = new RemotePanel();
        logPanel = new LogPanel();

        // Split pane so local and remote panels are equal width
        JSplitPane splitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT, localPanel, remotePanel);
        splitPane.setResizeWeight(0.5); // equal space
        splitPane.setDividerLocation(0.5);

        add(loginPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(logPanel, BorderLayout.SOUTH);

        // Connect FTP client logging to log panel
        ftpClient.setLogListener(msg -> logPanel.appendLog(msg));
    }

    // Wire up all panel callbacks
    private void setupListeners() {
        setupLoginListener();
        setupLocalListener();
        setupRemoteListener();
    }

    // Handle login connect/disconnect
    private void setupLoginListener() {
        loginPanel.setLoginListener(new LoginPanel.LoginListener() {
            public void onConnect(String host, int port, String user, String pass) {
                // Run in background thread to avoid freezing UI
                new Thread(() -> {
                    try {
                        logPanel.appendLog("--- Connecting to " + host + ":" + port + " ---");
                        ftpClient.connect(host, port);
                        ftpClient.login(user, pass);
                        String path = ftpClient.pwd();
                        java.util.List<String> files = ftpClient.list();

                        // Update UI on EDT
                        SwingUtilities.invokeLater(() -> {
                            loginPanel.setConnected(true);
                            remotePanel.updateListing(files, path);
                            logPanel.appendLog("--- Connected successfully ---");
                        });
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            logPanel.appendLog("ERROR: " + e.getMessage());
                            JOptionPane.showMessageDialog(MainFrame.this,
                                "Connection failed: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }).start();
            }

            public void onDisconnect() {
                new Thread(() -> {
                    ftpClient.quit();
                    SwingUtilities.invokeLater(() -> {
                        loginPanel.setConnected(false);
                        logPanel.appendLog("--- Disconnected ---");
                    });
                }).start();
            }
        });
    }

    // Handle local panel upload
    private void setupLocalListener() {
        localPanel.setLocalListener(new LocalPanel.LocalListener() {
            public void onUpload(File file) {
                if (!ftpClient.isConnected()) {
                    showError("Not connected to server");
                    return;
                }
                new Thread(() -> {
                    try {
                        logPanel.appendLog("Uploading: " + file.getName());
                        ftpClient.upload(file.getAbsolutePath(), file.getName());
                        logPanel.appendLog("Upload complete: " + file.getName());
                        refreshRemote();
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            logPanel.appendLog("Upload ERROR: " + e.getMessage());
                            showError("Upload failed: " + e.getMessage());
                        });
                    }
                }).start();
            }
        });
    }

    // Handle remote panel actions
    private void setupRemoteListener() {
        remotePanel.setRemoteListener(new RemotePanel.RemoteListener() {
            public void onChangeDir(String path) {
                if (!ftpClient.isConnected()) return;
                new Thread(() -> {
                    try {
                        ftpClient.cwd(path);
                        String newPath = ftpClient.pwd();
                        java.util.List<String> files = ftpClient.list();
                        SwingUtilities.invokeLater(() ->
                            remotePanel.updateListing(files, newPath));
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            logPanel.appendLog("CWD ERROR: " + e.getMessage());
                            showError("Cannot change directory: " + e.getMessage());
                        });
                    }
                }).start();
            }

            public void onDownload(String filename) {
                if (!ftpClient.isConnected()) return;
                File localDir = localPanel.getCurrentDir();
                String localPath = new File(localDir, filename).getAbsolutePath();
                new Thread(() -> {
                    try {
                        logPanel.appendLog("Downloading: " + filename);
                        ftpClient.download(filename, localPath);
                        logPanel.appendLog("Download complete: " + filename);
                        SwingUtilities.invokeLater(() -> localPanel.refreshList());
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            logPanel.appendLog("Download ERROR: " + e.getMessage());
                            showError("Download failed: " + e.getMessage());
                        });
                    }
                }).start();
            }

            public void onDelete(String filename) {
                if (!ftpClient.isConnected()) return;
                new Thread(() -> {
                    try {
                        ftpClient.delete(filename);
                        logPanel.appendLog("Deleted: " + filename);
                        refreshRemote();
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            logPanel.appendLog("Delete ERROR: " + e.getMessage());
                            showError("Delete failed: " + e.getMessage());
                        });
                    }
                }).start();
            }

            public void onMkdir(String dirName) {
                if (!ftpClient.isConnected()) return;
                new Thread(() -> {
                    try {
                        ftpClient.mkdir(dirName);
                        logPanel.appendLog("Created directory: " + dirName);
                        refreshRemote();
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            logPanel.appendLog("MkDir ERROR: " + e.getMessage());
                            showError("MkDir failed: " + e.getMessage());
                        });
                    }
                }).start();
            }

            public void onRmdir(String dirName) {
                if (!ftpClient.isConnected()) return;
                new Thread(() -> {
                    try {
                        ftpClient.rmdir(dirName);
                        logPanel.appendLog("Removed directory: " + dirName);
                        refreshRemote();
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            logPanel.appendLog("RmDir ERROR: " + e.getMessage());
                            showError("RmDir failed: " + e.getMessage());
                        });
                    }
                }).start();
            }

            public void onRefresh() {
                refreshRemote();
            }
        });
    }

    // Refresh remote file listing
    private void refreshRemote() {
        if (!ftpClient.isConnected()) return;
        new Thread(() -> {
            try {
                String path = ftpClient.pwd();
                java.util.List<String> files = ftpClient.list();
                SwingUtilities.invokeLater(() ->
                    remotePanel.updateListing(files, path));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                    logPanel.appendLog("Refresh ERROR: " + e.getMessage()));
            }
        }).start();
    }

    // Show error dialog
    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error",
            JOptionPane.ERROR_MESSAGE);
    }

    // Application entry point
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
