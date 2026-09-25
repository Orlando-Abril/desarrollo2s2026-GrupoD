package com.example.demo.exception;

public class FootballDataException extends RuntimeException {
    private final String code;

    public FootballDataException(String code, String message) {
        super(message);
        this.code = code;
    }

    public FootballDataException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
