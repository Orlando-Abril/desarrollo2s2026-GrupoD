package com.example.demo.service;

import com.example.demo.exception.SuperuserUnavailableException;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerTokenAllocation;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.PlayerTokenAllocationRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlayerTokenInitializationService {
    private final UserRepository userRepository;
    private final PlayerTokenAllocationRepository allocationRepository;
    private final CatalogSyncAuditService auditService;
    private final String superuserUsername;

    public PlayerTokenInitializationService(UserRepository userRepository,
                                            PlayerTokenAllocationRepository allocationRepository,
                                            CatalogSyncAuditService auditService,
                                            @Value("${market.superuser-username}") String superuserUsername) {
        this.userRepository = userRepository;
        this.allocationRepository = allocationRepository;
        this.auditService = auditService;
        this.superuserUsername = superuserUsername;
    }

    public User requireSuperuser() {
        return userRepository.findByUsername(superuserUsername)
                .filter(user -> user.getRole() == Role.ADMIN)
                .orElseThrow(SuperuserUnavailableException::new);
    }

    public void initialize(Player player, User owner, UUID correlationId) {
        if (allocationRepository.existsByPlayerId(player.getId())) return;
        PlayerTokenAllocation allocation = allocationRepository.save(PlayerTokenAllocation.builder()
                .player(player)
                .owner(owner)
                .build());
        auditService.append(owner.getId(), correlationId, "TOKENS_ALLOCATED",
                "Initial immutable allocation of 100 tokens at 1.00 credit",
                "PlayerTokenAllocation", String.valueOf(allocation.getId()), null,
                "playerId=" + player.getId() + ",totalSupply=100,ownerQuantity=100,basePrice=1.00",
                player.getLeague(), 1, null);
    }
}
