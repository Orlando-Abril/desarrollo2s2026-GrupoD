package com.example.demo.exception;

public class InvalidQuoteQueryException extends RuntimeException {

    public InvalidQuoteQueryException(String message) {
        super(message);
    }
}
