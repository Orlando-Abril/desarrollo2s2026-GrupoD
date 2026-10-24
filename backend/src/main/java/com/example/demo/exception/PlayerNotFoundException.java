package com.example.demo.exception;

public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(Long playerId) {
        super("No existe el jugador " + playerId);
    }
}
