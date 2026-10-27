package com.example.demo.service;

import com.example.demo.dto.player.CurrentQuoteResponse;
import com.example.demo.dto.player.PlayerDetailResponse;
import com.example.demo.dto.player.PlayerResponse;
import com.example.demo.dto.player.PlayerStatsResponse;
import com.example.demo.exception.InvalidQuoteQueryException;
import com.example.demo.exception.PlayerNotFoundException;
import com.example.demo.model.Player;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerDetailService {

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository statsRepository;
    private final PlayerQuoteRepository quoteRepository;
    private final ValuationStrategyCatalog strategyCatalog;

    public PlayerDetailService(PlayerRepository playerRepository, PlayerStatsRepository statsRepository,
                               PlayerQuoteRepository quoteRepository, ValuationStrategyCatalog strategyCatalog) {
        this.playerRepository = playerRepository;
        this.statsRepository = statsRepository;
        this.quoteRepository = quoteRepository;
        this.strategyCatalog = strategyCatalog;
    }

    @Transactional(readOnly = true)
    public PlayerDetailResponse findById(Long id) {
        if (id == null || id <= 0) {
            throw new InvalidQuoteQueryException("El id del jugador debe ser mayor a cero");
        }

        Player player = playerRepository.findById(id).orElseThrow(() -> new PlayerNotFoundException(id));
        PlayerStatsResponse stats = statsRepository.findById(id)
                .map(PlayerStatsResponse::from)
                .orElse(null);
        String activeStrategy = strategyCatalog.active().code();
        CurrentQuoteResponse quote = quoteRepository
                .findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(id, activeStrategy)
                .map(CurrentQuoteResponse::from)
                .orElse(null);

        return new PlayerDetailResponse(toPlayerResponse(player), stats, quote);
    }

    private static PlayerResponse toPlayerResponse(Player player) {
        return new PlayerResponse(player.getId(), player.getExternalId(), player.getFullName(), player.getTeam(),
                player.getLeague(), player.getPositions(), player.getNationality(), player.getAge(),
                player.getMarketValue());
    }
}
