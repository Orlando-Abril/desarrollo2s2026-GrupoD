package com.example.demo.service.valuation;

import com.example.demo.dto.quote.LatestQuotesResponse;
import com.example.demo.dto.strategy.StrategyRef;
import com.example.demo.exception.InvalidQuoteQueryException;
import com.example.demo.model.PlayerQuote;
import com.example.demo.repository.ComparisonPlayerRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Última cotización de una estrategia para los jugadores de una página del Catálogo (spec 031). Dos consultas,
 * sin importar la cantidad de ids.
 */
@Service
public class LatestQuoteService {

    public static final int MAX_IDS = 100;

    private final ValuationStrategyCatalog catalog;
    private final ComparisonPlayerRepository players;
    private final PlayerQuoteRepository quotes;

    public LatestQuoteService(ValuationStrategyCatalog catalog, ComparisonPlayerRepository players,
                              PlayerQuoteRepository quotes) {
        this.catalog = catalog;
        this.players = players;
        this.quotes = quotes;
    }

    /**
     * @param rawPlayerIds ids positivos separados por comas, entre 1 y {@value #MAX_IDS} distintos
     * @param strategy     código exacto; nulo usa la alternativa
     */
    @Transactional(readOnly = true)
    public LatestQuotesResponse latest(String rawPlayerIds, String strategy) {
        List<Long> requested = parseIds(rawPlayerIds);
        ValuationStrategyDefinition definition = strategy == null ? catalog.alternative() : catalog.find(strategy);

        Set<Long> existing = new HashSet<>(players.findExistingIds(requested));
        Map<Long, PlayerQuote> latest = new HashMap<>();
        for (PlayerQuote quote : quotes.findLatestForPlayers(definition.code(), requested)) {
            latest.put(quote.getPlayer().getId(), quote);
        }
        List<LatestQuotesResponse.Item> items = requested.stream()
                .filter(existing::contains)
                .map(id -> new LatestQuotesResponse.Item(id, toQuote(latest.get(id))))
                .toList();
        return new LatestQuotesResponse(StrategyRef.of(definition), items);
    }

    static List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidQuoteQueryException("playerIds es obligatorio");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : raw.split(",", -1)) {
            ids.add(parseId(part.trim()));
        }
        if (ids.size() > MAX_IDS) {
            throw new InvalidQuoteQueryException("playerIds admite como máximo " + MAX_IDS + " ids distintos");
        }
        return List.copyOf(ids);
    }

    private static Long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id < 1) {
                throw new InvalidQuoteQueryException("playerIds sólo admite ids positivos");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new InvalidQuoteQueryException("playerIds debe ser una lista de enteros separados por comas");
        }
    }

    private static LatestQuotesResponse.Quote toQuote(PlayerQuote quote) {
        return quote == null ? null : new LatestQuotesResponse.Quote(quote.getValue().setScale(4, RoundingMode.HALF_EVEN),
                quote.getStatus(), quote.getCalculatedAt(), quote.getStrategyVersion());
    }
}
