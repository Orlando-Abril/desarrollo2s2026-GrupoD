package com.example.demo.service.market;

import com.example.demo.exception.PlayerNotFoundException;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.MarketPlayerRepository;
import com.example.demo.repository.market.MarketUserRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Único punto para tomar los locks del mercado (spec 027, research D3). Cualquier trabajo sobre el libro de un jugador
 * los toma en este orden: jugador → órdenes → usuarios por id ascendente → tenencias. Ninguna entidad se carga
 * antes de su lock: si ya está en el contexto de persistencia, una consulta {@code PESSIMISTIC_WRITE} bloquea la
 * fila pero Hibernate devuelve la instancia que tenía, con los datos leídos antes del lock.
 */
@Component
public class MarketLocks {

    private final MarketPlayerRepository playerRepository;
    private final MarketOrderRepository orderRepository;
    private final MarketUserRepository userRepository;
    private final HoldingRepository holdingRepository;

    public MarketLocks(MarketPlayerRepository playerRepository, MarketOrderRepository orderRepository,
                       MarketUserRepository userRepository, HoldingRepository holdingRepository) {
        this.playerRepository = playerRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
    }

    public Player lockPlayer(Long playerId) {
        return playerRepository.findLockedById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
    }

    /** El lado del libro contra el que se calza una orden de {@code incomingSide}, ya ordenado. */
    public List<MarketOrder> lockOppositeBook(Long playerId, OrderSide incomingSide) {
        return incomingSide == OrderSide.BUY
                ? orderRepository.findOpenSellsForUpdate(playerId)
                : orderRepository.findOpenBuysForUpdate(playerId);
    }

    /** Bloquea los usuarios en orden ascendente de id. Es el único lugar donde se carga la entidad {@link User}. */
    public Map<Long, User> lockUsers(Collection<Long> userIds) {
        Map<Long, User> locked = new LinkedHashMap<>();
        userIds.stream().distinct().sorted().forEach(id -> locked.put(id, userRepository.findLockedById(id)
                .orElseThrow(() -> new IllegalStateException("market_user_missing id=" + id))));
        return locked;
    }

    public Optional<Holding> lockHolding(Long userId, Long playerId) {
        return holdingRepository.findForUpdate(userId, playerId);
    }

    /**
     * Bloquea las tenencias existentes de esos usuarios para el jugador, después de los usuarios. Su orden entre sí
     * no importa porque el jugador ya está bloqueado; el mapa es mutable para sumar las tenencias que crea el calce.
     */
    public Map<Long, Holding> lockHoldings(Collection<Long> userIds, Long playerId) {
        Map<Long, Holding> locked = new HashMap<>();
        userIds.stream().distinct().sorted()
                .forEach(id -> lockHolding(id, playerId).ifPresent(holding -> locked.put(id, holding)));
        return locked;
    }
}
