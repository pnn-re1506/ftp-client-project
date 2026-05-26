import java.io.*;
import java.net.*;

public class FTPConnection {
    private Socket controlSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private LogListener logListener;

    interface LogListener {
        void onLog(String message);
    }

    void setLogListener(LogListener listener) {
        this.logListener = listener;
    }

    private void log(String msg) {
        if (logListener != null) {
            logListener.onLog(msg);
        }
    }

    void connect(String host, int port) throws IOException {
        controlSocket = new Socket(host, port);
        controlSocket.setSoTimeout(15000); 
        reader = new BufferedReader(
            new InputStreamReader(controlSocket.getInputStream()));
        writer = new PrintWriter(
            new OutputStreamWriter(controlSocket.getOutputStream()), true);
    }

    String sendCommand(String cmd) throws IOException {
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

    String readResponse() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Connection closed by server");
        }
        sb.append(line);

        if (line.length() >= 4 && line.charAt(3) == '-') {
            String code = line.substring(0, 3);
            while (true) {
                line = reader.readLine();
                if (line == null) break;
                sb.append("\n").append(line);
                if (line.startsWith(code + " ")) break;
            }
        }
        return sb.toString();
    }

    String readWelcome() throws IOException {
        String response = readResponse();
        log("<< " + response);
        return response;
    }

    boolean isConnected() {
        return controlSocket != null && controlSocket.isConnected()
            && !controlSocket.isClosed();
    }
    void disconnect() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (controlSocket != null) controlSocket.close();
        } catch (IOException e) {
        } finally {
            reader = null;
            writer = null;
            controlSocket = null;
        }
    }
}
