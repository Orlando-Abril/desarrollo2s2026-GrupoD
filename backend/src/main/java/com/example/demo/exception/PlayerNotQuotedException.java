package com.example.demo.exception;

/** El jugador no tiene cotización vigente (422). Validación defensiva: hoy {@code marketValue} es obligatorio. */
public class PlayerNotQuotedException extends RuntimeException {

    public PlayerNotQuotedException(Long playerId) {
        super("El jugador " + playerId + " no tiene cotización vigente");
    }
}
