package com.example.demo.exception;

import java.time.LocalDate;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(Long playerId, String strategy, LocalDate at) {
        super("No hay cotización " + strategy + " para el jugador " + playerId + " vigente al " + at);
    }
}
