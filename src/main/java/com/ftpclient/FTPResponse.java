package com.ftpclient;

/**
 * Represents a parsed FTP server response.
 *
 * FTP responses follow RFC 959 format:
 *   - Single-line:  "CODE MESSAGE\r\n"         e.g. "220 Welcome\r\n"
 *   - Multi-line:   "CODE-first line\r\n"       (dash after code)
 *                   " continuation...\r\n"
 *                   "CODE last line\r\n"         (space after code = end)
 *
 * The 3-digit reply code indicates success/failure:
 *   1xx = Positive preliminary (action started, expect another reply)
 *   2xx = Positive completion
 *   3xx = Positive intermediate (need more info, e.g. password after USER)
 *   4xx = Transient negative (temporary failure, retry may work)
 *   5xx = Permanent negative (command rejected)
 */
public class FTPResponse {

    private final int code;       // 3-digit FTP reply code
    private final String message; // Full response message (may span multiple lines)

    /**
     * Constructs an FTPResponse with the given code and message.
     *
     * @param code    the 3-digit reply code
     * @param message the response message text
     */
    public FTPResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * Returns the 3-digit FTP reply code.
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the full response message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Checks if this response indicates success (2xx or 1xx codes).
     * 3xx is "intermediate" — used during login flow (USER → 331 → PASS → 230).
     */
    public boolean isSuccess() {
        return code >= 100 && code < 400;
    }

    /**
     * Checks if this is a positive completion reply (2xx).
     */
    public boolean isPositiveCompletion() {
        return code >= 200 && code < 300;
    }

    /**
     * Checks if this is a positive preliminary reply (1xx).
     * Indicates the action has started; another reply will follow.
     */
    public boolean isPositivePreliminary() {
        return code >= 100 && code < 200;
    }

    /**
     * Checks if this is a "need more info" reply (3xx).
     * e.g. 331 "User OK, send password"
     */
    public boolean isIntermediate() {
        return code >= 300 && code < 400;
    }

    /**
     * Checks if this response indicates an error (4xx or 5xx).
     */
    public boolean isError() {
        return code >= 400;
    }

    @Override
    public String toString() {
        return code + " " + message;
    }
}
