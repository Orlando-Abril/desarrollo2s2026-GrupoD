package com.example.demo.controller;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;
import com.example.demo.model.Role;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.RankingFixtures;
import com.example.demo.support.UnitTestProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.example.demo.support.StrategyComparisonFixtures.dataSnapshot;
import static com.example.demo.support.StrategyComparisonFixtures.quote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 031, US3: {@code GET /quotes/latest}. */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class LatestQuotesControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired PlayerRepository players;
    @Autowired PlayerQuoteRepository quotes;
    @Autowired ActiveStrategySettingRepository settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean FootballDataAdapter footballDataAdapter;
    @MockitoBean WhoScoredAdapter whoScoredAdapter;

    private String userKey;

    @BeforeEach
    void setUp() {
        settings.deleteAll();
        userKey = AuthFixtures.apiKey(apiKeys, AuthFixtures.user(users, "latest-" + UUID.randomUUID(), Role.USER));
    }

    @Test
    void returnsTheLatestQuotePerPlayerInRequestOrder() throws Exception {
        Player first = player("First");
        Player second = player("Second");
        Player withoutQuote = player("Without quote");
        quote(quotes, first, "MOMENTUM", "1.1000", QuoteStatus.CALCULATED, "2026-10-01T00:00:00Z");
        quote(quotes, first, "MOMENTUM", "1.1500", QuoteStatus.CALCULATED, "2026-10-28T03:00:00Z");
        quote(quotes, second, "MOMENTUM", "0.9000", QuoteStatus.INSUFFICIENT_HISTORY, "2026-10-28T03:00:00Z");
        quote(quotes, second, "OFFICIAL", "5.0000", QuoteStatus.CALCULATED, "2026-10-29T03:00:00Z");

        mvc.perform(get("/quotes/latest").header("X-API-KEY", userKey)
                        .param("playerIds", ids(second, withoutQuote, first)).param("strategy", "MOMENTUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].playerId").value(second.getId()))
                .andExpect(jsonPath("$.items[0].quote.value").value(0.9))
                .andExpect(jsonPath("$.items[0].quote.status").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.items[1].playerId").value(withoutQuote.getId()))
                .andExpect(jsonPath("$.items[1].quote").value(nullValue()))
                .andExpect(jsonPath("$.items[2].quote.value").value(1.15))
                .andExpect(jsonPath("$.items[2].quote.calculatedAt").value("2026-10-28T03:00:00Z"))
                .andExpect(jsonPath("$.items[2].quote.strategyVersion").value(1));
    }

    @Test
    void withoutStrategyTheAlternativeIsUsed() throws Exception {
        Player player = player("Default");
        quote(quotes, player, "MOMENTUM", "1.3000", QuoteStatus.CALCULATED, "2026-10-28T03:00:00Z");
        quote(quotes, player, "OFFICIAL", "2.0000", QuoteStatus.CALCULATED, "2026-10-28T03:00:00Z");

        mvc.perform(get("/quotes/latest").header("X-API-KEY", userKey).param("playerIds", ids(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy.code").value("MOMENTUM"))
                .andExpect(jsonPath("$.strategy.label").value("MOMENTUM v1"))
                .andExpect(jsonPath("$.items[0].quote.value").value(1.3));
    }

    @Test
    void duplicatesAreReportedOnceAndUnknownIdsAreOmitted() throws Exception {
        Player player = player("Once");

        mvc.perform(get("/quotes/latest").header("X-API-KEY", userKey)
                        .param("playerIds", player.getId() + "," + Long.MAX_VALUE + "," + player.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].playerId").value(player.getId()));
    }

    @Test
    void acceptsOneHundredDistinctIdsAndRejectsInvalidInput() throws Exception {
        String hundred = LongStream.rangeClosed(1, 100).mapToObj(Long::toString).collect(Collectors.joining(","));
        mvc.perform(get("/quotes/latest").header("X-API-KEY", userKey).param("playerIds", hundred))
                .andExpect(status().isOk());

        List<String> invalid = List.of(hundred + ",101", "", " ", "a", "0", "-3", "1,,2");
        for (String value : invalid) {
            mvc.perform(get("/quotes/latest").header("X-API-KEY", userKey).param("playerIds", value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"));
        }
        mvc.perform(get("/quotes/latest").header("X-API-KEY", userKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        mvc.perform(get("/quotes/latest").header("X-API-KEY", userKey).param("playerIds", "1")
                        .param("strategy", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_strategy"));
    }

    @Test
    void requiresCredentials() throws Exception {
        mvc.perform(get("/quotes/latest").param("playerIds", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void statementCountIsConstantAndNothingIsWrittenOrFetchedExternally() throws Exception {
        List<Player> created = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Player player = player("P" + i);
            quote(quotes, player, "MOMENTUM", "1.2000", QuoteStatus.CALCULATED, "2026-10-28T03:00:00Z");
            created.add(player);
        }
        entityManager.flush();
        Map<String, Object> before = dataSnapshot(jdbc);

        long few = measure(created.subList(0, 5));
        long many = measure(created);

        assertThat(many).isEqualTo(few);
        assertThat(dataSnapshot(jdbc)).isEqualTo(before);
        verifyNoInteractions(footballDataAdapter, whoScoredAdapter);
    }

    private long measure(List<Player> requested) throws Exception {
        entityManager.clear();
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().clear();
        mvc.perform(get("/quotes/latest").header("X-API-KEY", userKey)
                        .param("playerIds", ids(requested.toArray(Player[]::new))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(requested.size()));
        return factory.getStatistics().getPrepareStatementCount();
    }

    private Player player(String name) {
        return RankingFixtures.player(players, name, "Club", League.PREMIER_LEAGUE, Position.FORWARD);
    }

    private static String ids(Player... requested) {
        return java.util.Arrays.stream(requested).map(player -> player.getId().toString())
                .collect(Collectors.joining(","));
    }
}
