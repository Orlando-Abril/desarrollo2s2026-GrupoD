package com.example.demo.service.market;

import com.example.demo.dto.market.PortfolioPositionResponse;
import com.example.demo.dto.market.PortfolioStrategyValuationResponse;
import com.example.demo.dto.market.PositionStrategyValuationResponse;
import com.example.demo.dto.strategy.StrategyRef;
import com.example.demo.model.PlayerQuote;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Valúa un portfolio ya construido con la última cotización de otra estrategia (spec 031). Usa la cotización
 * cualquiera sea su estado; si un jugador no tiene ninguna, su posición y los totales quedan vacíos.
 */
public final class PortfolioStrategyValuator {

    private PortfolioStrategyValuator() {
    }

    public record Result(List<PortfolioPositionResponse> positions, PortfolioStrategyValuationResponse totals) {
    }

    public static Result valuate(StrategyRef strategy, List<PortfolioPositionResponse> positions, BigDecimal balance,
                                 BigDecimal totalCost, Map<Long, PlayerQuote> latestByPlayer) {
        List<PortfolioPositionResponse> valued = positions.stream()
                .map(position -> position.withStrategyValuation(valuation(position, latestByPlayer.get(position.playerId()))))
                .toList();
        int withoutQuote = (int) valued.stream().filter(position -> !position.strategyValuation().quoted()).count();
        if (withoutQuote > 0) {
            return new Result(valued, new PortfolioStrategyValuationResponse(strategy, null, null, null, withoutQuote));
        }
        BigDecimal totalValue = valued.stream().map(position -> position.strategyValuation().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Result(valued, new PortfolioStrategyValuationResponse(strategy, totalValue,
                totalValue.subtract(totalCost), balance.add(totalValue), 0));
    }

    private static PositionStrategyValuationResponse valuation(PortfolioPositionResponse position, PlayerQuote quote) {
        if (quote == null) {
            return PositionStrategyValuationResponse.missing();
        }
        return PositionStrategyValuationResponse.of(quote.getValue(), quote.getStatus(), quote.getCalculatedAt(),
                position.quantity(), position.costBasis());
    }
}
