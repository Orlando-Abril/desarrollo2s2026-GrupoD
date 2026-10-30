package com.example.demo.service.market;

import com.example.demo.dto.market.MyOrderResponse;
import com.example.demo.dto.market.OrderBookResponse;
import com.example.demo.dto.market.OrderRequest;
import com.example.demo.dto.market.OrderResponse;
import com.example.demo.exception.InsufficientBalanceException;
import com.example.demo.exception.InsufficientTokensException;
import com.example.demo.exception.InvalidOrderException;
import com.example.demo.exception.OrderAccessDeniedException;
import com.example.demo.exception.OrderClosedException;
import com.example.demo.exception.OrderNotFoundException;
import com.example.demo.exception.PlayerNotFoundException;
import com.example.demo.exception.PlayerNotQuotedException;
import com.example.demo.exception.PriceOutOfBandException;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActor;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderType;
import com.example.demo.model.market.Trade;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.service.audit.TransactionAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Órdenes del mercado (spec 027). Cada pedido es una única transacción: valida, bloquea (en el orden de
 * {@link MarketLocks}), calza con {@link OrderMatchingEngineService}, aplica cada calce con su auditoría y deja en el
 * libro o cancela lo que no se llenó. Si algo falla, no queda nada del pedido.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final MarketUserResolver userResolver;
    private final MarketLocks locks;
    private final OrderMatchingEngineService engine;
    private final MarketOrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final PlayerRepository playerRepository;
    private final TransactionAuditService auditService;
    private final Clock clock;

    public OrderService(MarketUserResolver userResolver, MarketLocks locks, OrderMatchingEngineService engine,
                        MarketOrderRepository orderRepository, HoldingRepository holdingRepository,
                        TradeRepository tradeRepository, PlayerRepository playerRepository,
                        TransactionAuditService auditService, Clock clock) {
        this.userResolver = userResolver;
        this.locks = locks;
        this.engine = engine;
        this.orderRepository = orderRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.playerRepository = playerRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public OrderResult placeBuy(String principalName, OrderRequest request) {
        return place(userResolver.resolve(principalName), OrderSide.BUY, request);
    }

    @Transactional
    public OrderResult placeSell(String principalName, OrderRequest request) {
        return place(userResolver.resolve(principalName), OrderSide.SELL, request);
    }

    /** El libro de un jugador agregado por precio, con su cotización vigente. Sin locks: es una vista. */
    @Transactional(readOnly = true)
    public OrderBookResponse book(Long playerId) {
        Player player = playerRepository.findById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
        return new OrderBookResponse(player.getId(), player.getMarketValue(), orderRepository.findSellLevels(playerId),
                orderRepository.findBuyLevels(playerId));
    }

    /**
     * Órdenes activas del usuario, de la más nueva a la más vieja. El precio promedio sale de sus operaciones, que sólo
     * se consultan si la orden ejecutó algo; la respuesta no las muestra (spec 029, research R10).
     */
    @Transactional(readOnly = true)
    public List<MyOrderResponse> myOrders(String principalName) {
        MarketPrincipal principal = userResolver.resolve(principalName);
        return orderRepository.findActiveByUserWithPlayer(principal.id()).stream()
                .map(order -> MyOrderResponse.of(OrderResponse.of(order, executedTrades(order), List.of()),
                        order.getPlayer().getFullName()))
                .toList();
    }

    private List<Trade> executedTrades(MarketOrder order) {
        if (order.getFilledQuantity() == 0) {
            return List.of();
        }
        return tradeRepository.findByBuyOrderIdOrSellOrderIdOrderByIdAsc(order.getId(), order.getId());
    }

    /**
     * Cancela una orden activa del usuario y libera lo reservado. El jugador sale de una proyección y la orden se carga
     * recién con su lock, después del jugador: cargarla antes devolvería un estado viejo (research D3).
     */
    @Transactional
    public OrderResult cancel(String principalName, Long orderId) {
        MarketPrincipal principal = userResolver.resolve(principalName);
        Long playerId = orderRepository.findPlayerIdById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        locks.lockPlayer(playerId);
        MarketOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getUser().getId().equals(principal.id())) {
            throw new OrderAccessDeniedException(OrderAccessDeniedException.ORDER_FORBIDDEN,
                    "La orden " + orderId + " es de otro usuario");
        }
        if (!order.getStatus().isActive()) {
            throw new OrderClosedException(orderId);
        }

        Instant now = Instant.now(clock);
        AuditActor actor = AuditActor.user(principal.id());
        BigDecimal releasedCredits = order.reservedCredits();
        int releasedTokens = order.getSide() == OrderSide.SELL ? order.remaining() : 0;
        OrderSnapshot before = OrderSnapshot.of(order);
        order.cancel(now);
        auditService.audit(actor, AuditAction.ORDER_CANCELLED, MarketEntityTypes.ORDER, order.getId(), before,
                OrderSnapshot.of(order));
        if (releasedTokens > 0) {
            locks.lockUsers(List.of(principal.id()));
            Holding holding = locks.lockHolding(principal.id(), playerId)
                    .orElseThrow(() -> new IllegalStateException("market_holding_missing orderId=" + orderId));
            HoldingSnapshot holdingBefore = HoldingSnapshot.of(holding);
            holding.release(releasedTokens, now);
            auditService.audit(actor, AuditAction.HOLDING_CHANGED, MarketEntityTypes.HOLDING, holding.getId(),
                    holdingBefore, HoldingSnapshot.of(holding));
        }
        log.info("market_order_cancelled orderId={} userId={} releasedCredits={} releasedTokens={}", orderId,
                principal.id(), releasedCredits, releasedTokens);
        return new OrderResult(order, tradeRepository.findByBuyOrderIdOrSellOrderIdOrderByIdAsc(orderId, orderId));
    }

    private OrderResult place(MarketPrincipal principal, OrderSide side, OrderRequest request) {
        requireCoherent(request);
        Player player = locks.lockPlayer(request.playerId());
        requireQuotedAndWithinBand(player, request);

        List<MarketOrder> book = locks.lockOppositeBook(player.getId(), side);
        IncomingOrder incoming = new IncomingOrder(principal.id(), side, request.type(), request.limitPrice(),
                request.quantity());
        MatchResult match = engine.match(incoming, book.stream().map(BookEntry::of).toList());

        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(principal.id());
        match.fills().forEach(fill -> userIds.add(fill.counterpartyUserId()));
        Map<Long, User> users = locks.lockUsers(userIds);
        Map<Long, Holding> holdings = locks.lockHoldings(userIds, player.getId());
        User owner = users.get(principal.id());
        requireFunds(side, request, match, owner, holdings.get(owner.getId()));

        Instant now = Instant.now(clock);
        MarketOrder order = orderRepository.save(request.type() == OrderType.LIMIT
                ? MarketOrder.limit(owner, player, side, request.quantity(), request.limitPrice(), now)
                : MarketOrder.market(owner, player, side, request.quantity(), now));
        AuditActor actor = AuditActor.user(owner.getId());
        auditService.audit(actor, AuditAction.ORDER_CREATED, MarketEntityTypes.ORDER, order.getId(), null,
                OrderSnapshot.of(order));

        Map<Long, MarketOrder> bookById = book.stream()
                .collect(Collectors.toMap(MarketOrder::getId, Function.identity()));
        Fills fills = new Fills(order, player, users, holdings, actor, now);
        List<Trade> trades = new ArrayList<>();
        for (Fill fill : match.fills()) {
            trades.add(applyFill(fills, bookById.get(fill.bookOrderId()), fill));
        }
        settleRemainder(fills);

        log.info("market_order_placed orderId={} userId={} playerId={} side={} type={} quantity={} filled={} status={}",
                order.getId(), owner.getId(), player.getId(), side, order.getType(), order.getQuantity(),
                order.getFilledQuantity(), order.getStatus());
        return new OrderResult(order, trades);
    }

    private static void requireCoherent(OrderRequest request) {
        if (request.type() == OrderType.LIMIT && request.limitPrice() == null) {
            throw new InvalidOrderException("limitPrice: es obligatorio en una orden LIMIT");
        }
        if (request.type() == OrderType.MARKET && request.limitPrice() != null) {
            throw new InvalidOrderException("limitPrice: no se admite en una orden MARKET");
        }
    }

    private void requireQuotedAndWithinBand(Player player, OrderRequest request) {
        BigDecimal marketValue = player.getMarketValue();
        if (marketValue == null) {
            throw new PlayerNotQuotedException(player.getId());
        }
        if (request.type() == OrderType.LIMIT && !engine.isWithinBand(request.limitPrice(), marketValue)) {
            throw new PriceOutOfBandException(request.limitPrice(),
                    marketValue.multiply(OrderMatchingEngineService.BAND_LOWER),
                    marketValue.multiply(OrderMatchingEngineService.BAND_UPPER));
        }
    }

    /** Compra: el costo de lo que se ejecuta más la reserva de lo que queda; venta: tokens disponibles (research D4). */
    private void requireFunds(OrderSide side, OrderRequest request, MatchResult match, User owner, Holding holding) {
        if (side == OrderSide.BUY) {
            BigDecimal cost = match.total();
            if (request.type() == OrderType.LIMIT) {
                cost = cost.add(request.limitPrice().multiply(BigDecimal.valueOf(match.remaining())));
            }
            BigDecimal available = owner.getBalance().subtract(orderRepository.sumReservedCredits(owner.getId()));
            if (cost.compareTo(available) > 0) {
                throw new InsufficientBalanceException(cost, available);
            }
            return;
        }
        int available = holding == null ? 0 : holding.available();
        if (request.quantity() > available) {
            throw new InsufficientTokensException(request.quantity(), available);
        }
    }

    /**
     * Aplica un calce: tokens del vendedor al comprador, créditos del comprador al vendedor, las dos órdenes y la
     * operación, con la auditoría del antes y el después de cada cosa. Simétrico para compra y venta.
     */
    private Trade applyFill(Fills fills, MarketOrder bookOrder, Fill fill) {
        MarketOrder incoming = fills.order();
        boolean incomingBuys = incoming.getSide() == OrderSide.BUY;
        MarketOrder buyOrder = incomingBuys ? incoming : bookOrder;
        MarketOrder sellOrder = incomingBuys ? bookOrder : incoming;
        Long ownerId = incoming.getUser().getId();
        User buyer = fills.users().get(incomingBuys ? ownerId : fill.counterpartyUserId());
        User seller = fills.users().get(incomingBuys ? fill.counterpartyUserId() : ownerId);
        int quantity = fill.quantity();
        Instant now = fills.now();

        Holding sellerHolding = fills.holdings().get(seller.getId());
        HoldingSnapshot sellerHoldingBefore = HoldingSnapshot.of(sellerHolding);
        if (sellOrder == bookOrder) {
            sellerHolding.consumeReserved(quantity, now);
        } else {
            sellerHolding.debit(quantity, now);
        }
        Holding buyerHolding = fills.holdings().get(buyer.getId());
        HoldingSnapshot buyerHoldingBefore = buyerHolding == null ? null : HoldingSnapshot.of(buyerHolding);
        if (buyerHolding == null) {
            buyerHolding = holdingRepository.save(Holding.issue(buyer, fills.player(), quantity, now));
            fills.holdings().put(buyer.getId(), buyerHolding);
        } else {
            buyerHolding.credit(quantity, now);
        }

        BigDecimal total = fill.price().multiply(BigDecimal.valueOf(quantity));
        BalanceSnapshot buyerBalanceBefore = BalanceSnapshot.of(buyer);
        BalanceSnapshot sellerBalanceBefore = BalanceSnapshot.of(seller);
        buyer.setBalance(buyer.getBalance().subtract(total));
        seller.setBalance(seller.getBalance().add(total));

        OrderSnapshot incomingBefore = OrderSnapshot.of(incoming);
        OrderSnapshot bookBefore = OrderSnapshot.of(bookOrder);
        incoming.fill(quantity, now);
        bookOrder.fill(quantity, now);
        Trade trade = tradeRepository.save(Trade.of(buyOrder, sellOrder, quantity, fill.price(), now));

        AuditActor actor = fills.actor();
        auditService.audit(actor, AuditAction.TRADE_EXECUTED, MarketEntityTypes.TRADE, trade.getId(), null,
                TradeSnapshot.of(trade));
        auditService.audit(actor, AuditAction.ORDER_FILLED, MarketEntityTypes.ORDER, incoming.getId(), incomingBefore,
                OrderSnapshot.of(incoming));
        auditService.audit(actor, AuditAction.ORDER_FILLED, MarketEntityTypes.ORDER, bookOrder.getId(), bookBefore,
                OrderSnapshot.of(bookOrder));
        auditService.audit(actor, AuditAction.BALANCE_CHANGED, MarketEntityTypes.USER, buyer.getId(),
                buyerBalanceBefore, BalanceSnapshot.of(buyer));
        auditService.audit(actor, AuditAction.BALANCE_CHANGED, MarketEntityTypes.USER, seller.getId(),
                sellerBalanceBefore, BalanceSnapshot.of(seller));
        auditService.audit(actor, AuditAction.HOLDING_CHANGED, MarketEntityTypes.HOLDING, buyerHolding.getId(),
                buyerHoldingBefore, HoldingSnapshot.of(buyerHolding));
        auditService.audit(actor, AuditAction.HOLDING_CHANGED, MarketEntityTypes.HOLDING, sellerHolding.getId(),
                sellerHoldingBefore, HoldingSnapshot.of(sellerHolding));

        log.info("market_trade_executed tradeId={} playerId={} buyerId={} sellerId={} quantity={} price={}",
                trade.getId(), fills.player().getId(), buyer.getId(), seller.getId(), quantity, trade.getPrice());
        return trade;
    }

    /** Lo que una MARKET no llenó se cancela; lo que una venta LIMIT deja en el libro reserva tokens. */
    private void settleRemainder(Fills fills) {
        MarketOrder order = fills.order();
        if (order.remaining() == 0) {
            return;
        }
        if (order.getType() == OrderType.MARKET) {
            OrderSnapshot before = OrderSnapshot.of(order);
            order.cancel(fills.now());
            auditService.audit(fills.actor(), AuditAction.ORDER_CANCELLED, MarketEntityTypes.ORDER, order.getId(),
                    before, OrderSnapshot.of(order));
        } else if (order.getSide() == OrderSide.SELL) {
            Holding holding = fills.holdings().get(order.getUser().getId());
            HoldingSnapshot before = HoldingSnapshot.of(holding);
            holding.reserve(order.remaining(), fills.now());
            auditService.audit(fills.actor(), AuditAction.HOLDING_CHANGED, MarketEntityTypes.HOLDING, holding.getId(),
                    before, HoldingSnapshot.of(holding));
        }
    }

    /** Lo que comparten los calces de un mismo pedido: la orden que llega y lo bloqueado para ella. */
    private record Fills(MarketOrder order, Player player, Map<Long, User> users, Map<Long, Holding> holdings,
                         AuditActor actor, Instant now) {
    }
}
