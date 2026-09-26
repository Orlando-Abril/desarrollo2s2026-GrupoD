package com.example.demo.service;

import com.example.demo.adapter.whoscored.WhoScoredAdapter;
import com.example.demo.adapter.whoscored.dto.WhoScoredPlayerStats;
import com.example.demo.config.CacheConfig;
import com.example.demo.exception.WhoScoredException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerStats;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerStatsServiceTest {
    static final Instant FETCHED_AT = Instant.parse("2026-09-25T13:30:00Z");

    @Mock PlayerRepository playerRepository;
    @Mock PlayerStatsRepository statsRepository;
    @Mock WhoScoredAdapter adapter;
    PlayerStatsService service;
    CacheManager cacheManager;

    final List<Player> players = new ArrayList<>();
    long nextId = 1;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(CacheConfig.WHOSCORED_PLAYER_STATS_CACHE);
        service = new PlayerStatsService(playerRepository, statsRepository, adapter,
                TransactionOperations.withoutTransaction(), cacheManager);
        when(playerRepository.findAll()).thenReturn(players);
        when(statsRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(statsRepository.save(any(PlayerStats.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // --- US1: obtener y guardar -------------------------------------------------------------------

    @Test
    void savesAllNineMetricsWithTheSourceValues() {
        Player bruno = player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, row("123761", "Bruno Fernandes", "32", List.of("Man Utd", "Manchester United"),
                450, 3, 1, 19, 13, 5, 0, 0, "7.39"));

        StatsUpdateResult result = service.updateAllStats();

        PlayerStats saved = savedStats();
        assertThat(saved.getPlayer()).isSameAs(bruno);
        assertThat(saved.getWhoscoredPlayerId()).isEqualTo("123761");
        assertThat(saved.getMinutesPlayed()).isEqualTo(450);
        assertThat(saved.getGoals()).isEqualTo(3);
        assertThat(saved.getAssists()).isEqualTo(1);
        assertThat(saved.getShots()).isEqualTo(19);
        assertThat(saved.getKeyPasses()).isEqualTo(13);
        assertThat(saved.getTackles()).isEqualTo(5);
        assertThat(saved.getYellowCards()).isZero();
        assertThat(saved.getRedCards()).isZero();
        assertThat(saved.getRating()).isEqualByComparingTo("7.39");
        assertThat(saved.getFetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(result).isEqualTo(new StatsUpdateResult(1, 1, 0, 0, 0));
    }

    @Test
    void metricsNotReportedBySourceAreStoredAsNullNotZero() {
        player("Bryan Mbeumo", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, row("353377", "Bryan Mbeumo", "32", List.of("Man Utd", "Manchester United"),
                450, 2, null, 19, null, 5, 0, 0, null));

        service.updateAllStats();

        PlayerStats saved = savedStats();
        assertThat(saved.getAssists()).isNull();
        assertThat(saved.getKeyPasses()).isNull();
        assertThat(saved.getRating()).isNull();
        assertThat(saved.getGoals()).isEqualTo(2);
    }

    @Test
    void replacesThePreviousSetEntirelyIncludingNullsAndFetchedAt() {
        Player bruno = player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        PlayerStats previous = previousStats(bruno);
        leagueRows(League.PREMIER_LEAGUE, row("123761", "Bruno Fernandes", "32", List.of("Man Utd", "Manchester United"),
                500, 4, null, null, null, null, null, null, "7.10"));

        service.updateAllStats();

        PlayerStats saved = savedStats();
        assertThat(saved).isSameAs(previous);
        assertThat(saved.getMinutesPlayed()).isEqualTo(500);
        assertThat(saved.getGoals()).isEqualTo(4);
        assertThat(saved.getAssists()).isNull();
        assertThat(saved.getShots()).isNull();
        assertThat(saved.getTackles()).isNull();
        assertThat(saved.getRating()).isEqualByComparingTo("7.10");
        assertThat(saved.getFetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void onlyReadsTheCatalogAndOnlyQueriesLeaguesWithPlayers() {
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, row("123761", "Bruno Fernandes", "32", List.of("Man Utd", "Manchester United"),
                450, 3, 1, 19, 13, 5, 0, 0, "7.39"));

        service.updateAllStats();

        verify(playerRepository).findAll();
        verifyNoMoreInteractions(playerRepository);
        verify(adapter).fetchLeaguePlayers(League.PREMIER_LEAGUE);
        verify(adapter).endRun();
        verifyNoMoreInteractions(adapter);
    }

    // --- US2: asociar sólo al jugador correcto --------------------------------------------------

    @Test
    void matchesIgnoringCaseDiacriticsAndPunctuation() {
        Player odegaard = player("MARTIN ODEGAARD", "Arsenal FC", League.PREMIER_LEAGUE);
        Player kante = player("N'Golo Kanté", "Arsenal FC", League.PREMIER_LEAGUE);
        Player enzo = player("Enzo Fernandez", "Chelsea FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE,
                simpleRow("247454", "Martin Ødegaard", "13", "Arsenal"),
                simpleRow("600000", "N'Golo  Kante", "13", "Arsenal"),
                simpleRow("369430", "Enzo Fernández", "15", "Chelsea"));

        StatsUpdateResult result = service.updateAllStats();

        assertThat(allSavedStats(3)).extracting(PlayerStats::getPlayer).containsExactly(odegaard, kante, enzo);
        assertThat(result).isEqualTo(new StatsUpdateResult(3, 3, 0, 0, 0));
    }

    @Test
    void matchesAnyTeamNamePublishedByTheSourceButNothingElse() {
        Player bruno = player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        player("Matheus Cunha", "Wolverhampton Wanderers FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE,
                simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"),
                simpleRow("91909", "Matheus Cunha", "161", "Wolves"));

        StatsUpdateResult result = service.updateAllStats();

        assertThat(savedStats().getPlayer()).isSameAs(bruno);
        assertThat(result).isEqualTo(new StatsUpdateResult(2, 1, 0, 1, 0));
    }

    @Test
    void playerWithoutMatchIsLoggedAndKeepsPreviousStats(CapturedOutput output) {
        Player ghost = player("Jugador Inexistente", "Arsenal FC", League.PREMIER_LEAGUE);
        PlayerStats previous = previousStats(ghost);
        leagueRows(League.PREMIER_LEAGUE, simpleRow("247454", "Martin Ødegaard", "13", "Arsenal"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(previous.getMinutesPlayed()).isEqualTo(100);
        assertThat(previous.getWhoscoredPlayerId()).isEqualTo("old");
        assertThat(result).isEqualTo(new StatsUpdateResult(1, 0, 0, 1, 0));
        assertThat(output).contains("whoscored_stats_unmatched playerId=" + ghost.getId()
                + " team=Arsenal FC reason=player_not_found");
    }

    @Test
    void homonymsInTheSameTeamAreNotAssociated(CapturedOutput output) {
        player("Luke Shaw", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE,
                simpleRow("900002", "Luke Shaw", "32", "Man Utd", "Manchester United"),
                simpleRow("900003", "Luke Shaw", "32", "Man Utd", "Manchester United"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(result.unmatched()).isOne();
        assertThat(output).contains("reason=player_ambiguous");
    }

    @Test
    void sameNameInAnotherTeamIsNotAssociated(CapturedOutput output) {
        player("Cole Palmer", "Manchester City FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE,
                simpleRow("395692", "Cole Palmer", "15", "Chelsea"),
                simpleRow("315227", "Erling Haaland", "167", "Man City", "Manchester City"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(result.unmatched()).isOne();
        assertThat(output).contains("reason=player_not_found");
    }

    @Test
    void teamWithoutCounterpartLeavesAllItsPlayersUnmatched(CapturedOutput output) {
        player("Matheus Cunha", "Wolverhampton Wanderers FC", League.PREMIER_LEAGUE);
        player("Jorgen Strand Larsen", "Wolverhampton Wanderers FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, simpleRow("91909", "Matheus Cunha", "161", "Wolves"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(result).isEqualTo(new StatsUpdateResult(2, 0, 0, 2, 0));
        assertThat(output).contains("reason=team_not_found");
    }

    @Test
    void twoSourceTeamsWithTheSameNormalizedNameAreAmbiguous(CapturedOutput output) {
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE,
                simpleRow("123761", "Bruno Fernandes", "32", "Manchester United"),
                simpleRow("999999", "Bruno Fernandes", "99", "Manchester United FC"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(result.unmatched()).isOne();
        assertThat(output).contains("reason=team_ambiguous");
    }

    // --- US2: equipo por inclusión (sólo si no hay coincidencia exacta; research V6) --------------

    @Test
    void localTeamStartingWithTheSourceNameIsMatched(CapturedOutput output) {
        Player son = player("Son Heung-min", "Tottenham Hotspur FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, simpleRow("91909", "Son Heung-min", "30", "Tottenham"));

        StatsUpdateResult result = service.updateAllStats();

        assertThat(savedStats().getPlayer()).isSameAs(son);
        assertThat(result.updated()).isOne();
        assertThat(output).contains("whoscored_stats_team_matched team=Tottenham Hotspur FC whoscoredTeam=Tottenham rule=prefix");
    }

    @Test
    void prefixIsCheckedOnCharactersSoInterMatchesAndMilanDoesNot() {
        Player lautaro = player("Lautaro Martinez", "FC Internazionale Milano", League.SERIE_A);
        leagueRows(League.SERIE_A,
                simpleRow("300000", "Lautaro Martínez", "75", "Inter"),
                simpleRow("300001", "Rafael Leao", "80", "AC Milan"));

        service.updateAllStats();

        assertThat(savedStats().getPlayer()).isSameAs(lautaro);
    }

    @Test
    void prefixWinsOverOtherTeamsContainedInTheName() {
        Player espanyolPlayer = player("Javi Puado", "RCD Espanyol de Barcelona", League.LA_LIGA);
        leagueRows(League.LA_LIGA,
                simpleRow("400000", "Javi Puado", "70", "Espanyol"),
                simpleRow("400001", "Javi Puado", "65", "Barcelona"));

        StatsUpdateResult result = service.updateAllStats();

        PlayerStats saved = savedStats();
        assertThat(saved.getPlayer()).isSameAs(espanyolPlayer);
        assertThat(saved.getWhoscoredPlayerId()).isEqualTo("400000");
        assertThat(result.unmatched()).isZero();
    }

    @Test
    void localTeamContainingAllWordsOfTheSourceNameIsMatched(CapturedOutput output) {
        Player greenwood = player("Mason Greenwood", "Olympique de Marseille", League.LIGUE_1);
        leagueRows(League.LIGUE_1, simpleRow("500000", "Mason Greenwood", "249", "Marseille"));

        service.updateAllStats();

        assertThat(savedStats().getPlayer()).isSameAs(greenwood);
        assertThat(output).contains("whoscoredTeam=Marseille rule=contains");
    }

    @Test
    void exactMatchTakesPrecedenceOverInclusion() {
        Player player = player("Some Player", "Real Madrid CF", League.LA_LIGA);
        leagueRows(League.LA_LIGA,
                simpleRow("600001", "Some Player", "52", "Real Madrid"),
                simpleRow("600002", "Some Player", "53", "Real"));

        service.updateAllStats();

        assertThat(savedStats().getWhoscoredPlayerId()).isEqualTo("600001");
        assertThat(savedStats().getPlayer()).isSameAs(player);
    }

    @Test
    void moreThanOneIncludedTeamIsAmbiguous(CapturedOutput output) {
        player("Some Player", "Real Madrid Castilla Juvenil", League.LA_LIGA);
        leagueRows(League.LA_LIGA,
                simpleRow("600001", "Some Player", "52", "Real Madrid"),
                simpleRow("600002", "Some Player", "53", "Real Madrid Castilla"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(result.unmatched()).isOne();
        assertThat(output).contains("reason=team_ambiguous");
    }

    @Test
    void sourceNamesShorterThanFourCharactersAreNotUsedForInclusion(CapturedOutput output) {
        player("Some Player", "PSV Eindhoven", League.BUNDESLIGA);
        leagueRows(League.BUNDESLIGA, simpleRow("700000", "Some Player", "90", "PSV"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(result.unmatched()).isOne();
        assertThat(output).contains("reason=team_not_found");
    }

    // --- US2: equivalencias fijas de equipos (TeamAliases) -----------------------------------------

    @Test
    void knownEquivalenceMatchesTeamsWhoseNamesDoNotResemble(CapturedOutput output) {
        Player gladbachPlayer = player("Alassane Plea", "Borussia Mönchengladbach", League.BUNDESLIGA);
        Player lyonPlayer = player("Corentin Tolisso", "Olympique Lyonnais", League.LIGUE_1);
        Player rennesPlayer = player("Ludovic Blas", "Stade Rennais FC 1901", League.LIGUE_1);
        leagueRows(League.BUNDESLIGA,
                simpleRow("800001", "Alassane Pléa", "134", "Borussia M.Gladbach"),
                simpleRow("800002", "Julian Brandt", "44", "Borussia Dortmund"));
        leagueRows(League.LIGUE_1,
                simpleRow("800003", "Corentin Tolisso", "228", "Lyon"),
                simpleRow("800004", "Ludovic Blas", "313", "Rennes"));

        StatsUpdateResult result = service.updateAllStats();

        assertThat(allSavedStats(3)).extracting(PlayerStats::getPlayer)
                .containsExactlyInAnyOrder(gladbachPlayer, lyonPlayer, rennesPlayer);
        assertThat(result.unmatched()).isZero();
        assertThat(output).contains("team=Borussia Mönchengladbach whoscoredTeam=Borussia M.Gladbach rule=alias")
                .contains("whoscoredTeam=Lyon rule=alias").contains("whoscoredTeam=Rennes rule=alias");
    }

    @Test
    void equivalenceIsComparedOnNormalizedNames() {
        Player player = player("Alassane Plea", "BORUSSIA MONCHENGLADBACH", League.BUNDESLIGA);
        leagueRows(League.BUNDESLIGA, simpleRow("800001", "Alassane Plea", "134", "Borussia M. Gladbach"));

        service.updateAllStats();

        assertThat(savedStats().getPlayer()).isSameAs(player);
    }

    @Test
    void whenTheEquivalentTeamIsNotInTheLeagueTheUsualRulesApply(CapturedOutput output) {
        player("Corentin Tolisso", "Olympique Lyonnais", League.LIGUE_1);
        leagueRows(League.LIGUE_1, simpleRow("800005", "Corentin Tolisso", "300", "Lille"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(result.unmatched()).isOne();
        assertThat(output).contains("reason=team_not_found");
    }

    @Test
    void transferredPlayerOnlyUsesTheRowOfItsLocalTeam() {
        player("Enzo Fernández", "Chelsea FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE,
                row("369430", "Enzo Fernández", "167", List.of("Man City", "Manchester City"),
                        248, 1, 0, 5, 3, 3, 1, 0, "7.20"),
                row("369430", "Enzo Fernández", "15", List.of("Chelsea"), 25, 0, 0, 0, 1, 0, 0, 0, "6.14"));

        service.updateAllStats();

        PlayerStats saved = savedStats();
        assertThat(saved.getMinutesPlayed()).isEqualTo(25);
        assertThat(saved.getRating()).isEqualByComparingTo("6.14");
    }

    // --- US5: tolerancia a fallas ---------------------------------------------------------------

    @Test
    void blockedLeaguePersistsNothingKeepsPreviousDataAndOtherLeaguesContinue(CapturedOutput output) {
        Player bruno = player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        PlayerStats previous = previousStats(bruno);
        Player vinicius = player("Vinicius Junior", "Real Madrid CF", League.LA_LIGA);
        when(adapter.fetchLeaguePlayers(League.PREMIER_LEAGUE))
                .thenThrow(new WhoScoredException(WhoScoredException.BLOCKED, "403"));
        leagueRows(League.LA_LIGA, simpleRow("357209", "Vinícius Júnior", "52", "Real Madrid"));

        StatsUpdateResult result = service.updateAllStats();

        assertThat(savedStats().getPlayer()).isSameAs(vinicius);
        assertThat(previous.getMinutesPlayed()).isEqualTo(100);
        assertThat(previous.getFetchedAt()).isEqualTo(Instant.parse("2026-09-18T04:00:00Z"));
        assertThat(result).isEqualTo(new StatsUpdateResult(2, 1, 0, 0, 1));
        assertThat(output).contains("whoscored_stats_league_failed league=PREMIER_LEAGUE code=blocked");
    }

    @Test
    void leagueFailureLogIncludesWhatTheSourceAnswered(CapturedOutput output) {
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        when(adapter.fetchLeaguePlayers(League.PREMIER_LEAGUE))
                .thenThrow(new WhoScoredException(WhoScoredException.HTTP_ERROR, "WhoScored respondió status 429"));

        service.updateAllStats();

        assertThat(output).contains(
                "whoscored_stats_league_failed league=PREMIER_LEAGUE code=http_error detail=WhoScored respondió status 429");
    }

    @Test
    void totalOutageFinishesWithoutExceptionAndChangesNothing() {
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        player("Vinicius Junior", "Real Madrid CF", League.LA_LIGA);
        when(adapter.fetchLeaguePlayers(any(League.class)))
                .thenThrow(new WhoScoredException(WhoScoredException.TIMEOUT, "down"));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(result).isEqualTo(new StatsUpdateResult(2, 0, 0, 0, 2));
    }

    @Test
    void associatedRowWithoutAnyMetricIsAFailureAndKeepsPreviousData(CapturedOutput output) {
        Player heaton = player("Tom Heaton", "Manchester United FC", League.PREMIER_LEAGUE);
        PlayerStats previous = previousStats(heaton);
        leagueRows(League.PREMIER_LEAGUE, row("900004", "Tom Heaton", "32", List.of("Man Utd", "Manchester United"),
                null, null, null, null, null, null, null, null, null));

        StatsUpdateResult result = service.updateAllStats();

        verifyNothingSaved();
        assertThat(previous.getMinutesPlayed()).isEqualTo(100);
        assertThat(result).isEqualTo(new StatsUpdateResult(1, 0, 0, 0, 1));
        assertThat(output).contains("whoscored_stats_player_failed playerId=" + heaton.getId() + " code=no_metrics");
    }

    @Test
    void persistenceErrorOfOnePlayerDoesNotStopTheOthers(CapturedOutput output) {
        Player bruno = player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        Player mbeumo = player("Bryan Mbeumo", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE,
                simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"),
                simpleRow("353377", "Bryan Mbeumo", "32", "Man Utd", "Manchester United"));
        when(statsRepository.save(any(PlayerStats.class))).thenAnswer(invocation -> {
            PlayerStats stats = invocation.getArgument(0);
            if (stats.getPlayer() == bruno) {
                throw new DataIntegrityViolationException("boom");
            }
            return stats;
        });

        StatsUpdateResult result = service.updateAllStats();

        assertThat(allSavedStats(2)).extracting(PlayerStats::getPlayer).containsExactly(bruno, mbeumo);
        assertThat(result).isEqualTo(new StatsUpdateResult(2, 1, 0, 0, 1));
        assertThat(output).contains("whoscored_stats_player_failed playerId=" + bruno.getId() + " code=persistence_error");
    }

    @Test
    void releasesTheTransportAtTheEndOfEveryRunEvenWhenAllLeaguesFail() {
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        when(adapter.fetchLeaguePlayers(any(League.class)))
                .thenThrow(new WhoScoredException(WhoScoredException.BROWSER_ERROR, "chromium missing"));

        service.updateAllStats();

        verify(adapter, times(1)).endRun();
    }

    @Test
    void failureWhileReleasingTheTransportDoesNotChangeTheResult() {
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"));
        doThrow(new IllegalStateException("close failed")).when(adapter).endRun();

        StatsUpdateResult result = service.updateAllStats();

        assertThat(result).isEqualTo(new StatsUpdateResult(1, 1, 0, 0, 0));
    }

    @Test
    void emptyCatalogFinishesWithZeroProcessed() {
        StatsUpdateResult result = service.updateAllStats();

        assertThat(result).isEqualTo(new StatsUpdateResult(0, 0, 0, 0, 0));
        verify(adapter).endRun();
        verifyNoMoreInteractions(adapter);
    }

    // --- US4: caché por jugador ------------------------------------------------------------------

    @Test
    void leagueFullyCachedIsNotQueriedAndKeepsOriginalFetchedAt() {
        Player bruno = player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        WhoScoredPlayerStats cached = row("123761", "Bruno Fernandes", "32", List.of("Man Utd"),
                400, 2, 1, 15, 10, 4, 0, 0, "7.00");
        cache().put(bruno.getId(), cached);

        StatsUpdateResult result = service.updateAllStats();

        verify(adapter).endRun();
        verifyNoMoreInteractions(adapter);
        PlayerStats saved = savedStats();
        assertThat(saved.getMinutesPlayed()).isEqualTo(400);
        assertThat(saved.getFetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(result).isEqualTo(new StatsUpdateResult(1, 1, 1, 0, 0));
    }

    @Test
    void onePendingPlayerDownloadsTheLeagueOnce() {
        Player bruno = player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        player("Bryan Mbeumo", "Manchester United FC", League.PREMIER_LEAGUE);
        cache().put(bruno.getId(), simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"));
        leagueRows(League.PREMIER_LEAGUE,
                simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"),
                simpleRow("353377", "Bryan Mbeumo", "32", "Man Utd", "Manchester United"));

        StatsUpdateResult result = service.updateAllStats();

        verify(adapter, times(1)).fetchLeaguePlayers(League.PREMIER_LEAGUE);
        allSavedStats(2);
        assertThat(result).isEqualTo(new StatsUpdateResult(2, 2, 1, 0, 0));
    }

    @Test
    void secondRunWithinTtlDoesNotQueryTheSourceAgain() {
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"));

        service.updateAllStats();
        StatsUpdateResult second = service.updateAllStats();

        verify(adapter, times(1)).fetchLeaguePlayers(League.PREMIER_LEAGUE);
        assertThat(second).isEqualTo(new StatsUpdateResult(1, 1, 1, 0, 0));
    }

    @Test
    void onlySuccessfullySavedPlayersAreCached() {
        Player bruno = player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        Player ghost = player("Jugador Inexistente", "Manchester United FC", League.PREMIER_LEAGUE);
        Player heaton = player("Tom Heaton", "Manchester United FC", League.PREMIER_LEAGUE);
        Player mbeumo = player("Bryan Mbeumo", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE,
                simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"),
                row("900004", "Tom Heaton", "32", List.of("Man Utd"), null, null, null, null, null, null, null, null, null),
                simpleRow("353377", "Bryan Mbeumo", "32", "Man Utd", "Manchester United"));
        when(statsRepository.save(any(PlayerStats.class))).thenAnswer(invocation -> {
            PlayerStats stats = invocation.getArgument(0);
            if (stats.getPlayer() == mbeumo) {
                throw new DataIntegrityViolationException("boom");
            }
            return stats;
        });

        service.updateAllStats();

        assertThat(cache().get(bruno.getId())).isNotNull();
        assertThat(cache().get(ghost.getId())).isNull();
        assertThat(cache().get(heaton.getId())).isNull();
        assertThat(cache().get(mbeumo.getId())).isNull();
    }

    @Test
    void failedLeagueIsNotCachedAndIsRetriedNextRun() {
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        when(adapter.fetchLeaguePlayers(League.PREMIER_LEAGUE))
                .thenThrow(new WhoScoredException(WhoScoredException.BLOCKED, "403"))
                .thenReturn(List.of(simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United")));

        StatsUpdateResult first = service.updateAllStats();
        StatsUpdateResult second = service.updateAllStats();

        assertThat(first.failed()).isOne();
        assertThat(second).isEqualTo(new StatsUpdateResult(1, 1, 0, 0, 0));
        verify(adapter, times(2)).fetchLeaguePlayers(League.PREMIER_LEAGUE);
    }

    @Test
    void cacheReadFailureFallsBackToTheSource(CapturedOutput output) {
        useFailingCache(true, false);
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"));

        StatsUpdateResult result = service.updateAllStats();

        verify(adapter).fetchLeaguePlayers(League.PREMIER_LEAGUE);
        savedStats();
        assertThat(result).isEqualTo(new StatsUpdateResult(1, 1, 0, 0, 0));
        assertThat(output).contains("whoscored_stats_cache_unavailable operation=get");
    }

    @Test
    void cacheWriteFailureStillSavesTheStats(CapturedOutput output) {
        useFailingCache(false, true);
        player("Bruno Fernandes", "Manchester United FC", League.PREMIER_LEAGUE);
        leagueRows(League.PREMIER_LEAGUE, simpleRow("123761", "Bruno Fernandes", "32", "Man Utd", "Manchester United"));

        StatsUpdateResult result = service.updateAllStats();

        savedStats();
        assertThat(result).isEqualTo(new StatsUpdateResult(1, 1, 0, 0, 0));
        assertThat(output).contains("whoscored_stats_cache_unavailable operation=put");
    }

    // --- helpers --------------------------------------------------------------------------------

    Cache cache() {
        return cacheManager.getCache(CacheConfig.WHOSCORED_PLAYER_STATS_CACHE);
    }

    void useFailingCache(boolean failOnGet, boolean failOnPut) {
        Cache failing = mock(Cache.class);
        if (failOnGet) {
            when(failing.get(any(), eq(WhoScoredPlayerStats.class))).thenThrow(new IllegalStateException("redis down"));
        }
        if (failOnPut) {
            doThrow(new IllegalStateException("redis down")).when(failing).put(any(), any());
        }
        CacheManager failingManager = mock(CacheManager.class);
        when(failingManager.getCache(CacheConfig.WHOSCORED_PLAYER_STATS_CACHE)).thenReturn(failing);
        service = new PlayerStatsService(playerRepository, statsRepository, adapter,
                TransactionOperations.withoutTransaction(), failingManager);
    }

    Player player(String name, String team, League league) {
        Player player = Player.builder().id(nextId++).externalId("fd-" + nextId).fullName(name).team(team)
                .league(league).marketValue(BigDecimal.ONE).build();
        players.add(player);
        return player;
    }

    PlayerStats previousStats(Player player) {
        PlayerStats previous = new PlayerStats(player);
        previous.setPlayerId(player.getId());
        previous.setWhoscoredPlayerId("old");
        previous.setMinutesPlayed(100);
        previous.setGoals(1);
        previous.setAssists(1);
        previous.setShots(3);
        previous.setKeyPasses(2);
        previous.setTackles(2);
        previous.setYellowCards(0);
        previous.setRedCards(0);
        previous.setRating(new BigDecimal("6.50"));
        previous.setFetchedAt(Instant.parse("2026-09-18T04:00:00Z"));
        when(statsRepository.findById(player.getId())).thenReturn(Optional.of(previous));
        return previous;
    }

    void leagueRows(League league, WhoScoredPlayerStats... rows) {
        when(adapter.fetchLeaguePlayers(league)).thenReturn(List.of(rows));
    }

    static WhoScoredPlayerStats row(String playerId, String name, String teamId, List<String> teamNames,
                                    Integer minutes, Integer goals, Integer assists, Integer shots,
                                    Integer keyPasses, Integer tackles, Integer yellow, Integer red, String rating) {
        return new WhoScoredPlayerStats(playerId, name, teamId, teamNames, minutes, goals, assists, shots,
                keyPasses, tackles, yellow, red, rating == null ? null : new BigDecimal(rating), FETCHED_AT);
    }

    static WhoScoredPlayerStats simpleRow(String playerId, String name, String teamId, String... teamNames) {
        return row(playerId, name, teamId, List.of(teamNames), 90, 0, 0, 1, 1, 1, 0, 0, "6.50");
    }

    PlayerStats savedStats() {
        ArgumentCaptor<PlayerStats> captor = ArgumentCaptor.forClass(PlayerStats.class);
        verify(statsRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    List<PlayerStats> allSavedStats(int expected) {
        ArgumentCaptor<PlayerStats> captor = ArgumentCaptor.forClass(PlayerStats.class);
        verify(statsRepository, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    void verifyNothingSaved() {
        verify(statsRepository, never()).save(any(PlayerStats.class));
    }
}
