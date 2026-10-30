package com.example.demo.service.market;

/** Resultado de una pasada del creador de mercado: jugadores con la orden rehecha, sin cambios y con falla. */
public record SyncResult(int updated, int unchanged, int failed) {

    public static final SyncResult EMPTY = new SyncResult(0, 0, 0);
}
