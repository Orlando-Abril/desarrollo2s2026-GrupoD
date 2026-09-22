package com.example.demo.exception;

public class SuperuserUnavailableException extends RuntimeException {
    public SuperuserUnavailableException() {
        super("El superusuario de mercado configurado no está disponible");
    }
}
