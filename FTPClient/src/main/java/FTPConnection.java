import java.io.*;
import java.net.*;

// FTPConnection - Manages control socket, reader, writer for FTP
public class FTPConnection {
    private Socket controlSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private LogListener logListener;

    // Listener interface so GUI can receive log messages
    interface LogListener {
        void onLog(String message);
    }

    void setLogListener(LogListener listener) {
        this.logListener = listener;
    }

    // Log a message to the listener
    private void log(String msg) {
        if (logListener != null) {
            logListener.onLog(msg);
        }
    }

    // Open control connection to FTP server
    void connect(String host, int port) throws IOException {
        controlSocket = new Socket(host, port);
        controlSocket.setSoTimeout(15000); // 15s timeout
        reader = new BufferedReader(
            new InputStreamReader(controlSocket.getInputStream()));
        writer = new PrintWriter(
            new OutputStreamWriter(controlSocket.getOutputStream()), true);
    }

    // Send a command and return the full response
    String sendCommand(String cmd) throws IOException {
        // Mask password in logs
        if (cmd.startsWith("PASS ")) {
            log(">> PASS ****");
        } else {
            log(">> " + cmd);
        }
        writer.println(cmd);
        String response = readResponse();
        log("<< " + response);
        return response;
    }

    // Read response, handling multiline replies (e.g. "220-..." until "220 ")
    String readResponse() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Connection closed by server");
        }
        sb.append(line);

        // Check for multiline: "XYZ-" means more lines follow
        if (line.length() >= 4 && line.charAt(3) == '-') {
            String code = line.substring(0, 3);
            // Read until we find "XYZ " (code + space)
            while (true) {
                line = reader.readLine();
                if (line == null) break;
                sb.append("\n").append(line);
                if (line.startsWith(code + " ")) break;
            }
        }
        return sb.toString();
    }

    // Read the initial welcome message after connecting
    String readWelcome() throws IOException {
        String response = readResponse();
        log("<< " + response);
        return response;
    }

    // Check if control socket is connected
    boolean isConnected() {
        return controlSocket != null && controlSocket.isConnected()
            && !controlSocket.isClosed();
    }

    // Close control connection
    void disconnect() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (controlSocket != null) controlSocket.close();
        } catch (IOException e) {
            // ignore on close
        } finally {
            reader = null;
            writer = null;
            controlSocket = null;
        }
    }
}
