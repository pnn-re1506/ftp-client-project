package com.ftpclient;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FTP client implementation using raw sockets and the FTP protocol (RFC 959).
 *
 * Architecture:
 *   - Control connection: A persistent TCP socket on port 21 (by default).
 *     Commands are sent as text lines; responses are read back.
 *   - Data connection: A temporary TCP socket opened for each data transfer
 *     (LIST, RETR, STOR). We use PASV mode, where the server opens a port
 *     and tells us the address to connect to.
 *
 * Every public method that sends an FTP command will also invoke the
 * optional logConsumer callback so the GUI can display protocol traffic.
 */
public class FTPClient {

    private Socket controlSocket;       // Persistent control connection
    private BufferedReader reader;       // Reads responses from server
    private PrintWriter writer;         // Sends commands to server
    private boolean connected = false;  // Connection state flag

    // Optional callback for logging FTP commands and responses to the GUI
    private Consumer<String> logConsumer;

    /**
     * Sets a callback that receives log messages (commands sent, responses received).
     * The GUI hooks into this to display protocol traffic in the console panel.
     */
    public void setLogConsumer(Consumer<String> logConsumer) {
        this.logConsumer = logConsumer;
    }

    /**
     * Logs a message to the consumer (if set) and to stdout.
     */
    private void log(String message) {
        if (logConsumer != null) {
            logConsumer.accept(message);
        }
        System.out.println(message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONNECTION MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens a control connection to the FTP server.
     *
     * FTP Protocol:
     *   1. Client opens TCP connection to server:port (default 21)
     *   2. Server responds with 220 "Service ready" greeting
     *
     * @param host FTP server hostname or IP
     * @param port FTP control port (typically 21)
     * @return the server's welcome response
     * @throws IOException if connection fails
     */
    public FTPResponse connect(String host, int port) throws IOException {
        log(">>> Connecting to " + host + ":" + port);

        // Open TCP socket to the FTP server's control port
        controlSocket = new Socket(host, port);

        // Set up I/O streams on the control connection:
        //   - BufferedReader for reading server responses line by line
        //   - PrintWriter with auto-flush for sending commands
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream()), true);

        // Read the server's welcome banner (code 220)
        FTPResponse response = readResponse();
        log("<<< " + response);

        if (response.getCode() == 220) {
            connected = true;
        }

        return response;
    }

