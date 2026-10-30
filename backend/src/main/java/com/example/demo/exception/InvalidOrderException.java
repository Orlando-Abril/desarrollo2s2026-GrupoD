package com.example.demo.exception;

/** Orden incoherente: una LIMIT sin precio límite o una MARKET con precio límite (400). */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
