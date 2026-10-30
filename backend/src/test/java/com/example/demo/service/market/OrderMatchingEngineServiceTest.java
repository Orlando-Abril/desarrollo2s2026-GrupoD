package com.example.demo.service.market;

import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMatchingEngineServiceTest {

    private static final long BUYER = 1L;
    private static final long C = 10L;
    private static final long D = 11L;
    private static final long SUPERUSER = 99L;

    private final OrderMatchingEngineService engine = new OrderMatchingEngineService();

    @Test
    void limitBuyExecutesAtThePriceOfTheRestingSell() {
        MatchResult result = engine.match(limit(BUYER, OrderSide.BUY, "1.3000", 10),
                List.of(entry(1L, SUPERUSER, "1.2000", 100)));

        assertThat(result.fills()).containsExactly(new Fill(1L, SUPERUSER, 10, price("1.2000")));
        assertThat(result.remaining()).isZero();
    }

    @Test
    void limitSellExecutesAtThePriceOfTheRestingBuy() {
        MatchResult result = engine.match(limit(C, OrderSide.SELL, "1.0000", 5),
                List.of(entry(1L, BUYER, "1.1500", 5)));

        assertThat(result.fills()).containsExactly(new Fill(1L, BUYER, 5, price("1.1500")));
    }

    @Test
    void sweepsByBestPriceAndThenByAge() {
        MatchResult result = engine.match(market(BUYER, OrderSide.BUY, 8), List.of(
                entry(1L, C, "1.1000", 3),
                entry(2L, D, "1.1000", 3),
                entry(3L, SUPERUSER, "1.2000", 50)));

        assertThat(result.fills()).containsExactly(
                new Fill(1L, C, 3, price("1.1000")),
                new Fill(2L, D, 3, price("1.1000")),
                new Fill(3L, SUPERUSER, 2, price("1.2000")));
        assertThat(result.remaining()).isZero();
        assertThat(result.total()).isEqualByComparingTo("9.0000");
    }

    @Test
    void marketSellTakesTheHighestBuyFirst() {
        MatchResult result = engine.match(market(C, OrderSide.SELL, 1), List.of(
                entry(1L, BUYER, "1.1500", 5),
                entry(2L, D, "1.0000", 5)));

        assertThat(result.fills()).containsExactly(new Fill(1L, BUYER, 1, price("1.1500")));
    }

    @Test
    void limitBuyStopsAtTheFirstPriceAboveItsLimit() {
        MatchResult result = engine.match(limit(BUYER, OrderSide.BUY, "1.1000", 5), List.of(
                entry(1L, C, "1.1000", 2),
                entry(2L, D, "1.2000", 5)));

        assertThat(result.fills()).containsExactly(new Fill(1L, C, 2, price("1.1000")));
        assertThat(result.remaining()).isEqualTo(3);
    }

    @Test
    void limitSellStopsAtTheFirstPriceBelowItsLimit() {
        MatchResult result = engine.match(limit(C, OrderSide.SELL, "1.1000", 5), List.of(
                entry(1L, BUYER, "1.1500", 2),
                entry(2L, D, "1.0500", 5)));

        assertThat(result.fills()).containsExactly(new Fill(1L, BUYER, 2, price("1.1500")));
        assertThat(result.remaining()).isEqualTo(3);
    }

    @Test
    void limitPartiallyFilledLeavesTheRestPending() {
        MatchResult result = engine.match(limit(BUYER, OrderSide.BUY, "1.3000", 10),
                List.of(entry(1L, SUPERUSER, "1.2000", 4)));

        assertThat(result.fills()).containsExactly(new Fill(1L, SUPERUSER, 4, price("1.2000")));
        assertThat(result.remaining()).isEqualTo(6);
    }

    @Test
    void restingOrderIsFilledOnlyByTheIncomingQuantity() {
        MatchResult result = engine.match(market(BUYER, OrderSide.BUY, 3),
                List.of(entry(1L, SUPERUSER, "1.2000", 10)));

        assertThat(result.fills()).containsExactly(new Fill(1L, SUPERUSER, 3, price("1.2000")));
        assertThat(result.filledQuantity()).isEqualTo(3);
    }

    @Test
    void marketWithoutCounterpartyFillsNothing() {
        MatchResult result = engine.match(market(C, OrderSide.SELL, 5), List.of());

        assertThat(result.fills()).isEmpty();
        assertThat(result.remaining()).isEqualTo(5);
        assertThat(result.total()).isEqualByComparingTo("0");
    }

    @Test
    void marketWithAThinBookFillsWhatThereIs() {
        MatchResult result = engine.match(market(BUYER, OrderSide.BUY, 10),
                List.of(entry(1L, SUPERUSER, "1.2000", 4)));

        assertThat(result.filledQuantity()).isEqualTo(4);
        assertThat(result.remaining()).isEqualTo(6);
    }

    @Test
    void skipsOrdersOfTheSameUser() {
        MatchResult result = engine.match(market(BUYER, OrderSide.BUY, 3), List.of(
                entry(1L, BUYER, "1.1000", 5),
                entry(2L, C, "1.2000", 5)));

        assertThat(result.fills()).containsExactly(new Fill(2L, C, 3, price("1.2000")));
    }

    @Test
    void bandIncludesBothEnds() {
        BigDecimal marketValue = price("1.0000");

        assertThat(engine.isWithinBand(price("0.8000"), marketValue)).isTrue();
        assertThat(engine.isWithinBand(price("1.0000"), marketValue)).isTrue();
        assertThat(engine.isWithinBand(price("1.2000"), marketValue)).isTrue();
        assertThat(engine.isWithinBand(price("0.7999"), marketValue)).isFalse();
        assertThat(engine.isWithinBand(price("1.2001"), marketValue)).isFalse();
    }

    @Test
    void bandIsComparedExactlyWithoutRounding() {
        BigDecimal marketValue = price("1.2345");

        assertThat(engine.isWithinBand(price("0.9876"), marketValue)).isTrue();
        assertThat(engine.isWithinBand(price("0.9875"), marketValue)).isFalse();
        assertThat(engine.isWithinBand(price("1.4814"), marketValue)).isTrue();
        assertThat(engine.isWithinBand(price("1.4815"), marketValue)).isFalse();
    }

    @Test
    void doesNotMutateTheBook() {
        List<BookEntry> book = new ArrayList<>(List.of(entry(1L, C, "1.1000", 3), entry(2L, D, "1.2000", 3)));
        List<BookEntry> copy = List.copyOf(book);

        engine.match(market(BUYER, OrderSide.BUY, 5), book);

        assertThat(book).isEqualTo(copy);
    }

    private static IncomingOrder limit(long userId, OrderSide side, String limitPrice, int quantity) {
        return new IncomingOrder(userId, side, OrderType.LIMIT, price(limitPrice), quantity);
    }

    private static IncomingOrder market(long userId, OrderSide side, int quantity) {
        return new IncomingOrder(userId, side, OrderType.MARKET, null, quantity);
    }

    private static BookEntry entry(long orderId, long userId, String limitPrice, int remaining) {
        return new BookEntry(orderId, userId, price(limitPrice), remaining);
    }

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }
}
