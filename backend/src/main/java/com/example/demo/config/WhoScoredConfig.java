package com.example.demo.config;

import com.example.demo.adapter.whoscored.HttpFeedClient;
import com.example.demo.adapter.whoscored.PlaywrightFeedClient;
import com.example.demo.adapter.whoscored.WhoScoredFeedClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(WhoScoredProperties.class)
public class WhoScoredConfig {

    /**
     * Sin cookie, Referer ni token (V0). No se siguen redirecciones (default {@code NEVER} de HttpClient)
     * para que un 3xx del feed quede visible como {@code http_error}.
     */
    @Bean
    RestClient whoScoredRestClient(WhoScoredProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    /** Default de la app: Chromium headless (research R12). No abre el navegador hasta la primera consulta. */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "whoscored.client", havingValue = "browser", matchIfMissing = true)
    WhoScoredFeedClient playwrightFeedClient(WhoScoredProperties properties) {
        return new PlaywrightFeedClient(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "whoscored.client", havingValue = "http")
    WhoScoredFeedClient httpFeedClient(RestClient whoScoredRestClient) {
        return new HttpFeedClient(whoScoredRestClient);
    }
}
