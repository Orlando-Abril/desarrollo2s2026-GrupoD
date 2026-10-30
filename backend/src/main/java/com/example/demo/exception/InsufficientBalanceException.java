package com.example.demo.exception;

import java.math.BigDecimal;

/** El saldo disponible no cubre el costo de la compra (422). */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(BigDecimal required, BigDecimal available) {
        super("Saldo insuficiente: la orden necesita " + required.toPlainString() + " créditos y hay "
                + available.toPlainString() + " disponibles");
    }
}
