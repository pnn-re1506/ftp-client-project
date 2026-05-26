import javax.swing.*;
import java.awt.*;

public class LogPanel extends JPanel {
    private JTextArea logArea;

    public LogPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Log"));
        setPreferredSize(new Dimension(0, 250));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);
    }
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
