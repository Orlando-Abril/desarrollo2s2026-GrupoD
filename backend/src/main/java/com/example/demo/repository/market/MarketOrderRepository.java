package com.example.demo.repository.market;

import com.example.demo.dto.market.BookLevelResponse;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Órdenes y libro. El libro son las LIMIT activas de un jugador y lado: las compras de mayor a menor precio
 * y las ventas de menor a mayor; a igual precio, la más vieja primero y, a igual instante, el id menor.
 */
public interface MarketOrderRepository extends Repository<MarketOrder, Long> {

    String BOOK_FILTER = "select o from MarketOrder o where o.player.id = :playerId and o.side = :side"
            + " and o.type = com.example.demo.model.market.OrderType.LIMIT and o.status in :active";

    /** El libro de un jugador y lado agregado por precio (spec 027, research D10); nunca expone dueños ni ids. */
    String LEVELS = "select new com.example.demo.dto.market.BookLevelResponse(o.limitPrice,"
            + " sum(o.quantity - o.filledQuantity)) from MarketOrder o where o.player.id = :playerId"
            + " and o.side = :side and o.type = com.example.demo.model.market.OrderType.LIMIT"
            + " and o.status in :active group by o.limitPrice";

    MarketOrder save(MarketOrder order);

    Optional<MarketOrder> findById(Long id);

    /** Jugador de la orden sin cargarla: la orden se carga recién con su lock (spec 027, research D3). */
    @Query("select o.player.id from MarketOrder o where o.id = :id")
    Optional<Long> findPlayerIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketOrder o where o.id = :id")
    Optional<MarketOrder> findByIdForUpdate(@Param("id") Long id);

    Page<MarketOrder> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    default List<MarketOrder> findOpenBuys(Long playerId) {
        return findBookByPriceDesc(playerId, OrderSide.BUY, OrderStatus.ACTIVE);
    }

    default List<MarketOrder> findOpenSells(Long playerId) {
        return findBookByPriceAsc(playerId, OrderSide.SELL, OrderStatus.ACTIVE);
    }

    default List<MarketOrder> findOpenBuysForUpdate(Long playerId) {
        return lockBookByPriceDesc(playerId, OrderSide.BUY, OrderStatus.ACTIVE);
    }

    default List<MarketOrder> findOpenSellsForUpdate(Long playerId) {
        return lockBookByPriceAsc(playerId, OrderSide.SELL, OrderStatus.ACTIVE);
    }

    /** Ventas del libro agregadas por precio, de menor a mayor, con lo pendiente a cada precio. */
    default List<BookLevelResponse> findSellLevels(Long playerId) {
        return findLevelsByPriceAsc(playerId, OrderSide.SELL, OrderStatus.ACTIVE);
    }

    /** Compras del libro agregadas por precio, de mayor a menor, con lo pendiente a cada precio. */
    default List<BookLevelResponse> findBuyLevels(Long playerId) {
        return findLevelsByPriceDesc(playerId, OrderSide.BUY, OrderStatus.ACTIVE);
    }

    /** Ventas activas del usuario para un jugador, bloqueadas; para el superusuario, su orden de creador de mercado. */
    default List<MarketOrder> findActiveSellsForUpdate(Long userId, Long playerId) {
        return lockActiveByUserAndPlayerAndSide(userId, playerId, OrderSide.SELL, OrderStatus.ACTIVE);
    }

    /** Créditos reservados por las compras LIMIT activas del usuario, con escala 4; 0 si no tiene. */
    default BigDecimal sumReservedCredits(Long userId) {
        return sumPendingBuyLimitCredits(userId, OrderStatus.ACTIVE).setScale(4, RoundingMode.HALF_EVEN);
    }

    @Query(BOOK_FILTER + " order by o.limitPrice desc, o.createdAt asc, o.id asc")
    List<MarketOrder> findBookByPriceDesc(@Param("playerId") Long playerId, @Param("side") OrderSide side,
                                          @Param("active") Collection<OrderStatus> active);

    @Query(BOOK_FILTER + " order by o.limitPrice asc, o.createdAt asc, o.id asc")
    List<MarketOrder> findBookByPriceAsc(@Param("playerId") Long playerId, @Param("side") OrderSide side,
                                         @Param("active") Collection<OrderStatus> active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(BOOK_FILTER + " order by o.limitPrice desc, o.createdAt asc, o.id asc")
    List<MarketOrder> lockBookByPriceDesc(@Param("playerId") Long playerId, @Param("side") OrderSide side,
                                          @Param("active") Collection<OrderStatus> active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(BOOK_FILTER + " order by o.limitPrice asc, o.createdAt asc, o.id asc")
    List<MarketOrder> lockBookByPriceAsc(@Param("playerId") Long playerId, @Param("side") OrderSide side,
                                         @Param("active") Collection<OrderStatus> active);

    @Query(LEVELS + " order by o.limitPrice asc")
    List<BookLevelResponse> findLevelsByPriceAsc(@Param("playerId") Long playerId, @Param("side") OrderSide side,
                                                 @Param("active") Collection<OrderStatus> active);

    @Query(LEVELS + " order by o.limitPrice desc")
    List<BookLevelResponse> findLevelsByPriceDesc(@Param("playerId") Long playerId, @Param("side") OrderSide side,
                                                  @Param("active") Collection<OrderStatus> active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketOrder o where o.user.id = :userId and o.player.id = :playerId and o.side = :side"
            + " and o.status in :active order by o.id")
    List<MarketOrder> lockActiveByUserAndPlayerAndSide(@Param("userId") Long userId, @Param("playerId") Long playerId,
                                                       @Param("side") OrderSide side,
                                                       @Param("active") Collection<OrderStatus> active);

    @Query("select coalesce(sum((o.quantity - o.filledQuantity) * o.limitPrice), 0) from MarketOrder o"
            + " where o.user.id = :userId and o.side = com.example.demo.model.market.OrderSide.BUY"
            + " and o.type = com.example.demo.model.market.OrderType.LIMIT and o.status in :active")
    BigDecimal sumPendingBuyLimitCredits(@Param("userId") Long userId,
                                         @Param("active") Collection<OrderStatus> active);
}
