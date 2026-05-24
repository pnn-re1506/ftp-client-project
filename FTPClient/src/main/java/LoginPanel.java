import javax.swing.*;
import java.awt.*;

// LoginPanel - Top panel with host, port, user, pass, connect button
public class LoginPanel extends JPanel {
    private JTextField hostField;
    private JTextField portField;
    private JTextField userField;
    private JPasswordField passField;
    private JButton connectBtn;
    private boolean connected = false;

    // Callback for connect/disconnect actions
    interface LoginListener {
        void onConnect(String host, int port, String user, String pass);
        void onDisconnect();
    }

    private LoginListener listener;

    public LoginPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 8));
        setBorder(BorderFactory.createTitledBorder("Connection"));

        // Host field
        add(new JLabel("Host:"));
        hostField = new JTextField("", 14);
        add(hostField);

        // Port field
        add(new JLabel("Port:"));
        portField = new JTextField("21", 4);
        add(portField);

        // Username field
        add(new JLabel("Username:"));
        userField = new JTextField("", 10);
        add(userField);

        // Password field
        add(new JLabel("Password:"));
        passField = new JPasswordField("", 10);
        add(passField);

        // Connect button
        connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e -> handleConnectClick());
        add(connectBtn);
    }

    // Handle connect/disconnect button click
    private void handleConnectClick() {
        if (listener == null) return;
        if (!connected) {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                String host = hostField.getText().trim();
                String user = userField.getText().trim();
                String pass = new String(passField.getPassword());
                listener.onConnect(host, port, user, pass);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                    "Invalid port number", "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } else {
            listener.onDisconnect();
        }
    }

    // Update button state after connect/disconnect
    public void setConnected(boolean connected) {
        this.connected = connected;
        connectBtn.setText(connected ? "Disconnect" : "Connect");
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        userField.setEnabled(!connected);
        passField.setEnabled(!connected);
    }

    public void setLoginListener(LoginListener listener) {
        this.listener = listener;
    }
}
