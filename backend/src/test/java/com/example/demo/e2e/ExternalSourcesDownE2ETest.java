package com.example.demo.e2e;

import com.example.demo.adapter.footballdata.FootballDataAdapter;
import com.example.demo.adapter.footballdata.dto.FootballDataResponse;
import com.example.demo.config.CacheConfig;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.LoginResponse;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.RegisterResponse;
import com.example.demo.dto.player.PlayerResponse;
import com.example.demo.dto.player.PlayerSyncResponse;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.support.E2EFixtures;
import com.example.demo.support.E2EFixtures.FixtureSet;
import com.example.demo.support.E2ETest;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

@E2ETest
class ExternalSourcesDownE2ETest {

    private static final Duration GET_HTTP_TIMEOUT = Duration.ofMillis(100);
    private static final Duration SYNC_HTTP_TIMEOUT = Duration.ofMillis(500);
    private static final String USERNAME = "e2e-resilience-user";
    private static final String PASSWORD = "e2e-resilience-password";
    private static final ParameterizedTypeReference<List<PlayerResponse>> PLAYER_LIST_TYPE =
            new ParameterizedTypeReference<>() { };

    @LocalServerPort
    private int port;

    @Autowired
    private E2EFixtures fixtures;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private FootballDataAdapter footballDataAdapter;

    private RestClient client;
    private String bearerToken;

