package com.example.demo.exception;

import java.util.Collection;

public class UnknownValuationStrategyException extends RuntimeException {

    public UnknownValuationStrategyException(String requested, Collection<String> validCodes) {
        super("Estrategia de valuación desconocida: '" + requested + "'. Códigos válidos: "
                + String.join(", ", validCodes));
    }
}
