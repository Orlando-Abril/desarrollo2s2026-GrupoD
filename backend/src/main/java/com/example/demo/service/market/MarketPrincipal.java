package com.example.demo.service.market;

/** Usuario autenticado para el mercado: sólo id y username, sin cargar la entidad (spec 027, research D3 y D8). */
public record MarketPrincipal(Long id, String username) {
}
