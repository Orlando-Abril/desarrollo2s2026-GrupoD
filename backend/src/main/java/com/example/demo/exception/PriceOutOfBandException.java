package com.example.demo.exception;

import java.math.BigDecimal;

/** Precio límite fuera de la banda ±20% de la cotización vigente (400). */
public class PriceOutOfBandException extends RuntimeException {

    public PriceOutOfBandException(BigDecimal limitPrice, BigDecimal lower, BigDecimal upper) {
        super("El precio límite " + limitPrice.toPlainString() + " está fuera de la banda permitida ["
                + lower.toPlainString() + ", " + upper.toPlainString() + "]");
    }
}
