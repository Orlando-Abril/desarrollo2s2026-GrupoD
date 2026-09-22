package com.example.demo.service;

import com.example.demo.dto.player.PlayerResponse;
import com.example.demo.exception.CatalogUnavailableException;
import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlayerCatalogQueryService {
    private final PlayerRepository playerRepository;
    private final CatalogSyncAuditService auditService;

    public PlayerCatalogQueryService(PlayerRepository playerRepository, CatalogSyncAuditService auditService) {
        this.playerRepository = playerRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PlayerResponse> findPlayers(League league, String team, Position position) {
        if (playerRepository.count() == 0 && !auditService.hasSuccessfulSnapshot()) {
            throw new CatalogUnavailableException();
        }
        return playerRepository.findAll(PlayerSpecifications.withFilters(league, team, position))
                .stream().map(this::toResponse).toList();
    }

    private PlayerResponse toResponse(Player player) {
        return new PlayerResponse(player.getId(), player.getExternalId(), player.getFullName(), player.getTeam(),
                player.getLeague(), player.getPositions(), player.getNationality(), player.getAge(),
                player.getMarketValue());
    }
}
