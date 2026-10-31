package com.example.demo.service.market;

import com.example.demo.dto.market.PortfolioPositionResponse;
import com.example.demo.dto.strategy.StrategyRef;
import com.example.demo.model.Player;
import com.example.demo.model.PlayerQuote;
import com.example.demo.model.QuoteStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioStrategyValuatorTest {

    private static final StrategyRef MOMENTUM = new StrategyRef("MOMENTUM", 1, "MOMENTUM v1");
    private static final Instant AT = Instant.parse("2026-10-28T03:00:00Z");

    @Test
    void valuesEachPositionAndTotalsWithTheStrategyQuote() {
        PortfolioPositionResponse first = position(1L, 15, "16.0000", "1.3000");
        PortfolioPositionResponse second = position(2L, 4, "4.0000", "1.0000");

        PortfolioStrategyValuator.Result result = PortfolioStrategyValuator.valuate(MOMENTUM, List.of(first, second),
                bd("100.0000"), bd("20.0000"), Map.of(
                        1L, quote(1L, "1.1000", QuoteStatus.CALCULATED),
                        2L, quote(2L, "0.7500", QuoteStatus.CALCULATED)));

        assertThat(result.positions().get(0).strategyValuation().value()).isEqualByComparingTo("16.5000");
        assertThat(result.positions().get(0).strategyValuation().profitLoss()).isEqualByComparingTo("0.5000");
        assertThat(result.positions().get(0).currentValue()).isEqualByComparingTo("19.5000");
        assertThat(result.positions().get(1).strategyValuation().value()).isEqualByComparingTo("3.0000");
        assertThat(result.positions().get(1).strategyValuation().profitLoss()).isEqualByComparingTo("-1.0000");
        assertThat(result.totals().strategy()).isEqualTo(MOMENTUM);
        assertThat(result.totals().totalValue()).isEqualByComparingTo("19.5000");
        assertThat(result.totals().totalProfitLoss()).isEqualByComparingTo("-0.5000");
        assertThat(result.totals().totalEquity()).isEqualByComparingTo("119.5000");
        assertThat(result.totals().positionsWithoutQuote()).isZero();
    }

    @Test
    void usesAndReportsNonCalculatedQuotes() {
        PortfolioStrategyValuator.Result result = PortfolioStrategyValuator.valuate(MOMENTUM,
                List.of(position(1L, 10, "10.0000", "1.0000")), bd("0.0000"), bd("10.0000"),
                Map.of(1L, quote(1L, "1.0450", QuoteStatus.NO_STATS)));

        assertThat(result.positions().get(0).strategyValuation().quotePrice()).isEqualByComparingTo("1.0450");
        assertThat(result.positions().get(0).strategyValuation().quoteStatus()).isEqualTo(QuoteStatus.NO_STATS);
        assertThat(result.positions().get(0).strategyValuation().quoteCalculatedAt()).isEqualTo(AT);
        assertThat(result.positions().get(0).strategyValuation().value()).isEqualByComparingTo("10.4500");
    }

    @Test
    void aPositionWithoutQuoteLeavesItsFieldsAndTheTotalsEmpty() {
        PortfolioStrategyValuator.Result result = PortfolioStrategyValuator.valuate(MOMENTUM,
                List.of(position(1L, 10, "10.0000", "1.0000"), position(2L, 5, "5.0000", "1.0000")),
                bd("50.0000"), bd("15.0000"), Map.of(1L, quote(1L, "1.2000", QuoteStatus.CALCULATED)));

        assertThat(result.positions().get(1).strategyValuation())
                .extracting("quotePrice", "quoteStatus", "quoteCalculatedAt", "value", "profitLoss")
                .containsOnlyNulls();
        assertThat(result.positions().get(0).strategyValuation().value()).isEqualByComparingTo("12.0000");
        assertThat(result.totals().totalValue()).isNull();
        assertThat(result.totals().totalProfitLoss()).isNull();
        assertThat(result.totals().totalEquity()).isNull();
        assertThat(result.totals().positionsWithoutQuote()).isEqualTo(1);
    }

    @Test
    void keepsFourDecimalsWithoutIntermediateRounding() {
        PortfolioStrategyValuator.Result result = PortfolioStrategyValuator.valuate(MOMENTUM,
                List.of(position(1L, 3, "3.0001", "1.0000"), position(2L, 3, "3.0001", "1.0000")),
                bd("0.0000"), bd("6.0002"), Map.of(
                        1L, quote(1L, "1.3333", QuoteStatus.CALCULATED),
                        2L, quote(2L, "1.3333", QuoteStatus.CALCULATED)));

        assertThat(result.totals().totalValue()).isEqualTo(bd("7.9998"));
        assertThat(result.totals().totalProfitLoss()).isEqualTo(bd("1.9996"));
    }

    private static PortfolioPositionResponse position(long playerId, int quantity, String cost, String marketValue) {
        BigDecimal current = bd(marketValue).multiply(BigDecimal.valueOf(quantity));
        return new PortfolioPositionResponse(playerId, "Jugador " + playerId, "Club", quantity, 0, quantity,
                bd(cost).divide(BigDecimal.valueOf(quantity), 4, java.math.RoundingMode.HALF_EVEN), bd(cost),
                bd(marketValue), current, current.subtract(bd(cost)));
    }

    private static PlayerQuote quote(long playerId, String value, QuoteStatus status) {
        return PlayerQuote.builder().player(Player.builder().id(playerId).build()).strategyCode("MOMENTUM")
                .strategyVersion(1).value(bd(value)).status(status).calculatedAt(AT).runId(UUID.randomUUID())
                .correlationId("test").build();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
