package com.example.demo.exception;

public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException() {
        super("El catálogo todavía no está disponible");
    }
}
