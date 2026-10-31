package com.example.demo.service.valuation;

import com.example.demo.dto.quote.OpportunitiesResponse;
import com.example.demo.dto.strategy.StrategyRef;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.Position;
import com.example.demo.repository.ComparisonPlayerRepository;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Oportunidades (spec 031): compara el {@code marketValue} de cada jugador con su última cotización de la
 * estrategia alternativa. Sólo lee cotizaciones ya guardadas, con tres consultas sin importar la cantidad de
 * jugadores.
 */
@Service
public class OpportunityService {

    private final ValuationStrategyCatalog catalog;
    private final ActiveStrategyService activeStrategyService;
    private final ComparisonPlayerRepository players;
    private final PlayerQuoteRepository quotes;

    public OpportunityService(ValuationStrategyCatalog catalog, ActiveStrategyService activeStrategyService,
                              ComparisonPlayerRepository players, PlayerQuoteRepository quotes) {
        this.catalog = catalog;
        this.activeStrategyService = activeStrategyService;
        this.players = players;
        this.quotes = quotes;
    }

    @Transactional(readOnly = true)
    public OpportunitiesResponse opportunities(League league, Position position, int limit) {
        ValuationStrategyDefinition active = catalog.active();
        ValuationStrategyDefinition alternative = catalog.alternative();
        ValuationStrategyDefinition market = activeStrategyService.marketStrategy();

        Map<Long, PlayerQuote> alternativeQuotes = latestByPlayer(alternative);
        Map<Long, PlayerQuote> marketQuotes = latestByPlayer(market);
        List<OpportunityCalculator.Candidate> candidates = players.findForComparison(league, position).stream()
                .map(player -> candidate(player, alternativeQuotes.get(player.getId()),
                        marketQuotes.get(player.getId())))
                .toList();

        OpportunityCalculator.Result result = OpportunityCalculator.classify(candidates, limit);
        return new OpportunitiesResponse(StrategyRef.of(active), StrategyRef.of(alternative), StrategyRef.of(market),
                !market.code().equals(active.code()), result.undervalued(), result.overvalued());
    }

    private Map<Long, PlayerQuote> latestByPlayer(ValuationStrategyDefinition strategy) {
        Map<Long, PlayerQuote> byPlayer = new HashMap<>();
        for (PlayerQuote quote : quotes.findLatestForAllPlayers(strategy.code())) {
            byPlayer.put(quote.getPlayer().getId(), quote);
        }
        return byPlayer;
    }

    private static OpportunityCalculator.Candidate candidate(Player player, PlayerQuote alternative,
                                                             PlayerQuote market) {
        return new OpportunityCalculator.Candidate(player.getId(), player.getFullName(), player.getTeam(),
                player.getLeague(), player.getPositions(), player.getMarketValue(),
                alternative == null ? null : alternative.getValue(),
                alternative == null ? null : alternative.getStatus(),
                alternative == null ? null : alternative.getCalculatedAt(),
                market == null ? null : market.getStatus());
    }
}
