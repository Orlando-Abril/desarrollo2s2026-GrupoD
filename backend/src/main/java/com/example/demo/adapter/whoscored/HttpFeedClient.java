package com.example.demo.adapter.whoscored;

import com.example.demo.exception.WhoScoredException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

/**
 * Transporte HTTP simple ({@code whoscored.client=http}). Se usa en tests con {@code MockRestServiceServer}:
 * contra el WhoScored real, Cloudflare bloquea a este cliente (research R12).
 */
public class HttpFeedClient implements WhoScoredFeedClient {
    private final RestClient restClient;

    public HttpFeedClient(RestClient whoScoredRestClient) {
        this.restClient = whoScoredRestClient;
    }

    @Override
    public FeedResponse get(String pathAndQuery) {
        try {
            return restClient.get()
                    .uri(pathAndQuery)
                    .exchange((request, response) -> new FeedResponse(response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (ResourceAccessException ex) {
            throw new WhoScoredException(WhoScoredException.TIMEOUT, "WhoScored no respondió a tiempo", ex);
        } catch (RestClientException ex) {
            throw new WhoScoredException(WhoScoredException.HTTP_ERROR, "Error al consultar WhoScored", ex);
        }
    }

    @Override
    public void close() {
        // Sin recursos propios: el RestClient es un bean compartido.
    }
}
