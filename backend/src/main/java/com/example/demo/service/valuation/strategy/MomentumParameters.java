package com.example.demo.service.valuation.strategy;

/**
 * Parámetros de forma reciente de MOMENTUM. {@code window} es la cantidad de cotizaciones previas que mira
 * la forma reciente; con menos de {@code minHistory} cotizaciones previas, el algoritmo (feature 019)
 * registra {@code INSUFFICIENT_HISTORY}.
 */
public record MomentumParameters(int window, int minHistory) {

    public MomentumParameters {
        if (window < 1 || minHistory < 1 || minHistory > window) {
            throw new IllegalArgumentException("Parámetros de MOMENTUM inválidos: window=" + window
                    + ", minHistory=" + minHistory + " (se exige 1 <= minHistory <= window)");
        }
    }
}
