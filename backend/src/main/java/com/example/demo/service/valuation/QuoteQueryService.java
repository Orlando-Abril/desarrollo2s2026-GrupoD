package com.example.demo.service.valuation;

import com.example.demo.dto.quote.PlayerQuoteResponse;
import com.example.demo.exception.InvalidQuoteQueryException;
import com.example.demo.exception.PlayerNotFoundException;
import com.example.demo.exception.QuoteNotFoundException;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class QuoteQueryService {

    private static final LocalDate SQL_MAX_DATE = LocalDate.of(9999, 12, 31);
    private static final Instant SQL_MIN = LocalDate.of(1, 1, 1)
            .atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final Instant SQL_MAX = SQL_MAX_DATE
            .atTime(LocalTime.of(23, 59, 59, 999_999_000)).toInstant(ZoneOffset.UTC);

    private final PlayerRepository playerRepository;
    private final PlayerQuoteRepository quoteRepository;
    private final ValuationStrategyCatalog strategyCatalog;

    public QuoteQueryService(PlayerRepository playerRepository, PlayerQuoteRepository quoteRepository,
                             ValuationStrategyCatalog strategyCatalog) {
        this.playerRepository = playerRepository;
        this.quoteRepository = quoteRepository;
        this.strategyCatalog = strategyCatalog;
    }

    @Transactional(readOnly = true)
    public List<PlayerQuoteResponse> history(Long playerId, String strategy, LocalDate from, LocalDate to) {
        String code = validateAndResolve(playerId, strategy);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidQuoteQueryException("from no puede ser posterior a to");
        }

        Instant lowerBound = from == null ? SQL_MIN : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant exclusiveUpperBound = to == null ? null : startOfNextDay(to);
        Instant repositoryUpperBound = exclusiveUpperBound == null || SQL_MAX_DATE.equals(to)
                ? SQL_MAX : exclusiveUpperBound;

        return quoteRepository
                .findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc(
                        playerId, code, lowerBound, repositoryUpperBound)
                .stream()
                .filter(quote -> exclusiveUpperBound == null || quote.getCalculatedAt().isBefore(exclusiveUpperBound))
                .map(PlayerQuoteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerQuoteResponse quoteAt(Long playerId, String strategy, LocalDate at) {
        String code = validateAndResolve(playerId, strategy);
        return quoteRepository.findEffectiveOn(playerId, code, at)
                .map(PlayerQuoteResponse::from)
                .orElseThrow(() -> new QuoteNotFoundException(playerId, code, at));
    }

    private String validateAndResolve(Long playerId, String strategy) {
        if (playerId == null || playerId <= 0) {
            throw new InvalidQuoteQueryException("El id del jugador debe ser mayor a cero");
        }
        if (!playerRepository.existsById(playerId)) {
            throw new PlayerNotFoundException(playerId);
        }
        return strategy == null ? strategyCatalog.active().code() : strategyCatalog.find(strategy).code();
    }

    private static Instant startOfNextDay(LocalDate date) {
        if (date.equals(SQL_MAX_DATE)) {
            return Instant.MAX;
        }
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
