import java.io.*;
import java.net.*;
import java.util.*;

public class FTPClient {
    private FTPConnection connection;

    FTPClient() {
        connection = new FTPConnection();
    }

    void setLogListener(FTPConnection.LogListener listener) {
        connection.setLogListener(listener);
    }

    void connect(String host, int port) throws IOException, FTPException {
        connection.connect(host, port);
        String welcome = connection.readWelcome();
        FTPResponse res = FTPResponse.parse(welcome);
        if (res.isError()) {
            throw new FTPException(res.code, res.message);
        }
    }

    void login(String user, String pass) throws IOException, FTPException {
        String res = connection.sendCommand("USER " + user);
        FTPResponse userRes = FTPResponse.parse(res);
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

    String pwd() throws IOException, FTPException {
        String res = connection.sendCommand("PWD");
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
        String msg = r.message;
        int start = msg.indexOf('"');
        int end = msg.indexOf('"', start + 1);
        if (start >= 0 && end > start) {
            return msg.substring(start + 1, end);
        }
        return msg;
    }

    void cwd(String path) throws IOException, FTPException {
        String res = connection.sendCommand("CWD " + path);
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    private void setBinaryMode() throws IOException, FTPException {
        String res = connection.sendCommand("TYPE I");
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    Socket openPassiveConnection() throws IOException, FTPException {
        String res = connection.sendCommand("PASV");
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
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

        String ip = parts[0].trim() + "." + parts[1].trim() + "."
                   + parts[2].trim() + "." + parts[3].trim();
        int port = Integer.parseInt(parts[4].trim()) * 256
                 + Integer.parseInt(parts[5].trim());

        Socket dataSocket = new Socket(ip, port);
        dataSocket.setSoTimeout(15000);
        return dataSocket;
    }

    List<String> list() throws IOException, FTPException {
        Socket dataSocket = null;
        BufferedReader dataReader = null;
        try {
            dataSocket = openPassiveConnection();
            String res = connection.sendCommand("LIST");
            FTPResponse r = FTPResponse.parse(res);
            if (r.isError()) throw new FTPException(r.code, r.message);
            dataReader = new BufferedReader(
                new InputStreamReader(dataSocket.getInputStream()));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = dataReader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
            readTransferComplete();
            return lines;
        } finally {
            if (dataReader != null) try { dataReader.close(); } catch (IOException e) {}
            if (dataSocket != null) try { dataSocket.close(); } catch (IOException e) {}
        }
    }

    private void readTransferComplete() {
        try {
            connection.readResponse();
        } catch (IOException e) {
        }
    }

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
            fileIn = new FileInputStream(localFile);
            dataOut = dataSocket.getOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }
            dataOut.flush();
            dataSocket.close(); 
            dataSocket = null;

            readTransferComplete();
        } finally {
            if (fileIn != null) try { fileIn.close(); } catch (IOException e) {}
            if (dataOut != null) try { dataOut.close(); } catch (IOException e) {}
            if (dataSocket != null) try { dataSocket.close(); } catch (IOException e) {}
        }
    }

    void delete(String filename) throws IOException, FTPException {
        String res = connection.sendCommand("DELE " + filename);
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    void mkdir(String dirName) throws IOException, FTPException {
        String res = connection.sendCommand("MKD " + dirName);
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    void rmdir(String dirName) throws IOException, FTPException {
        String res = connection.sendCommand("RMD " + dirName);
        FTPResponse r = FTPResponse.parse(res);
        if (r.isError()) throw new FTPException(r.code, r.message);
    }

    void quit() {
        try {
            connection.sendCommand("QUIT");
        } catch (IOException e) {
        } finally {
            connection.disconnect();
        }
    }

    boolean isConnected() {
        return connection.isConnected();
    }
}
