package com.example.demo.exception;

public class AuthenticatedUserNotFoundException extends RuntimeException {
    public AuthenticatedUserNotFoundException() {
        super("La credencial no representa una cuenta de usuario");
    }
}
