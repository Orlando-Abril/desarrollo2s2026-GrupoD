package com.example.demo.service.market;

import java.math.BigDecimal;

/** Un calce calculado por el motor: contra qué orden del libro, cuánto y a qué precio (el de esa orden). */
public record Fill(Long bookOrderId, Long counterpartyUserId, int quantity, BigDecimal price) {
}