    @BeforeEach
    void authenticate() {
        client = RestClient.create("http://localhost:" + port);

        ResponseEntity<RegisterResponse> register = client.post()
                .uri("/auth/register")
                .body(new RegisterRequest(USERNAME, "e2e-resilience@example.com", PASSWORD))
                .retrieve()
                .toEntity(RegisterResponse.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<LoginResponse> login = client.post()
                .uri("/auth/login")
                .body(new LoginRequest(USERNAME, PASSWORD))
                .retrieve()
                .toEntity(LoginResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().tokenType()).isEqualTo("Bearer");
        assertThat(login.getBody().token()).isNotBlank();
        bearerToken = login.getBody().token();
    }

    @Test
    void returnsLocalPlayersWithoutFiltersWhenSourcesAreDown() {
        assertThat(getPlayers()).isEmpty();

        FixtureSet fixtureSet = fixtures.createPlayersFromFiveLeagues();

        assertThat(getPlayers()).containsExactlyElementsOf(expectedResponses(fixtureSet.all()));
    }

    @Test
    void filtersLocalPlayersByLeagueWhenSourcesAreDown() {
        FixtureSet fixtureSet = fixtures.createPlayersFromFiveLeagues();

        List<PlayerResponse> response = getPlayers("league", League.PREMIER_LEAGUE);

        assertThat(response).containsExactly(expectedResponse(fixtureSet.forLeague(League.PREMIER_LEAGUE)));
    }

    @Test
    void filtersLocalPlayersByTeamWhenSourcesAreDown() {
        FixtureSet fixtureSet = fixtures.createPlayersFromFiveLeagues();

        List<PlayerResponse> response = getPlayers("team", "Real Madrid");
        assertThat(response).containsExactly(expectedResponse(fixtureSet.forTeam("Real Madrid")));
        assertThat(getPlayers("team", "E2E Missing Team")).isEmpty();
    }

    @Test
    void filtersLocalPlayersByPositionWhenSourcesAreDown() {
        FixtureSet fixtureSet = fixtures.createPlayersFromFiveLeagues();

        List<PlayerResponse> response = getPlayers("position", Position.MIDFIELDER);

        assertThat(response).containsExactlyElementsOf(
                expectedResponses(fixtureSet.withPosition(Position.MIDFIELDER)));
    }

    @Test
    void combinesLeagueTeamAndPositionWhenSourcesAreDown() {
        FixtureSet fixtureSet = fixtures.createPlayersFromFiveLeagues();

        List<PlayerResponse> response = getPlayers(uriBuilder -> uriBuilder.path("/players")
                        .queryParam("league", League.PREMIER_LEAGUE)
                        .queryParam("team", "Arsenal FC")
                        .queryParam("position", Position.FORWARD)
                        .build());

        assertThat(response).containsExactly(
                expectedResponse(fixtureSet.forLeague(League.PREMIER_LEAGUE)));
    }

    @Test
    void failedSyncReturnsFailedAndPreservesExistingPlayers() {
        FixtureSet fixtureSet = fixtures.createPlayersFromFiveLeagues();
        List<PlayerResponse> before = getPlayers();
        assertThat(before).containsExactlyElementsOf(expectedResponses(fixtureSet.all()));

        PlayerSyncResponse sync = postSync();

        assertThat(sync).isEqualTo(new PlayerSyncResponse("FAILED", 0, 5, 0));
        assertThat(getPlayers()).containsExactlyElementsOf(before);
    }

    @Test
    void syncUsesTypedRedisCacheWithoutCallingFootballData() {
        assertThat(getPlayers()).isEmpty();
        Cache cache = cacheManager.getCache(CacheConfig.COMPETITION_TEAMS_CACHE);
        assertThat(cache).isNotNull();
        cachedCompetitions().forEach(competition -> cache.put(competition.code(), competition.response()));
        reset(footballDataAdapter);

        PlayerSyncResponse sync = postSync();

        assertThat(sync).isEqualTo(new PlayerSyncResponse("COMPLETED", 5, 0, 0));
        verifyNoInteractions(footballDataAdapter);
        assertThat(getPlayers())
                .extracting(PlayerResponse::externalId, PlayerResponse::fullName, PlayerResponse::team,
                        PlayerResponse::league, PlayerResponse::positions, PlayerResponse::nationality,
                        PlayerResponse::age, PlayerResponse::marketValue)
                .containsExactlyInAnyOrderElementsOf(cachedExpectedFields());
    }

    private List<PlayerResponse> getPlayers() {
        return getPlayers(uriBuilder -> uriBuilder.path("/players").build());
    }

    private List<PlayerResponse> getPlayers(String parameter, Object value) {
        return getPlayers(uriBuilder -> uriBuilder.path("/players").queryParam(parameter, value).build());
    }

    private List<PlayerResponse> getPlayers(Function<UriBuilder, URI> uriFunction) {
        ResponseEntity<List<PlayerResponse>> warmUp = requestPlayers(uriFunction);
        assertOk(warmUp);

        ResponseEntity<List<PlayerResponse>> response = withinLimit(GET_HTTP_TIMEOUT,
                () -> requestPlayers(uriFunction));
        assertOk(response);
        return sorted(response.getBody());
    }

    private ResponseEntity<List<PlayerResponse>> requestPlayers(Function<UriBuilder, URI> uriFunction) {
        return client.get()
                .uri(uriFunction)
                .header(HttpHeaders.AUTHORIZATION, authorization())
                .retrieve()
                .toEntity(PLAYER_LIST_TYPE);
    }

    private PlayerSyncResponse postSync() {
        ResponseEntity<PlayerSyncResponse> warmUp = requestSync();
        assertOk(warmUp);

        ResponseEntity<PlayerSyncResponse> response = withinLimit(SYNC_HTTP_TIMEOUT, this::requestSync);
        assertOk(response);
        return response.getBody();
    }

    private ResponseEntity<PlayerSyncResponse> requestSync() {
        return client.post()
                .uri("/players/sync")
                .header(HttpHeaders.AUTHORIZATION, authorization())
                .retrieve()
                .toEntity(PlayerSyncResponse.class);
    }

    private String authorization() {
        return "Bearer " + bearerToken;
    }

    private static <T> ResponseEntity<T> withinLimit(Duration timeout,
                                                       Supplier<ResponseEntity<T>> request) {
        return assertTimeout(timeout, request::get);
    }

    private static void assertOk(ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    private static List<PlayerResponse> sorted(List<PlayerResponse> responses) {
        assertThat(responses).isNotNull();
        return responses.stream().sorted(Comparator.comparing(PlayerResponse::externalId)).toList();
    }

    private static List<PlayerResponse> expectedResponses(List<Player> players) {
        return players.stream()
                .map(ExternalSourcesDownE2ETest::expectedResponse)
                .sorted(Comparator.comparing(PlayerResponse::externalId))
                .toList();
    }

    private static PlayerResponse expectedResponse(Player player) {
        return new PlayerResponse(player.getId(), player.getExternalId(), player.getFullName(), player.getTeam(),
                player.getLeague(), player.getPositions(), player.getNationality(), player.getAge(),
                player.getMarketValue());
    }

    private static List<CachedCompetition> cachedCompetitions() {
        return List.of(
                new CachedCompetition("PL", League.PREMIER_LEAGUE, cachedPremierLeague()),
                new CachedCompetition("PD", League.LA_LIGA, cachedLaLiga()),
                new CachedCompetition("SA", League.SERIE_A, cachedSerieA()),
                new CachedCompetition("BL1", League.BUNDESLIGA, cachedBundesliga()),
                new CachedCompetition("FL1", League.LIGUE_1, cachedLigueOne()));
    }

    private static FootballDataResponse cachedPremierLeague() {
        return response("92001", "Cached Premier Forward", "Cached Arsenal", "FORWARD",
                LocalDate.of(2000, 1, 2), "England");
    }

    private static FootballDataResponse cachedLaLiga() {
        return response("92002", "Cached Liga Defender", "Cached Madrid", "DEFENDER",
                LocalDate.of(1999, 2, 3), "Spain");
    }

    private static FootballDataResponse cachedSerieA() {
        return response("92003", "Cached Serie Goalkeeper", "Cached Inter", "GOALKEEPER",
                LocalDate.of(1998, 3, 4), "Italy");
    }

    private static FootballDataResponse cachedBundesliga() {
        return response("92004", "Cached Bundesliga Midfielder", "Cached Bayern", "MIDFIELDER",
                LocalDate.of(2001, 4, 5), "Germany");
    }

    private static FootballDataResponse cachedLigueOne() {
        return response("92005", "Cached Ligue Defender", "Cached Paris", "DEFENCE",
                LocalDate.of(2002, 5, 6), "France");
    }

    private static FootballDataResponse response(String externalId, String name, String team, String position,
                                                 LocalDate dateOfBirth, String nationality) {
        FootballDataResponse.SquadMember member = new FootballDataResponse.SquadMember(
                Long.valueOf(externalId), name, position, dateOfBirth, nationality);
        return new FootballDataResponse(List.of(new FootballDataResponse.Team(team, List.of(member))));
    }

    private static List<Tuple> cachedExpectedFields() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return cachedCompetitions().stream()
                .map(competition -> {
                    FootballDataResponse.Team team = competition.response().teams().get(0);
                    FootballDataResponse.SquadMember member = team.squad().get(0);
                    Position position = switch (member.position()) {
                        case "FORWARD" -> Position.FORWARD;
                        case "MIDFIELDER" -> Position.MIDFIELDER;
                        case "GOALKEEPER" -> Position.GOALKEEPER;
                        default -> Position.DEFENDER;
                    };
                    return Tuple.tuple(member.id().toString(), member.name(), team.name(),
                            competition.league(), Set.of(position), member.nationality(),
                            Period.between(member.dateOfBirth(), today).getYears(), new BigDecimal("1.00"));
                })
                .toList();
    }

    private record CachedCompetition(String code, League league, FootballDataResponse response) {
    }
}
