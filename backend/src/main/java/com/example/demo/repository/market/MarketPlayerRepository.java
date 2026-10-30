package com.example.demo.repository.market;

import com.example.demo.model.Player;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Lock del jugador para el mercado. Existe para no modificar {@code PlayerRepository}, archivo protegido de
 * Entrega 1. Es el primer lock de cualquier trabajo sobre el libro de un jugador (spec 027, research D3).
 */
public interface MarketPlayerRepository extends Repository<Player, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.id = :id")
    Optional<Player> findLockedById(@Param("id") Long id);
}
