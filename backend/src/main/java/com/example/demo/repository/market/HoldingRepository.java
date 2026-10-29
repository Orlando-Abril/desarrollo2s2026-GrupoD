package com.example.demo.repository.market;

import com.example.demo.model.market.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Tenencias. Sin operaciones de borrado: una tenencia nunca se borra, y la del superusuario marca que el
 * jugador ya tiene sus tokens emitidos (spec 026, FR-007).
 */
public interface HoldingRepository extends Repository<Holding, Long> {

    Holding save(Holding holding);

    Optional<Holding> findByUserIdAndPlayerId(Long userId, Long playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holding h where h.user.id = :userId and h.player.id = :playerId")
    Optional<Holding> findForUpdate(@Param("userId") Long userId, @Param("playerId") Long playerId);

    List<Holding> findByUserIdOrderByPlayerIdAsc(Long userId);

    /** Jugadores sin tenencia del usuario dado; para el superusuario, los que faltan emitir. */
    @Query("select p.id from Player p where not exists "
            + "(select 1 from Holding h where h.player = p and h.user.id = :userId) order by p.id")
    List<Long> findPlayerIdsWithoutHolding(@Param("userId") Long userId);

    /** Jugadores de los que el usuario tiene tokens; para el superusuario, los que recorre el creador de mercado. */
    @Query("select h.player.id from Holding h where h.user.id = :userId and h.quantity > 0 order by h.player.id")
    List<Long> findPlayerIdsWithTokens(@Param("userId") Long userId);

    @Query("select coalesce(sum(h.quantity), 0) from Holding h where h.player.id = :playerId")
    long sumQuantityByPlayerId(@Param("playerId") Long playerId);
}
