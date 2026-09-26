package com.example.demo.exception;

public class WhoScoredException extends RuntimeException {
    public static final String HTTP_ERROR = "http_error";
    public static final String BLOCKED = "blocked";
    public static final String TIMEOUT = "timeout";
    public static final String UNEXPECTED_STRUCTURE = "unexpected_structure";
    public static final String BROWSER_ERROR = "browser_error";
    public static final String NO_METRICS = "no_metrics";
    public static final String PERSISTENCE_ERROR = "persistence_error";

    private final String code;

    public WhoScoredException(String code, String message) {
        super(message);
        this.code = code;
    }

    public WhoScoredException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
