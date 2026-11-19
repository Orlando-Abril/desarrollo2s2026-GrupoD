package com.example.demo.service.valuation;

import com.example.demo.config.CacheConfig;
import com.example.demo.dto.quote.PlayerQuoteResponse;
import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.exception.InvalidQuoteQueryException;
import com.example.demo.exception.PlayerNotFoundException;
import com.example.demo.exception.QuoteNotFoundException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

@Service
public class QuoteQueryService {
    private static final Logger log = LoggerFactory.getLogger(QuoteQueryService.class);
    private static final String ACTIVE_STRATEGY_ALIAS = "active";
    private static final LocalDate SQL_MAX_DATE = LocalDate.of(9999, 12, 31);
    private static final Instant SQL_MIN = LocalDate.of(1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final Instant SQL_MAX = SQL_MAX_DATE.atTime(LocalTime.of(23, 59, 59, 999_999_000)).toInstant(ZoneOffset.UTC);
    private final PlayerRepository playerRepository;
    private final PlayerQuoteRepository quoteRepository;
    private final ValuationStrategyCatalog strategyCatalog;
    private final CacheManager cacheManager;
    private final RankingLoader rankingLoader;
    private final AtomicLong rankingGeneration = new AtomicLong();
    private final String rankingBootId = UUID.randomUUID().toString();
    private final ConcurrentHashMap<String, CompletableFuture<RankingPageResponse>> rankingLoads = new ConcurrentHashMap<>();

    @Autowired
    public QuoteQueryService(PlayerRepository playerRepository, PlayerQuoteRepository quoteRepository,
                             ValuationStrategyCatalog strategyCatalog, CacheManager cacheManager,
                             RankingLoader rankingLoader) {
        this.playerRepository = playerRepository;
        this.quoteRepository = quoteRepository;
        this.strategyCatalog = strategyCatalog;
        this.cacheManager = cacheManager;
        this.rankingLoader = rankingLoader;
    }

    /** Preserva la construcción de los tests de la entrega anterior. */
    public QuoteQueryService(PlayerRepository playerRepository, PlayerQuoteRepository quoteRepository,
                             ValuationStrategyCatalog strategyCatalog, CacheManager cacheManager) {
        this(playerRepository, quoteRepository, strategyCatalog, cacheManager,
                new RankingLoader(playerRepository, quoteRepository, strategyCatalog));
    }

    public RankingPageResponse ranking(String strategy, League league, Position position, Pageable pageable) {
        long generation = rankingGeneration.get();
        String code = strategy == null ? ACTIVE_STRATEGY_ALIAS : strategyCatalog.find(strategy).code();
        String key = rankingKey(code, league, position, pageable, generation);
        RankingPageResponse cached = cacheGet(key);
        if (cached != null) return cached;
        CompletableFuture<RankingPageResponse> mine = new CompletableFuture<>();
        CompletableFuture<RankingPageResponse> flight = rankingLoads.putIfAbsent(key, mine);
        if (flight != null) {
            try {
                return flight.join();
            } catch (CompletionException ex) {
                if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
                throw ex;
            }
        }
        try {
            RankingPageResponse result = loadRanking(key, code, league, position, pageable, generation);
            mine.complete(result);
            return result;
        } catch (RuntimeException ex) {
            mine.completeExceptionally(ex);
            throw ex;
        } finally {
            if (!mine.isDone()) {
                mine.completeExceptionally(new IllegalStateException("La carga del ranking terminó sin resultado"));
            }
            rankingLoads.remove(key, mine);
        }
    }

    private RankingPageResponse loadRanking(String key, String code, League league, Position position,
                                            Pageable pageable, long generation) {
        AtomicReference<RankingLoader.LoadedRanking> loaded = new AtomicReference<>();
        RankingPageResponse result = loadFromCacheOrRepository(key, code, league, position, pageable, loaded);
        RankingLoader.LoadedRanking value = loaded.get();
        publishExplicitStrategyKey(code, league, position, pageable, generation, result, value);
        evictObsoleteGenerationKeys(key, code, league, position, pageable, generation, value);
        return result;
    }

    private RankingPageResponse loadFromCacheOrRepository(String key, String code, League league, Position position,
                                                          Pageable pageable,
                                                          AtomicReference<RankingLoader.LoadedRanking> loaded) {
        Cache cache = rankingCache();
        if (cache == null) {
            return loadAndCapture(code, league, position, pageable, loaded).page();
        }
        try {
            return cache.get(key, () -> loadAndCapture(code, league, position, pageable, loaded).page());
        } catch (Cache.ValueRetrievalException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return fallbackAfterCacheFailure(key, code, league, position, pageable, loaded, ex);
        }
    }

    private RankingLoader.LoadedRanking loadAndCapture(String code, League league, Position position,
                                                       Pageable pageable,
                                                       AtomicReference<RankingLoader.LoadedRanking> loaded) {
        RankingLoader.LoadedRanking value = rankingLoader.load(
                ACTIVE_STRATEGY_ALIAS.equals(code) ? null : code, league, position, pageable);
        loaded.set(value);
        return value;
    }

    private RankingPageResponse fallbackAfterCacheFailure(String key, String code, League league, Position position,
                                                           Pageable pageable,
                                                           AtomicReference<RankingLoader.LoadedRanking> loaded,
                                                           RuntimeException ex) {
        log.warn("quotes_ranking_cache_load_failed key={} errorType={}", key, ex.getClass().getSimpleName());
        RankingLoader.LoadedRanking value = loaded.get();
        if (value == null) value = loadAndCapture(code, league, position, pageable, loaded);
        return value.page();
    }

    private void publishExplicitStrategyKey(String code, League league, Position position, Pageable pageable,
                                            long generation, RankingPageResponse result,
                                            RankingLoader.LoadedRanking value) {
        if (value != null && ACTIVE_STRATEGY_ALIAS.equals(code) && rankingGeneration.get() == generation) {
            cachePut(rankingKey(value.strategyCode(), league, position, pageable, generation), result);
        }
    }

    private void evictObsoleteGenerationKeys(String key, String code, League league, Position position,
                                             Pageable pageable, long generation, RankingLoader.LoadedRanking value) {
        if (rankingGeneration.get() != generation) {
            cacheEvict(key);
            if (value != null && ACTIVE_STRATEGY_ALIAS.equals(code)) {
                cacheEvict(rankingKey(value.strategyCode(), league, position, pageable, generation));
            }
        }
    }

    static BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
    }

    private String rankingKey(String strategy, League league, Position position, Pageable pageable, long generation) {
        return strategy + "|" + (league == null ? "-" : league.name())
                + "|" + (position == null ? "-" : position.name())
                + "|" + pageable.getPageNumber() + "|" + pageable.getPageSize()
                + "|gen=" + rankingBootId + ":" + generation;
    }

    public long advanceRankingGeneration() { return rankingGeneration.incrementAndGet(); }

    public long currentRankingGeneration() { return rankingGeneration.get(); }

    private Cache rankingCache() {
        try {
            return cacheManager.getCache(CacheConfig.QUOTES_RANKING_CACHE);
        } catch (RuntimeException ex) {
            log.warn("quotes_ranking_cache_lookup_failed errorType={}", ex.getClass().getSimpleName());
            return null;
        }
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

    private void cacheEvict(String key) {
        try {
            Cache cache = rankingCache();
            if (cache != null) cache.evict(key);
        } catch (RuntimeException ex) {
            log.warn("quotes_ranking_cache_evict_stale_failed key={} errorType={}", key, ex.getClass().getSimpleName());
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
