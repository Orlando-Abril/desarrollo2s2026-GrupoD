package com.example.demo.service.valuation;

import com.example.demo.config.CacheConfig;
import com.example.demo.dto.quote.PlayerQuoteResponse;
import com.example.demo.dto.quote.RankingEntryResponse;
import com.example.demo.dto.quote.RankingEntryResponse.RankingPlayerResponse;
import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.exception.InvalidQuoteQueryException;
import com.example.demo.exception.PlayerNotFoundException;
import com.example.demo.exception.QuoteNotFoundException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.RankingRowProjection;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuoteQueryService {
    private static final Logger log = LoggerFactory.getLogger(QuoteQueryService.class);
    private static final LocalDate SQL_MAX_DATE = LocalDate.of(9999, 12, 31);
    private static final Instant SQL_MIN = LocalDate.of(1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final Instant SQL_MAX = SQL_MAX_DATE.atTime(LocalTime.of(23, 59, 59, 999_999_000)).toInstant(ZoneOffset.UTC);
    private final PlayerRepository playerRepository;
    private final PlayerQuoteRepository quoteRepository;
    private final ValuationStrategyCatalog strategyCatalog;
    private final CacheManager cacheManager;

    public QuoteQueryService(PlayerRepository playerRepository, PlayerQuoteRepository quoteRepository,
                             ValuationStrategyCatalog strategyCatalog, CacheManager cacheManager) {
        this.playerRepository = playerRepository;
        this.quoteRepository = quoteRepository;
        this.strategyCatalog = strategyCatalog;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public RankingPageResponse ranking(String strategy, League league, Position position, Pageable pageable) {
        String code = strategy == null ? strategyCatalog.active().code() : strategyCatalog.find(strategy).code();
        String key = rankingKey(code, league, position, pageable);
        RankingPageResponse cached = cacheGet(key);
        if (cached != null) return cached;
        Page<RankingRowProjection> rows = quoteRepository.findRanking(code, league == null ? null : league.name(),
                position == null ? null : position.name(), pageable);
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAllById(rows.getContent().stream().map(RankingRowProjection::getPlayerId).toList())
                .forEach(player -> players.put(player.getId(), player));
        List<RankingEntryResponse> items = rows.getContent().stream().map(row -> toResponse(row, players)).toList();
        RankingPageResponse response = new RankingPageResponse(items, pageable.getPageNumber(), pageable.getPageSize(),
                rows.getTotalElements(), rows.getTotalPages());
        cachePut(key, response);
        return response;
    }

    private RankingEntryResponse toResponse(RankingRowProjection row, Map<Long, Player> players) {
        Player player = players.get(row.getPlayerId());
        if (player == null) throw new IllegalStateException("El jugador del ranking ya no existe");
        return new RankingEntryResponse(row.getRank(), new RankingPlayerResponse(player.getId(), row.getName(),
                row.getTeam(), League.valueOf(row.getLeague()), player.getPositions()), row.getCurrentValue(),
                percentageChange(row.getCurrentValue(), row.getPreviousValue()), row.getStrategy(), row.getVersion());
    }

    static BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
    }

    private static String rankingKey(String strategy, League league, Position position, Pageable pageable) {
        return strategy + "|" + (league == null ? "-" : league.name())
                + "|" + (position == null ? "-" : position.name())
                + "|" + pageable.getPageNumber() + "|" + pageable.getPageSize();
    }

    private RankingPageResponse cacheGet(String key) {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.QUOTES_RANKING_CACHE);
            return cache == null ? null : cache.get(key, RankingPageResponse.class);
        } catch (RuntimeException ex) {
            log.warn("quotes_ranking_cache_read_failed key={} errorType={}", key, ex.getClass().getSimpleName());
            return null;
        }
    }

    private void cachePut(String key, RankingPageResponse value) {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.QUOTES_RANKING_CACHE);
            if (cache != null) cache.put(key, value);
        } catch (RuntimeException ex) {
            log.warn("quotes_ranking_cache_write_failed key={} errorType={}", key, ex.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public List<PlayerQuoteResponse> history(Long playerId, String strategy, LocalDate from, LocalDate to) {
        String code = validateAndResolve(playerId, strategy);
        if (from != null && to != null && from.isAfter(to)) throw new InvalidQuoteQueryException("from no puede ser posterior a to");
        Instant lowerBound = from == null ? SQL_MIN : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant exclusiveUpperBound = to == null ? null : startOfNextDay(to);
        Instant repositoryUpperBound = exclusiveUpperBound == null || SQL_MAX_DATE.equals(to) ? SQL_MAX : exclusiveUpperBound;
        return quoteRepository.findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc(
                        playerId, code, lowerBound, repositoryUpperBound).stream()
                .filter(quote -> exclusiveUpperBound == null || quote.getCalculatedAt().isBefore(exclusiveUpperBound))
                .map(PlayerQuoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlayerQuoteResponse quoteAt(Long playerId, String strategy, LocalDate at) {
        String code = validateAndResolve(playerId, strategy);
        return quoteRepository.findEffectiveOn(playerId, code, at).map(PlayerQuoteResponse::from)
                .orElseThrow(() -> new QuoteNotFoundException(playerId, code, at));
    }

    private String validateAndResolve(Long playerId, String strategy) {
        if (playerId == null || playerId <= 0) throw new InvalidQuoteQueryException("El id del jugador debe ser mayor a cero");
        if (!playerRepository.existsById(playerId)) throw new PlayerNotFoundException(playerId);
        return strategy == null ? strategyCatalog.active().code() : strategyCatalog.find(strategy).code();
    }

    private static Instant startOfNextDay(LocalDate date) {
        return date.equals(SQL_MAX_DATE) ? Instant.MAX : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
