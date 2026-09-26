package com.example.demo.adapter.whoscored;

import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.config.WhoScoredProperties;
import com.example.demo.exception.WhoScoredException;
import com.example.demo.model.League;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.example.demo.adapter.whoscored.WhoScoredStatsMapperTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class WhoScoredAdapterTest {
    static final String BASE_URL = "https://ws.test";
    static final MediaType TEXT_HTML_UTF8 = MediaType.parseMediaType("text/html;charset=utf-8");

    MockRestServiceServer server;
    WhoScoredStatsMapper mapper;
    WhoScoredAdapter adapter;

    void setUp(Duration requestDelay, boolean retryEnabled, Duration retryDelay) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, "test-agent");
        server = MockRestServiceServer.bindTo(builder).build();
        mapper = spy(new WhoScoredStatsMapper(JsonMapper.builder().build()));
        adapter = new WhoScoredAdapter(new HttpFeedClient(builder.build()),
                properties(requestDelay, retryEnabled, retryDelay), mapper);
    }

    @Test
    void requestsTheFourLeagueQueriesAndMergesThem() {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(withSuccess(fixture("league-summary.json"), TEXT_HTML_UTF8));
        expectQuery("shots", "zones", "2").andRespond(withSuccess(fixture("league-shots.json"), TEXT_HTML_UTF8));
        expectQuery("key-passes", "length", "2")
                .andRespond(withSuccess(fixture("league-key-passes.json"), TEXT_HTML_UTF8));
        expectQuery("tackles", "success", "2")
                .andRespond(withSuccess(fixture("league-tackles.json"), TEXT_HTML_UTF8));

        List<WhoScoredPlayerStats> rows = adapter.fetchLeaguePlayers(League.PREMIER_LEAGUE);

        server.verify();
        assertThat(rows).hasSize(8);
        assertThat(rows).filteredOn(r -> r.whoscoredPlayerId().equals("123761")).singleElement()
                .satisfies(bruno -> {
                    assertThat(bruno.shots()).isEqualTo(19);
                    assertThat(bruno.keyPasses()).isEqualTo(13);
                    assertThat(bruno.tackles()).isEqualTo(5);
                });
        assertThat(rows).extracting(WhoScoredPlayerStats::fetchedAt).containsOnly(rows.get(0).fetchedAt());
    }

    @Test
    void waitsTheConfiguredDelayBetweenRealRequests() {
        setUp(Duration.ofMillis(50), true, Duration.ZERO);
        expectFullLeague();

        long start = System.nanoTime();
        adapter.fetchLeaguePlayers(League.PREMIER_LEAGUE);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        server.verify();
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(150);
    }

    // --- US5: bloqueos, errores y reintento --------------------------------------------------------

    @Test
    void forbiddenWithCloudflareChallengeIsBlockedAndNeverParsedAsJson() {
        setUp(Duration.ZERO, false, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(forbidden("blocked-cloudflare.html"));

        assertCode(WhoScoredException.BLOCKED);
        server.verify();
        verify(mapper, never()).parse(anyString());
    }

    @Test
    void challengeBodyWithStatus200IsAlsoBlocked() {
        setUp(Duration.ZERO, false, Duration.ZERO);
        expectQuery("summary", "all", "0")
                .andRespond(withSuccess(fixture("blocked-cloudflare.html"), TEXT_HTML_UTF8));

        assertCode(WhoScoredException.BLOCKED);
        verify(mapper, never()).parse(anyString());
    }

    @Test
    void incapsulaChallengeIsBlocked() {
        setUp(Duration.ZERO, false, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(forbidden("blocked-incapsula.html"));

        assertCode(WhoScoredException.BLOCKED);
        verify(mapper, never()).parse(anyString());
    }

    @Test
    void blockedRequestIsRetriedOnceAndRecovers(CapturedOutput output) {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(forbidden("blocked-cloudflare.html"));
        expectFullLeague();

        List<WhoScoredPlayerStats> rows = adapter.fetchLeaguePlayers(League.PREMIER_LEAGUE);

        server.verify();
        assertThat(rows).hasSize(8);
        assertThat(output).contains("whoscored_stats_request_blocked league=PREMIER_LEAGUE category=summary attempt=1")
                .doesNotContain("attempt=2");
    }

    @Test
    void blockedTwiceFailsWithoutAThirdAttempt(CapturedOutput output) {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(withSuccess(fixture("league-summary.json"), TEXT_HTML_UTF8));
        expectQuery("shots", "zones", "2").andRespond(forbidden("blocked-cloudflare.html"));
        expectQuery("shots", "zones", "2").andRespond(forbidden("blocked-cloudflare.html"));

        assertCode(WhoScoredException.BLOCKED);
        server.verify();
        assertThat(output).contains("category=shots attempt=1").contains("category=shots attempt=2");
    }

    @Test
    void retryDisabledFailsOnFirstBlock() {
        setUp(Duration.ZERO, false, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(forbidden("blocked-cloudflare.html"));

        assertCode(WhoScoredException.BLOCKED);
        server.verify();
    }

    @Test
    void retryWaitsTheConfiguredBlockRetryDelay() {
        setUp(Duration.ZERO, true, Duration.ofMillis(80));
        expectQuery("summary", "all", "0").andRespond(forbidden("blocked-cloudflare.html"));
        expectFullLeague();

        long start = System.nanoTime();
        adapter.fetchLeaguePlayers(League.PREMIER_LEAGUE);

        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isGreaterThanOrEqualTo(80);
    }

    @Test
    void redirectIsHttpErrorWithoutRetry() {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0")
                .andRespond(withStatus(HttpStatus.FOUND).location(URI.create("/404.html")));

        assertCode(WhoScoredException.HTTP_ERROR);
        server.verify();
    }

    @Test
    void serverErrorIsHttpErrorWithoutRetry() {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(withServerError());

        assertCode(WhoScoredException.HTTP_ERROR);
        server.verify();
    }

    @Test
    void socketTimeoutIsTimeoutWithoutRetry() {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertCode(WhoScoredException.TIMEOUT);
        server.verify();
    }

    @Test
    void nonJsonBodyIsUnexpectedStructureAndNeverParsed() {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0")
                .andRespond(withSuccess("<html><body>Maintenance</body></html>", TEXT_HTML_UTF8));

        assertCode(WhoScoredException.UNEXPECTED_STRUCTURE);
        server.verify();
        verify(mapper, never()).parse(anyString());
    }

    @Test
    void jsonWithoutPlayerTableIsUnexpectedStructure() {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0")
                .andRespond(withSuccess(fixture("synthetic-unexpected-structure.json"), TEXT_HTML_UTF8));

        assertCode(WhoScoredException.UNEXPECTED_STRUCTURE);
    }

    @Test
    void failureOfTheFourthQueryReturnsNothingForTheLeague() {
        setUp(Duration.ZERO, true, Duration.ZERO);
        expectQuery("summary", "all", "0").andRespond(withSuccess(fixture("league-summary.json"), TEXT_HTML_UTF8));
        expectQuery("shots", "zones", "2").andRespond(withSuccess(fixture("league-shots.json"), TEXT_HTML_UTF8));
        expectQuery("key-passes", "length", "2")
                .andRespond(withSuccess(fixture("league-key-passes.json"), TEXT_HTML_UTF8));
        expectQuery("tackles", "success", "2").andRespond(withServerError());

        assertCode(WhoScoredException.HTTP_ERROR);
        server.verify();
        verify(mapper, never()).merge(any(), any(), any(), any(), any());
    }

    void assertCode(String code) {
        assertThatThrownBy(() -> adapter.fetchLeaguePlayers(League.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredException.class)
                .extracting(ex -> ((WhoScoredException) ex).getCode()).isEqualTo(code);
    }

    static ResponseCreator forbidden(String fixture) {
        return withStatus(HttpStatus.FORBIDDEN).contentType(TEXT_HTML_UTF8).body(fixture(fixture));
    }

    void expectFullLeague() {
        expectQuery("summary", "all", "0").andRespond(withSuccess(fixture("league-summary.json"), TEXT_HTML_UTF8));
        expectQuery("shots", "zones", "2").andRespond(withSuccess(fixture("league-shots.json"), TEXT_HTML_UTF8));
        expectQuery("key-passes", "length", "2")
                .andRespond(withSuccess(fixture("league-key-passes.json"), TEXT_HTML_UTF8));
        expectQuery("tackles", "success", "2")
                .andRespond(withSuccess(fixture("league-tackles.json"), TEXT_HTML_UTF8));
    }

    ResponseActions expectQuery(String category, String subcategory, String accumulation) {
        return server.expect(once(), requestTo(startsWith(BASE_URL + "/statisticsfeed/1/getplayerstatistics?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "test-agent"))
                .andExpect(queryParam("category", category))
                .andExpect(queryParam("subcategory", subcategory))
                .andExpect(queryParam("statsAccumulationType", accumulation))
                .andExpect(queryParam("tournamentOptions", "2"))
                .andExpect(queryParam("isCurrent", "true"))
                .andExpect(queryParam("field", "Overall"))
                .andExpect(queryParam("sortBy", "Rating"))
                .andExpect(queryParam("isMinApp", "false"))
                .andExpect(queryParam("includeZeroValues", "true"))
                .andExpect(queryParam("teamIds", ""))
                .andExpect(queryParam("numberOfPlayersToPick", ""));
    }

    static WhoScoredProperties properties(Duration requestDelay, boolean retryEnabled, Duration retryDelay) {
        Map<League, Integer> tournaments = new EnumMap<>(League.class);
        for (League league : League.values()) {
            tournaments.put(league, league.ordinal() + 10);
        }
        tournaments.put(League.PREMIER_LEAGUE, 2);
        return new WhoScoredProperties(URI.create(BASE_URL), "test-agent", Duration.ofSeconds(1),
                Duration.ofSeconds(1), requestDelay, Duration.ofHours(24),
                new WhoScoredProperties.BlockRetry(retryEnabled, retryDelay), tournaments,
                new WhoScoredProperties.Sync(false, "0 0 4 * * MON", "UTC"), WhoScoredProperties.Client.HTTP,
                new WhoScoredProperties.Browser("/regions/252/tournaments/2/england-premier-league", Duration.ofSeconds(1)));
    }
}
