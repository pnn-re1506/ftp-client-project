import java.io.*;
import java.net.*;
import java.util.*;

// FTPClient - High-level FTP operations using FTPConnection
public class FTPClient {
    private FTPConnection connection;

    FTPClient() {
        connection = new FTPConnection();
    }

    // Set log listener for GUI logging
    void setLogListener(FTPConnection.LogListener listener) {
        connection.setLogListener(listener);
    }

    // Connect to FTP server and read welcome message
    void connect(String host, int port) throws IOException, FTPException {
        connection.connect(host, port);
        String welcome = connection.readWelcome();
        FTPResponse res = FTPResponse.parse(welcome);
        if (res.isError()) {
            throw new FTPException(res.code, res.message);
        }
    }

    // Login with username and password
    void login(String user, String pass) throws IOException, FTPException {
        String res = connection.sendCommand("USER " + user);
        FTPResponse userRes = FTPResponse.parse(res);
        // 331 = password required, 230 = already logged in
        if (userRes.code == 331) {
            res = connection.sendCommand("PASS " + pass);
            FTPResponse passRes = FTPResponse.parse(res);
            if (passRes.isError()) {
                throw new FTPException(passRes.code, passRes.message);
            }
        } else if (userRes.isError()) {
            throw new FTPException(userRes.code, userRes.message);
        }
    }

    // Get current working directory
    String pwd() throws IOException, FTPException {
        String res = connection.sendCommand("PWD");
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
        // Extract path from quotes: 257 "/path" ...
        String msg = r.message;
        int start = msg.indexOf('"');
        int end = msg.indexOf('"', start + 1);
        if (start >= 0 && end > start) {
            return msg.substring(start + 1, end);
        }
        return msg;
    }

    // Change remote directory
    void cwd(String path) throws IOException, FTPException {
        String res = connection.sendCommand("CWD " + path);
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    // Set binary transfer mode
    private void setBinaryMode() throws IOException, FTPException {
        String res = connection.sendCommand("TYPE I");
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    // Parse PASV response and open data connection
    Socket openPassiveConnection() throws IOException, FTPException {
        String res = connection.sendCommand("PASV");
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);

        // Parse (h1,h2,h3,h4,p1,p2)
        String msg = r.message;
        int start = msg.indexOf('(');
        int end = msg.indexOf(')');
        if (start < 0 || end < 0) {
            throw new FTPException("Cannot parse PASV response: " + msg);
        }

        String[] parts = msg.substring(start + 1, end).split(",");
        if (parts.length != 6) {
            throw new FTPException("Invalid PASV response format");
        }

        // Build IP and port
        String ip = parts[0].trim() + "." + parts[1].trim() + "."
                   + parts[2].trim() + "." + parts[3].trim();
        int port = Integer.parseInt(parts[4].trim()) * 256
                 + Integer.parseInt(parts[5].trim());

        Socket dataSocket = new Socket(ip, port);
        dataSocket.setSoTimeout(15000);
        return dataSocket;
    }

    // List remote directory contents
    List<String> list() throws IOException, FTPException {
        Socket dataSocket = null;
        BufferedReader dataReader = null;
        try {
            dataSocket = openPassiveConnection();
            String res = connection.sendCommand("LIST");
            FTPResponse r = FTPResponse.parse(res);
            if (r.isError()) throw new FTPException(r.code, r.message);

            // Read listing from data connection
            dataReader = new BufferedReader(
                new InputStreamReader(dataSocket.getInputStream()));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = dataReader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }

            // Read transfer complete message
            readTransferComplete();
            return lines;
        } finally {
            if (dataReader != null) try { dataReader.close(); } catch (IOException e) {}
            if (dataSocket != null) try { dataSocket.close(); } catch (IOException e) {}
        }
    }

    // Read the "226 Transfer complete" response
    private void readTransferComplete() {
        try {
            connection.readResponse();
        } catch (IOException e) {
            // ignore - transfer may already be done
        }
    }

    // Download remote file to local path (binary mode)
    void download(String remoteFile, String localPath)
            throws IOException, FTPException {
        setBinaryMode();
        Socket dataSocket = null;
        InputStream dataIn = null;
        FileOutputStream fileOut = null;
        try {
            dataSocket = openPassiveConnection();
            String res = connection.sendCommand("RETR " + remoteFile);
            FTPResponse r = FTPResponse.parse(res);
            if (r.isError()) throw new FTPException(r.code, r.message);

            // Read data socket -> write local file
            dataIn = dataSocket.getInputStream();
            fileOut = new FileOutputStream(localPath);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
            }

            readTransferComplete();
        } finally {
            if (fileOut != null) try { fileOut.close(); } catch (IOException e) {}
            if (dataIn != null) try { dataIn.close(); } catch (IOException e) {}
            if (dataSocket != null) try { dataSocket.close(); } catch (IOException e) {}
        }
    }

    // Upload local file to remote server (binary mode)
    void upload(String localFile, String remoteName)
            throws IOException, FTPException {
        setBinaryMode();
        Socket dataSocket = null;
        FileInputStream fileIn = null;
        OutputStream dataOut = null;
        try {
            dataSocket = openPassiveConnection();
            String res = connection.sendCommand("STOR " + remoteName);
            FTPResponse r = FTPResponse.parse(res);
            if (r.isError()) throw new FTPException(r.code, r.message);

            // Read local file -> write data socket
            fileIn = new FileInputStream(localFile);
            dataOut = dataSocket.getOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }
            dataOut.flush();
            dataSocket.close(); // signal end of transfer
            dataSocket = null;

            readTransferComplete();
        } finally {
            if (fileIn != null) try { fileIn.close(); } catch (IOException e) {}
            if (dataOut != null) try { dataOut.close(); } catch (IOException e) {}
            if (dataSocket != null) try { dataSocket.close(); } catch (IOException e) {}
        }
    }

    // Delete remote file
    void delete(String filename) throws IOException, FTPException {
        String res = connection.sendCommand("DELE " + filename);
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    // Create remote directory
    void mkdir(String dirName) throws IOException, FTPException {
        String res = connection.sendCommand("MKD " + dirName);
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    // Remove remote directory
    void rmdir(String dirName) throws IOException, FTPException {
        String res = connection.sendCommand("RMD " + dirName);
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    // Close connection gracefully
    void quit() {
        try {
            connection.sendCommand("QUIT");
        } catch (IOException e) {
            // ignore on quit
        } finally {
            connection.disconnect();
        }
    }

    // Check if connected
    boolean isConnected() {
        return connection.isConnected();
    }
}
