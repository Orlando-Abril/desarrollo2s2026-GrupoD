package com.example.demo.adapter.whoscored;

/**
 * Transporte del feed de WhoScored: sólo descarga, no interpreta. La clasificación de bloqueos y el
 * parseo quedan en {@link WhoScoredAdapter} y {@link WhoScoredStatsMapper}.
 */
public interface WhoScoredFeedClient {

    /**
     * @param pathAndQuery ruta relativa a {@code whoscored.base-url}, con su query string
     * @throws com.example.demo.exception.WhoScoredException {@code timeout}, {@code http_error} o {@code browser_error}
     */
    FeedResponse get(String pathAndQuery);

    /** Libera los recursos del transporte; la próxima consulta los vuelve a abrir. */
    void close();

    record FeedResponse(int status, String body) {
    }
}
