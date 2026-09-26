package com.example.demo.config;

import com.example.demo.adapter.whoscored.HttpFeedClient;
import com.example.demo.adapter.whoscored.PlaywrightFeedClient;
import com.example.demo.adapter.whoscored.WhoScoredFeedClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** Selección del transporte por {@code whoscored.client}. Ningún caso abre un navegador. */
class WhoScoredFeedClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(WhoScoredConfig.class)
            .withPropertyValues(
                    "whoscored.base-url=http://127.0.0.1:1",
                    "whoscored.user-agent=test-agent",
                    "whoscored.connect-timeout=100ms",
                    "whoscored.read-timeout=100ms",
                    "whoscored.request-delay=0s",
                    "whoscored.cache-ttl=1m",
                    "whoscored.block-retry.enabled=true",
                    "whoscored.block-retry.delay=0s",
                    "whoscored.tournaments.PREMIER_LEAGUE=2",
                    "whoscored.tournaments.LA_LIGA=4",
                    "whoscored.tournaments.SERIE_A=5",
                    "whoscored.tournaments.BUNDESLIGA=3",
                    "whoscored.tournaments.LIGUE_1=22",
                    "whoscored.sync.enabled=false",
                    "whoscored.sync.cron=0 0 4 * * MON",
                    "whoscored.sync.zone=UTC",
                    "whoscored.browser.landing-path=/regions/252/tournaments/2/england-premier-league",
                    "whoscored.browser.navigation-timeout=1s");

    @Test
    void httpClientSelectsTheRestClientTransport() {
        contextRunner.withPropertyValues("whoscored.client=http").run(context -> {
            assertThat(context).hasSingleBean(WhoScoredFeedClient.class);
            assertThat(context.getBean(WhoScoredFeedClient.class)).isInstanceOf(HttpFeedClient.class);
        });
    }

    @Test
    void browserClientSelectsPlaywrightWithoutOpeningTheBrowser() {
        contextRunner.withPropertyValues("whoscored.client=browser").run(context -> {
            assertThat(context).hasSingleBean(WhoScoredFeedClient.class);
            assertThat(context.getBean(WhoScoredFeedClient.class)).isInstanceOf(PlaywrightFeedClient.class);
        });
    }

    @Test
    void clientValueIsCaseInsensitive() {
        contextRunner.withPropertyValues("whoscored.client=BROWSER").run(context ->
                assertThat(context.getBean(WhoScoredFeedClient.class)).isInstanceOf(PlaywrightFeedClient.class));
    }

    @Test
    void invalidLandingPathFailsAtStartup() {
        contextRunner.withPropertyValues("whoscored.client=http", "whoscored.browser.landing-path=regions/sin-barra")
                .run(context -> assertThat(context).hasFailed());
    }
}
