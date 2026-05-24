// FTPException - Custom exception for FTP errors (code >= 400)
public class FTPException extends Exception {
    int code;
    String reply;

    FTPException(int code, String reply) {
        super(code + " " + reply);
        this.code = code;
        this.reply = reply;
    }

    FTPException(String message) {
        super(message);
        this.code = 0;
        this.reply = message;
    }
}
