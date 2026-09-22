package com.example.demo.repository;

import com.example.demo.model.PlayerTokenAllocation;
import org.springframework.data.repository.Repository;

public interface PlayerTokenAllocationRepository extends Repository<PlayerTokenAllocation, Long> {
    PlayerTokenAllocation save(PlayerTokenAllocation allocation);
    boolean existsByPlayerId(Long playerId);
}
