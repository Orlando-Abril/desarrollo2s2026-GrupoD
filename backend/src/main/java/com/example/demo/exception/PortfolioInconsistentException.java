package com.example.demo.exception;

public class PortfolioInconsistentException extends RuntimeException {
    public PortfolioInconsistentException(String detail) {
        super(detail);
    }
}
