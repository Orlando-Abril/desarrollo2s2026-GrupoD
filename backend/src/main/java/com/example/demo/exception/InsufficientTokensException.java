package com.example.demo.exception;

/** La venta supera los tokens disponibles del usuario para ese jugador (422). */
public class InsufficientTokensException extends RuntimeException {

    public InsufficientTokensException(int requested, int available) {
        super("Tokens insuficientes: la orden vende " + requested + " y hay " + available + " disponibles");
    }
}
