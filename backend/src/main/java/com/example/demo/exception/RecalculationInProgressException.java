package com.example.demo.exception;

public class RecalculationInProgressException extends RuntimeException {

    public RecalculationInProgressException() {
        super("Ya hay un recálculo de cotizaciones en curso");
    }
}
