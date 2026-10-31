package com.example.demo.service.market;

import com.example.demo.dto.market.PortfolioPositionResponse;
import com.example.demo.dto.market.PortfolioResponse;
import com.example.demo.dto.market.TransactionPageResponse;
import com.example.demo.dto.market.TransactionResponse;
import com.example.demo.dto.market.TransactionType;
import com.example.demo.dto.strategy.StrategyRef;
import com.example.demo.exception.AuthenticatedUserNotFoundException;
import com.example.demo.exception.PortfolioInconsistentException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.User;
import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.PortfolioHoldingRow;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.repository.market.UserTradeRow;
import com.example.demo.service.valuation.strategy.ValuationStrategyCatalog;
import com.example.demo.service.valuation.strategy.ValuationStrategyDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PortfolioService {

    private static final int SCALE = 4;
    private static final BigDecimal INITIAL_TOKEN_COST = new BigDecimal("1.0000");
    private static final int INITIAL_TOKEN_QUANTITY = 100;

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final MarketOrderRepository orderRepository;
    private final SuperuserAccount superuserAccount;
    private final ValuationStrategyCatalog strategyCatalog;
    private final PlayerQuoteRepository quoteRepository;

    public PortfolioService(UserRepository userRepository, HoldingRepository holdingRepository,
                            TradeRepository tradeRepository, MarketOrderRepository orderRepository,
                            SuperuserAccount superuserAccount) {
        this(userRepository, holdingRepository, tradeRepository, orderRepository, superuserAccount, null, null);
    }

    @Autowired
    public PortfolioService(UserRepository userRepository, HoldingRepository holdingRepository,
                            TradeRepository tradeRepository, MarketOrderRepository orderRepository,
                            SuperuserAccount superuserAccount, ValuationStrategyCatalog strategyCatalog,
                            PlayerQuoteRepository quoteRepository) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        this.superuserAccount = superuserAccount;
        this.strategyCatalog = strategyCatalog;
        this.quoteRepository = quoteRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PortfolioResponse portfolio(Long targetUserId, Authentication authentication) {
        return portfolio(targetUserId, authentication, Optional.empty());
    }

    /**
     * Portfolio de la cuenta; con {@code strategy} agrega la valuación con la última cotización de esa estrategia
     * (spec 031). La estrategia se valida antes de autorizar: no revela datos de ninguna cuenta.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PortfolioResponse portfolio(Long targetUserId, Authentication authentication, Optional<String> strategy) {
        Optional<ValuationStrategyDefinition> definition = strategy.map(this::resolveStrategy);
        User target = authorizeAndLoad(targetUserId, authentication);
        List<PortfolioHoldingRow> holdingRows = holdingRepository.findPortfolioRows(targetUserId);
        List<UserTradeRow> tradeRows = tradeRepository.findCostBasisRows(targetUserId);
        boolean isSuperuser = superuserAccount.username().equals(target.getUsername());

        Map<Long, PositionCostState> states = buildCostStates(targetUserId, holdingRows, tradeRows, isSuperuser);
        validateHoldingConsistency(holdingRows, states);
        List<PortfolioPositionResponse> positions = buildPositions(holdingRows, states);

        BigDecimal balance = money(target.getBalance());
        BigDecimal reserved = money(orderRepository.sumReservedCredits(targetUserId));
        BigDecimal totalCost = sum(positions.stream().map(PortfolioPositionResponse::costBasis).toList());
        BigDecimal totalCurrent = sum(positions.stream().map(PortfolioPositionResponse::currentValue).toList());
        BigDecimal totalProfit = sum(positions.stream().map(PortfolioPositionResponse::profitLoss).toList());
        PortfolioResponse portfolio = new PortfolioResponse(targetUserId, balance, reserved,
                money(balance.subtract(reserved)), positions, totalCost, totalCurrent, totalProfit,
                money(balance.add(totalCurrent)));
        return definition.map(selected -> withStrategy(portfolio, selected)).orElse(portfolio);
    }

    private ValuationStrategyDefinition resolveStrategy(String code) {
        if (strategyCatalog == null || quoteRepository == null) {
            throw new IllegalStateException("PortfolioService sin catálogo de estrategias");
        }
        return strategyCatalog.find(code);
    }

    private PortfolioResponse withStrategy(PortfolioResponse portfolio, ValuationStrategyDefinition strategy) {
        List<Long> playerIds = portfolio.positions().stream().map(PortfolioPositionResponse::playerId).toList();
        Map<Long, PlayerQuote> latest = new HashMap<>();
        if (!playerIds.isEmpty()) {
            quoteRepository.findLatestForPlayers(strategy.code(), playerIds)
                    .forEach(quote -> latest.put(quote.getPlayer().getId(), quote));
        }
        PortfolioStrategyValuator.Result valued = PortfolioStrategyValuator.valuate(StrategyRef.of(strategy),
                portfolio.positions(), portfolio.balance(), portfolio.totalCost(), latest);
        return new PortfolioResponse(portfolio.userId(), portfolio.balance(), portfolio.reservedCredits(),
                portfolio.availableBalance(), valued.positions(), portfolio.totalCost(),
                portfolio.totalCurrentValue(), portfolio.totalProfitLoss(), portfolio.totalEquity(), valued.totals());
    }

    private static Map<Long, PositionCostState> buildCostStates(Long targetUserId,
                                                                 List<PortfolioHoldingRow> holdingRows,
                                                                 List<UserTradeRow> tradeRows,
                                                                 boolean isSuperuser) {
        Map<Long, PositionCostState> states = new HashMap<>();
        if (isSuperuser) {
            holdingRows.forEach(row -> states.put(row.playerId(), PositionCostState.initialEmission()));
        }
        for (UserTradeRow trade : tradeRows) {
            PositionCostState state = states.computeIfAbsent(trade.playerId(), ignored ->
                    isSuperuser ? PositionCostState.initialEmission() : new PositionCostState());
            applyTrade(targetUserId, trade, state);
        }
        return states;
    }

    private static void applyTrade(Long targetUserId, UserTradeRow trade, PositionCostState state) {
        if (targetUserId.equals(trade.buyerId())) {
            state.buy(trade.quantity(), trade.price());
        } else if (targetUserId.equals(trade.sellerId())) {
            state.sell(trade.quantity());
        } else {
            throw inconsistent("trade_without_target_user:" + trade.id());
        }
    }

    private static void validateHoldingConsistency(List<PortfolioHoldingRow> holdingRows,
                                                   Map<Long, PositionCostState> states) {
        Map<Long, PortfolioHoldingRow> holdingsByPlayer = new LinkedHashMap<>();
        holdingRows.forEach(row -> holdingsByPlayer.put(row.playerId(), row));
        for (Map.Entry<Long, PositionCostState> entry : states.entrySet()) {
            int holdingQuantity = holdingsByPlayer.containsKey(entry.getKey())
                    ? holdingsByPlayer.get(entry.getKey()).quantity() : 0;
            if (entry.getValue().quantity != holdingQuantity) {
                throw inconsistent("holding_trade_quantity_mismatch:" + entry.getKey());
            }
        }
    }

    private static List<PortfolioPositionResponse> buildPositions(List<PortfolioHoldingRow> holdingRows,
                                                                   Map<Long, PositionCostState> states) {
        return holdingRows.stream()
                .map(row -> buildPosition(row, states.get(row.playerId())))
                .toList();
    }

    private static PortfolioPositionResponse buildPosition(PortfolioHoldingRow row, PositionCostState state) {
        if (state == null || state.quantity != row.quantity()) {
            throw inconsistent("missing_cost_basis:" + row.playerId());
        }
        if (row.marketValue() == null) {
            throw inconsistent("missing_market_value:" + row.playerId());
        }
        BigDecimal cost = money(state.cost);
        BigDecimal average = money(state.cost.divide(BigDecimal.valueOf(state.quantity), MathContext.DECIMAL128));
        BigDecimal marketValue = money(row.marketValue());
        BigDecimal current = money(marketValue.multiply(BigDecimal.valueOf(row.quantity())));
        BigDecimal profit = money(current.subtract(cost));
        return new PortfolioPositionResponse(row.playerId(), row.playerName(), row.playerTeam(), row.quantity(),
                row.reservedQuantity(), row.quantity() - row.reservedQuantity(), average, cost, marketValue, current,
                profit);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TransactionPageResponse transactions(Long targetUserId, Authentication authentication, int page, int size) {
        authorizeAndLoad(targetUserId, authentication);
        Page<UserTradeRow> rows = tradeRepository.findTransactionRows(targetUserId, PageRequest.of(page, size));
        List<TransactionResponse> items = rows.getContent().stream().map(row -> new TransactionResponse(
                row.id(), targetUserId.equals(row.buyerId()) ? TransactionType.COMPRA : TransactionType.VENTA,
                row.playerId(), row.playerName(), row.quantity(), money(row.price()), money(row.total()),
                row.executedAt())).toList();
        return new TransactionPageResponse(items, page, size, rows.getNumberOfElements(), rows.getTotalElements(),
                rows.getTotalPages(), rows.isFirst(), rows.isLast());
    }

    private User authorizeAndLoad(Long targetUserId, Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            if (authentication == null) {
                throw new AuthenticatedUserNotFoundException();
            }
            User actor = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(AuthenticatedUserNotFoundException::new);
            if (!actor.getId().equals(targetUserId)) {
                throw new AccessDeniedException("forbidden");
            }
        }
        return userRepository.findById(targetUserId).orElseThrow(UserNotFoundException::new);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return money(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    private static PortfolioInconsistentException inconsistent(String detail) {
        return new PortfolioInconsistentException(detail);
    }

    private static final class PositionCostState {
        private int quantity;
        private BigDecimal cost = BigDecimal.ZERO;

        private static PositionCostState initialEmission() {
            PositionCostState state = new PositionCostState();
            state.quantity = INITIAL_TOKEN_QUANTITY;
            state.cost = INITIAL_TOKEN_COST.multiply(BigDecimal.valueOf(INITIAL_TOKEN_QUANTITY));
            return state;
        }

        private void buy(int bought, BigDecimal price) {
            quantity = Math.addExact(quantity, bought);
            cost = cost.add(price.multiply(BigDecimal.valueOf(bought)));
        }

        private void sell(int sold) {
            if (sold <= 0 || sold > quantity) {
                throw inconsistent("sale_exceeds_position");
            }
            if (sold == quantity) {
                quantity = 0;
                cost = BigDecimal.ZERO;
                return;
            }
            BigDecimal average = cost.divide(BigDecimal.valueOf(quantity), MathContext.DECIMAL128);
            cost = cost.subtract(average.multiply(BigDecimal.valueOf(sold), MathContext.DECIMAL128));
            quantity -= sold;
        }
    }
}
