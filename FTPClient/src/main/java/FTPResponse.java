// FTPResponse - Parses FTP server replies into code + message
public class FTPResponse {
    int code;       // 3-digit reply code
    String message; // full reply text

    FTPResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // Parse raw reply string into FTPResponse
    static FTPResponse parse(String raw) {
        if (raw == null || raw.length() < 3) {
            return new FTPResponse(0, raw != null ? raw : "");
        }
        try {
            int code = Integer.parseInt(raw.substring(0, 3));
            String msg = raw.length() > 4 ? raw.substring(4) : "";
            return new FTPResponse(code, msg);
        } catch (NumberFormatException e) {
            return new FTPResponse(0, raw);
        }
    }

    // Check if reply indicates an error
    boolean isError() {
        return code >= 400;
    }

    public String toString() {
        return code + " " + message;
    }
}
