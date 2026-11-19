package com.example.demo.service.valuation;

import com.example.demo.dto.quote.RankingEntryResponse;
import com.example.demo.dto.quote.RankingEntryResponse.RankingPlayerResponse;
import com.example.demo.dto.quote.RankingPageResponse;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.RankingRowProjection;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class RankingLoader {
    private final PlayerRepository players;
    private final PlayerQuoteRepository quotes;
    private final ValuationStrategyCatalog strategies;

    public RankingLoader(PlayerRepository players, PlayerQuoteRepository quotes, ValuationStrategyCatalog strategies) {
        this.players = players;
        this.quotes = quotes;
        this.strategies = strategies;
    }

    @Transactional(readOnly = true)
    public LoadedRanking load(String requestedCode, League league, Position position, Pageable pageable) {
        String code = requestedCode == null ? strategies.active().code() : requestedCode;
        Page<RankingRowProjection> rows = quotes.findRanking(code, league == null ? null : league.name(),
                position == null ? null : position.name(), pageable);
        Map<Long, Player> byId = new HashMap<>();
        if (!rows.getContent().isEmpty()) {
            players.findAllById(rows.getContent().stream().map(RankingRowProjection::getPlayerId).toList())
                    .forEach(player -> byId.put(player.getId(), player));
        }
        var items = rows.getContent().stream().map(row -> toResponse(row, byId)).toList();
        return new LoadedRanking(code, new RankingPageResponse(items, pageable.getPageNumber(), pageable.getPageSize(),
                rows.getTotalElements(), rows.getTotalPages()));
    }

    private static RankingEntryResponse toResponse(RankingRowProjection row, Map<Long, Player> players) {
        Player player = players.get(row.getPlayerId());
        if (player == null) throw new IllegalStateException("El jugador del ranking ya no existe");
        return new RankingEntryResponse(row.getRank(), new RankingPlayerResponse(player.getId(), row.getName(),
                row.getTeam(), League.valueOf(row.getLeague()), player.getPositions()), row.getCurrentValue(),
                QuoteQueryService.percentageChange(row.getCurrentValue(), row.getPreviousValue()),
                row.getStrategy(), row.getVersion());
    }

    public record LoadedRanking(String strategyCode, RankingPageResponse page) { }
}
