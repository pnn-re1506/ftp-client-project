
public class FTPResponse {
    int code;       
    String message; 

    FTPResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

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

    boolean isError() {
        return code >= 400;
    }

    public String toString() {
        return code + " " + message;
    }
}
