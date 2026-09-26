package com.example.demo.adapter.whoscored;

import com.example.demo.adapter.whoscored.dto.WhoScoredFeedResponse;
import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.exception.WhoScoredException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class WhoScoredStatsMapperTest {
    private static final Instant FETCHED_AT = Instant.parse("2026-09-25T13:30:00Z");

    private final WhoScoredStatsMapper mapper = new WhoScoredStatsMapper(JsonMapper.builder().build());

    @Test
    void mergesTheFourResponsesIntoTotalsPerPlayerAndTeam() {
        List<WhoScoredPlayerStats> rows = mergeLeague();

        WhoScoredPlayerStats bruno = row(rows, "123761", "32");
        assertThat(bruno.name()).isEqualTo("Bruno Fernandes");
        assertThat(bruno.minutesPlayed()).isEqualTo(450);
        assertThat(bruno.goals()).isEqualTo(3);
        assertThat(bruno.assists()).isEqualTo(1);
        assertThat(bruno.yellowCards()).isZero();
        assertThat(bruno.redCards()).isZero();
        assertThat(bruno.rating()).isEqualByComparingTo("7.39");
        assertThat(bruno.shots()).isEqualTo(19);
        assertThat(bruno.keyPasses()).isEqualTo(13);
        assertThat(bruno.tackles()).isEqualTo(5);
        assertThat(bruno.fetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void roundsRatingToTwoDecimalsAndReadsDecimalCardsAsIntegers() {
        List<WhoScoredPlayerStats> rows = mergeLeague();

        assertThat(row(rows, "315227", "167").rating()).isEqualByComparingTo("7.61");
        assertThat(row(rows, "395692", "15").yellowCards()).isEqualTo(1);
    }

    @Test
    void collectsEveryTeamNameThatTheSourcePublishesForATeam() {
        List<WhoScoredPlayerStats> rows = mergeLeague();

        assertThat(row(rows, "123761", "32").teamNames()).containsExactly("Man Utd", "Manchester United");
        assertThat(row(rows, "247454", "13").teamNames()).containsExactly("Arsenal");
    }

    @Test
    void keepsOneRowPerTeamForATransferredPlayer() {
        List<WhoScoredPlayerStats> rows = mergeLeague();

        assertThat(rows).filteredOn(r -> r.whoscoredPlayerId().equals("369430")).hasSize(2);
        WhoScoredPlayerStats city = row(rows, "369430", "167");
        WhoScoredPlayerStats chelsea = row(rows, "369430", "15");
        assertThat(city.name()).isEqualTo("Enzo Fernández");
        assertThat(city.minutesPlayed()).isEqualTo(248);
        assertThat(city.shots()).isEqualTo(5);
        assertThat(chelsea.minutesPlayed()).isEqualTo(25);
        assertThat(chelsea.shots()).isZero();
    }

    @Test
    void metricMissingFromOneResponseIsNullAndTheRestIsKept() {
        WhoScoredPlayerStats mbeumo = row(mergeLeague(), "353377", "32");

        assertThat(mbeumo.keyPasses()).isNull();
        assertThat(mbeumo.shots()).isEqualTo(19);
        assertThat(mbeumo.tackles()).isEqualTo(5);
        assertThat(mbeumo.hasAnyMetric()).isTrue();
    }

    @Test
    void summaryDefinesTheRowsAndDetailedRowsWithoutSummaryAreIgnored() {
        WhoScoredFeedResponse onlyBruno = mapper.parse("""
                {"playerTableStats":[{"playerId":123761,"name":"Bruno Fernandes","teamId":32,"teamName":"Man Utd","goal":3}]}""");
        List<WhoScoredPlayerStats> rows = mapper.merge(onlyBruno, parse("league-shots.json"),
                parse("league-key-passes.json"), parse("league-tackles.json"), FETCHED_AT);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.whoscoredPlayerId()).isEqualTo("123761");
            assertThat(r.minutesPlayed()).isNull();
            assertThat(r.shots()).isEqualTo(19);
        });
    }

    @Test
    void parsesBodyWithLeadingWhitespace() {
        assertThat(parse("league-summary.json").playerTableStats()).hasSize(8);
    }

    // --- US5: valores inválidos y estructura inesperada -------------------------------------------

    @Test
    void invalidValuesBecomeNullAreLoggedAndTheRestOfTheRowIsKept(CapturedOutput output) {
        List<WhoScoredPlayerStats> rows = mergeSummaryOnly("synthetic-summary-invalid-values.json");

        WhoScoredPlayerStats mainoo = row(rows, "900001", "32");
        assertThat(mainoo.yellowCards()).isNull();
        assertThat(mainoo.rating()).isNull();
        assertThat(mainoo.goals()).isNull();
        assertThat(mainoo.minutesPlayed()).isEqualTo(357);
        assertThat(mainoo.assists()).isEqualTo(1);
        assertThat(mainoo.redCards()).isZero();

        WhoScoredPlayerStats shaw = row(rows, "900002", "32");
        assertThat(shaw.minutesPlayed()).isNull();
        assertThat(shaw.rating()).isNull();
        assertThat(shaw.goals()).isZero();

        assertThat(output)
                .contains("whoscored_stats_invalid_metric whoscoredPlayerId=900001 metric=yellowCards")
                .contains("whoscored_stats_invalid_metric whoscoredPlayerId=900001 metric=rating")
                .contains("whoscored_stats_invalid_metric whoscoredPlayerId=900002 metric=minutesPlayed")
                .contains("whoscored_stats_invalid_metric whoscoredPlayerId=900002 metric=rating")
                .doesNotContain("whoscoredPlayerId=900001 metric=goals");
    }

    @Test
    void rowWithoutPlayerIdIsSkippedAndHomonymsAreKeptAsSeparateRows() {
        List<WhoScoredPlayerStats> rows = mergeSummaryOnly("synthetic-summary-invalid-values.json");

        assertThat(rows).extracting(WhoScoredPlayerStats::whoscoredPlayerId)
                .containsExactly("900001", "900002", "900003", "900004");
        assertThat(rows).filteredOn(r -> r.name().equals("Luke Shaw")).hasSize(2);
    }

    @Test
    void rowWithOnlyNullValuesHasNoMetrics() {
        WhoScoredPlayerStats heaton = row(mergeSummaryOnly("synthetic-summary-invalid-values.json"), "900004", "32");

        assertThat(heaton.hasAnyMetric()).isFalse();
    }

    @Test
    void jsonWithoutPlayerTableIsUnexpectedStructure() {
        assertUnexpectedStructure(() -> mapper.parse(fixture("synthetic-unexpected-structure.json")));
    }

    @Test
    void invalidJsonIsUnexpectedStructure() {
        assertUnexpectedStructure(() -> mapper.parse("{\"playerTableStats\": [ {\"playerId\": "));
    }

    @Test
    void rowsWithoutAnyIdentityAreUnexpectedStructure() {
        WhoScoredFeedResponse noIdentity = mapper.parse("{\"playerTableStats\":[{\"goal\":1},{\"name\":\"X\"}]}");

        assertUnexpectedStructure(() -> mapper.merge(noIdentity, empty(), empty(), empty(), FETCHED_AT));
    }

    @Test
    void emptyLeagueIsNotAnError() {
        assertThat(mapper.merge(empty(), empty(), empty(), empty(), FETCHED_AT)).isEmpty();
    }

    private List<WhoScoredPlayerStats> mergeSummaryOnly(String summaryFixture) {
        return mapper.merge(parse(summaryFixture), empty(), empty(), empty(), FETCHED_AT);
    }

    private WhoScoredFeedResponse empty() {
        return mapper.parse("{\"playerTableStats\":[]}");
    }

    private static void assertUnexpectedStructure(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(WhoScoredException.class)
                .extracting(ex -> ((WhoScoredException) ex).getCode())
                .isEqualTo(WhoScoredException.UNEXPECTED_STRUCTURE);
    }

    private List<WhoScoredPlayerStats> mergeLeague() {
        return mapper.merge(parse("league-summary.json"), parse("league-shots.json"),
                parse("league-key-passes.json"), parse("league-tackles.json"), FETCHED_AT);
    }

    private WhoScoredFeedResponse parse(String fixture) {
        return mapper.parse(fixture(fixture));
    }

    private static WhoScoredPlayerStats row(List<WhoScoredPlayerStats> rows, String playerId, String teamId) {
        return rows.stream()
                .filter(r -> r.whoscoredPlayerId().equals(playerId) && r.whoscoredTeamId().equals(teamId))
                .findFirst().orElseThrow();
    }

    static String fixture(String name) {
        try (InputStream in = WhoScoredStatsMapperTest.class.getResourceAsStream("/whoscored/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
