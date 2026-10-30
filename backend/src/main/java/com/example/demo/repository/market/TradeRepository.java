package com.example.demo.repository.market;

import com.example.demo.model.market.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Operaciones ejecutadas. Extiende {@link Repository} y no {@code JpaRepository} para que no exista
 * ninguna operación de borrado: una operación ejecutada es inmutable (spec 026, FR-020).
 */
public interface TradeRepository extends Repository<Trade, Long> {

    Trade save(Trade trade);

    Optional<Trade> findById(Long id);

    /** Operaciones donde el usuario compró o vendió, de la más nueva a la más vieja. */
    @Query(value = "select t from Trade t where t.buyer.id = :userId or t.seller.id = :userId"
            + " order by t.executedAt desc, t.id desc",
            countQuery = "select count(t) from Trade t where t.buyer.id = :userId or t.seller.id = :userId")
    Page<Trade> findByUser(@Param("userId") Long userId, Pageable pageable);

    List<Trade> findByPlayerIdOrderByExecutedAtDescIdDesc(Long playerId);

    /** Operaciones de una orden, como compradora o como vendedora; para su precio promedio. */
    List<Trade> findByBuyOrderIdOrSellOrderIdOrderByIdAsc(Long buyOrderId, Long sellOrderId);
}