    /**
     * Authenticates with the FTP server using USER and PASS commands.
     *
     * FTP Protocol:
     *   1. Send "USER username" → expect 331 "User OK, need password"
     *   2. Send "PASS password" → expect 230 "Login successful"
     *
     * @param username FTP username (use "anonymous" for public servers)
     * @param password FTP password (use email for anonymous login)
     * @return the final login response
     * @throws IOException if I/O error occurs
     */
    public FTPResponse login(String username, String password) throws IOException {
        // Step 1: Send USER command
        FTPResponse userResp = sendCommand("USER " + username);

        // 331 = "User name okay, need password"
        // 230 = "User logged in" (some servers accept USER without PASS)
        if (userResp.getCode() == 230) {
            return userResp; // Already logged in, no password needed
        }

        if (userResp.getCode() != 331) {
            throw new IOException("USER command failed: " + userResp);
        }

        // Step 2: Send PASS command
        FTPResponse passResp = sendCommand("PASS " + password);

        if (passResp.getCode() != 230) {
            throw new IOException("Login failed: " + passResp);
        }

        return passResp;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIRECTORY NAVIGATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current working directory on the server.
     *
     * FTP Protocol:
     *   Send "PWD" → server replies 257 "/current/path"
     *   The path is enclosed in double quotes in the response.
     *
     * @return the current directory path as a string
     * @throws IOException if command fails
     */
    public String pwd() throws IOException {
        FTPResponse response = sendCommand("PWD");

        if (response.getCode() != 257) {
            throw new IOException("PWD failed: " + response);
        }

        // Extract path from quotes: 257 "/some/path" is current directory
        String msg = response.getMessage();
        int first = msg.indexOf('"');
        int last = msg.lastIndexOf('"');
        if (first >= 0 && last > first) {
            return msg.substring(first + 1, last);
        }

        return msg;
    }

    /**
     * Changes the current working directory on the server.
     *
     * FTP Protocol:
     *   Send "CWD path" → expect 250 "Directory changed"
     *
     * @param path the directory to change to
     * @return the server's response
     * @throws IOException if command fails
     */
    public FTPResponse cwd(String path) throws IOException {
        FTPResponse response = sendCommand("CWD " + path);

        if (response.getCode() != 250) {
            throw new IOException("CWD failed: " + response);
        }

        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIRECTORY LISTING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lists files in the current remote directory.
     *
     * FTP Protocol:
     *   1. Send "PASV" → server replies 227 with data port info
     *   2. Parse the (h1,h2,h3,h4,p1,p2) to get data connection address
     *   3. Open data connection to that address
     *   4. Send "LIST" over control connection
     *   5. Read directory listing from data connection
     *   6. Close data connection
     *   7. Read final "226 Transfer complete" from control connection
     *
     * @return raw directory listing string (Unix ls -l format typically)
     * @throws IOException if command fails
     */
    public String list() throws IOException {
        // Step 1-3: Enter passive mode and open data connection
        Socket dataSocket = openPassiveDataConnection();

        // Step 4: Send LIST command over control connection
        FTPResponse listResp = sendCommand("LIST");

        // Expect 150 "Opening data connection" or 125 "Data connection already open"
        if (listResp.getCode() != 150 && listResp.getCode() != 125) {
            dataSocket.close();
            throw new IOException("LIST failed: " + listResp);
        }

        // Step 5: Read the directory listing from the data connection
        StringBuilder listing = new StringBuilder();
        try (BufferedReader dataReader = new BufferedReader(
                new InputStreamReader(dataSocket.getInputStream()))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                listing.append(line).append("\n");
            }
        }

        // Step 6: Data connection closed (try-with-resources)
        dataSocket.close();

        // Step 7: Read the transfer complete response (226)
        FTPResponse completeResp = readResponse();
        log("<<< " + completeResp);

        return listing.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FILE TRANSFER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Downloads a file from the server in binary mode.
     *
     * FTP Protocol:
     *   1. Send "TYPE I" → switch to binary transfer mode
     *   2. Send "PASV"   → enter passive mode, get data port
     *   3. Open data connection to the passive port
     *   4. Send "RETR filename" over control connection
     *   5. Read binary data from data connection, write to local file
     *   6. Close data connection
     *   7. Read "226 Transfer complete"
     *
     * @param remoteFile the remote file name to download
     * @param localPath  local file path to save to
     * @throws IOException if transfer fails
     */
    public void download(String remoteFile, String localPath) throws IOException {
        // Step 1: Switch to binary mode (TYPE I = Image/binary)
        FTPResponse typeResp = sendCommand("TYPE I");
        if (typeResp.getCode() != 200) {
            throw new IOException("TYPE I failed: " + typeResp);
        }

        // Step 2-3: Enter passive mode and open data connection
        Socket dataSocket = openPassiveDataConnection();

        // Step 4: Send RETR (retrieve) command
        FTPResponse retrResp = sendCommand("RETR " + remoteFile);
        if (retrResp.getCode() != 150 && retrResp.getCode() != 125) {
            dataSocket.close();
            throw new IOException("RETR failed: " + retrResp);
        }

        // Step 5: Read binary data from data connection and write to local file
        try (InputStream dataIn = dataSocket.getInputStream();
             FileOutputStream fileOut = new FileOutputStream(localPath)) {

            byte[] buffer = new byte[8192]; // 8KB buffer for efficient I/O
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            log("--- Downloaded " + totalBytes + " bytes to " + localPath);
        }

        // Step 6-7: Close data socket. Read transfer complete response.
        dataSocket.close();
        FTPResponse completeResp = readResponse();
        log("<<< " + completeResp);

        if (completeResp.getCode() != 226 && completeResp.getCode() != 250) {
            throw new IOException("Download did not complete successfully: " + completeResp);
        }
    }

    /**
     * Uploads a local file to the server in binary mode.
     *
     * FTP Protocol:
     *   1. Send "TYPE I" → switch to binary transfer mode
     *   2. Send "PASV"   → enter passive mode, get data port
     *   3. Open data connection to the passive port
     *   4. Send "STOR filename" over control connection
     *   5. Write local file data to the data connection
     *   6. Close data connection (signals end of transfer)
     *   7. Read "226 Transfer complete"
     *
     * @param localPath  local file path to upload
     * @param remoteFile remote file name to create/overwrite
     * @throws IOException if transfer fails
     */
    public void upload(String localPath, String remoteFile) throws IOException {
        // Step 1: Switch to binary mode
        FTPResponse typeResp = sendCommand("TYPE I");
        if (typeResp.getCode() != 200) {
            throw new IOException("TYPE I failed: " + typeResp);
        }

        // Step 2-3: Enter passive mode and open data connection
        Socket dataSocket = openPassiveDataConnection();

        // Step 4: Send STOR (store) command
        FTPResponse storResp = sendCommand("STOR " + remoteFile);
        if (storResp.getCode() != 150 && storResp.getCode() != 125) {
            dataSocket.close();
            throw new IOException("STOR failed: " + storResp);
        }

        // Step 5: Write local file data to the data connection
        try (FileInputStream fileIn = new FileInputStream(localPath);
             OutputStream dataOut = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            dataOut.flush();
            log("--- Uploaded " + totalBytes + " bytes from " + localPath);
        }

        // Step 6-7: Close data socket (signals EOF). Read transfer complete response.
        dataSocket.close();
        FTPResponse completeResp = readResponse();
        log("<<< " + completeResp);

        if (completeResp.getCode() != 226 && completeResp.getCode() != 250) {
            throw new IOException("Upload did not complete successfully: " + completeResp);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FILE/DIRECTORY MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a file on the server.
     *
     * FTP Protocol: Send "DELE filename" → expect 250 "File deleted"
     */
    public FTPResponse delete(String remoteFile) throws IOException {
        FTPResponse response = sendCommand("DELE " + remoteFile);
        if (response.getCode() != 250) {
            throw new IOException("DELE failed: " + response);
        }
        return response;
    }

    /**
     * Creates a directory on the server.
     *
     * FTP Protocol: Send "MKD dirname" → expect 257 "Directory created"
     */
    public FTPResponse mkdir(String dirName) throws IOException {
        FTPResponse response = sendCommand("MKD " + dirName);
        if (response.getCode() != 257) {
            throw new IOException("MKD failed: " + response);
        }
        return response;
    }

    /**
     * Removes a directory on the server.
     *
     * FTP Protocol: Send "RMD dirname" → expect 250 "Directory removed"
     */
    public FTPResponse rmdir(String dirName) throws IOException {
        FTPResponse response = sendCommand("RMD " + dirName);
        if (response.getCode() != 250) {
            throw new IOException("RMD failed: " + response);
        }
        return response;
    }

    /**
     * Renames a file or directory on the server.
     *
     * FTP Protocol:
     *   1. Send "RNFR oldName" → expect 350 "Ready for destination name"
     *   2. Send "RNTO newName" → expect 250 "Rename successful"
     */
    public FTPResponse rename(String oldName, String newName) throws IOException {
        FTPResponse rnfrResp = sendCommand("RNFR " + oldName);
        if (rnfrResp.getCode() != 350) {
            throw new IOException("RNFR failed: " + rnfrResp);
        }

        FTPResponse rntoResp = sendCommand("RNTO " + newName);
        if (rntoResp.getCode() != 250) {
            throw new IOException("RNTO failed: " + rntoResp);
        }

        return rntoResp;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISCONNECT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends QUIT and closes the control connection.
     *
     * FTP Protocol: Send "QUIT" → expect 221 "Goodbye"
     */
    public void quit() throws IOException {
        if (connected && writer != null) {
            try {
                sendCommand("QUIT");
            } catch (IOException e) {
                // Best-effort quit; connection may already be closed
                log("--- Warning during QUIT: " + e.getMessage());
            }
        }

        connected = false;

        // Close all resources
        if (reader != null) { try { reader.close(); } catch (IOException ignored) {} }
        if (writer != null) { writer.close(); }
        if (controlSocket != null && !controlSocket.isClosed()) {
            try { controlSocket.close(); } catch (IOException ignored) {}
        }

        log("--- Disconnected from server.");
    }

    /**
     * Returns whether the client is currently connected.
     */
    public boolean isConnected() {
        return connected && controlSocket != null && !controlSocket.isClosed();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a raw FTP command and reads the response.
     *
     * FTP commands are plain text lines terminated by \r\n.
     * PrintWriter's println() handles the line ending.
     *
     * @param command the FTP command string (e.g. "USER anonymous")
     * @return the parsed FTPResponse
     * @throws IOException if I/O error occurs
     */
    private FTPResponse sendCommand(String command) throws IOException {
        // Log the command being sent (mask password for security)
        if (command.startsWith("PASS ")) {
            log(">>> PASS ****");
        } else {
            log(">>> " + command);
        }

        // Send the command over the control connection
        writer.print(command + "\r\n");
        writer.flush();

        // Read and return the server's response
        FTPResponse response = readResponse();
        log("<<< " + response);
        return response;
    }

    /**
     * Reads a complete FTP response from the control connection.
     *
     * Handles both single-line and multi-line responses:
     *
     * Single-line format:
     *   "220 Welcome to FTP server\r\n"
     *
     * Multi-line format (RFC 959 Section 4.2):
     *   "220-Welcome to FTP server\r\n"    ← first line, code followed by dash
     *   " This is additional info\r\n"      ← continuation lines
     *   "220 End of greeting\r\n"           ← last line, code followed by space
     *
     * @return parsed FTPResponse with code and accumulated message
     * @throws IOException if I/O error or unexpected response format
     */
    private FTPResponse readResponse() throws IOException {
        StringBuilder fullMessage = new StringBuilder();
        String line;
        int code = -1;

        while ((line = reader.readLine()) != null) {
            // Each response line should start with a 3-digit code
            if (line.length() >= 3) {
                try {
                    int lineCode = Integer.parseInt(line.substring(0, 3));

                    if (code == -1) {
                        code = lineCode; // Capture the code from the first line
                    }

                    // Check if this is a continuation line (code followed by dash)
                    if (line.length() > 3 && line.charAt(3) == '-') {
                        // Multi-line response continues
                        fullMessage.append(line.substring(4)).append("\n");
                        continue;
                    }

                    // Final line: code followed by space (or end of line)
                    if (lineCode == code) {
                        if (line.length() > 4) {
                            fullMessage.append(line.substring(4));
                        }
                        break; // End of response
                    }
                } catch (NumberFormatException e) {
                    // Line doesn't start with a number; treat as continuation
                    fullMessage.append(line).append("\n");
                    continue;
                }
            }

            // Continuation line without code prefix
            fullMessage.append(line).append("\n");
        }

        if (code == -1) {
            throw new IOException("Connection closed: no response from server");
        }

        return new FTPResponse(code, fullMessage.toString().trim());
    }

    /**
     * Enters passive mode and opens a data connection.
     *
     * FTP PASV Protocol:
     *   1. Send "PASV" command
     *   2. Server replies: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
     *   3. Parse the 6 numbers:
     *      - h1.h2.h3.h4 = IP address of the data server
     *      - port = p1 * 256 + p2 = TCP port for data connection
     *   4. Client opens a new TCP socket to that IP:port
     *
     * @return Socket connected to the server's data port
     * @throws IOException if PASV fails or data connection cannot be opened
     */
    private Socket openPassiveDataConnection() throws IOException {
        // Step 1: Send PASV command
        FTPResponse pasvResp = sendCommand("PASV");

        if (pasvResp.getCode() != 227) {
            throw new IOException("PASV failed: " + pasvResp);
        }

        // Step 2-3: Parse the response to extract IP and port
        // Response format: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
        String responseMsg = pasvResp.getMessage();
        Pattern pattern = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");
        Matcher matcher = pattern.matcher(responseMsg);

        if (!matcher.find()) {
            throw new IOException("Could not parse PASV response: " + responseMsg);
        }

        // Build the data connection address
        String dataHost = matcher.group(1) + "." + matcher.group(2) + "."
                         + matcher.group(3) + "." + matcher.group(4);
        int p1 = Integer.parseInt(matcher.group(5));
        int p2 = Integer.parseInt(matcher.group(6));
        int dataPort = p1 * 256 + p2; // FTP port encoding formula

        log("--- Data connection: " + dataHost + ":" + dataPort);

        // Step 4: Open TCP socket to the data address
        return new Socket(dataHost, dataPort);
    }
}
