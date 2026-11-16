package com.example.demo.metrics;

import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderType;
import com.example.demo.repository.UserRepository;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class BusinessMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UserRepository users = mock(UserRepository.class);
    private final BusinessMetrics metrics = new BusinessMetrics(registry, users);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void registersEveryClosedCombinationAtZeroWithDescription() {
        assertThat(registry.find("gdmarket.orders.placed").counters()).hasSize(4)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(registry.find("gdmarket.orders.rejected").counters()).hasSize(3);
        assertThat(registry.find("gdmarket.quotes.recalculation").timers()).hasSize(4);
        assertThat(registry.find("gdmarket.external.calls").counters()).hasSize(6);
        assertThat(registry.find("gdmarket.auth.registrations").counters()).hasSize(2);
        assertThat(registry.find("gdmarket.auth.logins").counters()).hasSize(2);
        for (String single : List.of("gdmarket.trades.executed", "gdmarket.trades.tokens",
                "gdmarket.trades.volume.credits", "gdmarket.strategy.changes")) {
            assertThat(registry.get(single).counter().count()).as(single).isZero();
        }
        assertThat(registry.get("gdmarket.quotes.recalculation.last").timeGauge().value(TimeUnit.SECONDS)).isZero();
        assertThat(registry.find("gdmarket.users.registered").gauge()).isNotNull();
        assertThat(registry.find("gdmarket.quotes.calculated").counters()).isEmpty();

        List<Meter> meters = registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("gdmarket.")).toList();
        assertThat(meters).hasSize(27)
                .allSatisfy(meter -> assertThat(meter.getId().getDescription()).isNotBlank());
    }

    @Test
    void tagValuesComeFromClosedDomains() {
        Set<String> tagValues = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .collect(Collectors.toSet());
        assertThat(tagValues).containsExactlyInAnyOrder(
                "side=BUY", "side=SELL", "type=MARKET", "type=LIMIT",
                "reason=insufficient_balance", "reason=insufficient_tokens", "reason=price_out_of_band",
                "trigger=manual", "trigger=scheduled", "outcome=success", "outcome=failure",
                "source=football_data", "source=whoscored", "outcome=error", "outcome=cache");
    }

    @Test
    void immediateMethodsIncrementOnlyTheirCombination() {
        metrics.orderRejected(OrderRejectionReason.PRICE_OUT_OF_BAND);
        metrics.externalCall(ExternalSource.WHOSCORED, ExternalCallOutcome.CACHE);
        metrics.registration(AuthOutcome.FAILURE);
        metrics.login(AuthOutcome.SUCCESS);
        metrics.activeStrategyChanged();

        assertThat(count("gdmarket.orders.rejected", "reason", "price_out_of_band")).isEqualTo(1);
        assertThat(count("gdmarket.orders.rejected", "reason", "insufficient_balance")).isZero();
        assertThat(registry.get("gdmarket.external.calls").tags("source", "whoscored", "outcome", "cache")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("gdmarket.external.calls").tags("source", "football_data", "outcome", "cache")
                .counter().count()).isZero();
        assertThat(count("gdmarket.auth.registrations", "outcome", "failure")).isEqualTo(1);
        assertThat(count("gdmarket.auth.registrations", "outcome", "success")).isZero();
        assertThat(count("gdmarket.auth.logins", "outcome", "success")).isEqualTo(1);
        assertThat(registry.get("gdmarket.strategy.changes").counter().count()).isEqualTo(1);
    }

    @Test
    void ordersAndTradesCountImmediatelyWithoutTransaction() {
        metrics.orderCreated(OrderSide.BUY, OrderType.MARKET);
        metrics.tradeExecuted(3, new BigDecimal("30.50"));

        assertThat(orders(OrderSide.BUY, OrderType.MARKET)).isEqualTo(1);
        assertThat(orders(OrderSide.SELL, OrderType.MARKET)).isZero();
        assertThat(registry.get("gdmarket.trades.executed").counter().count()).isEqualTo(1);
        assertThat(registry.get("gdmarket.trades.tokens").counter().count()).isEqualTo(3);
        assertThat(registry.get("gdmarket.trades.volume.credits").counter().count()).isEqualTo(30.5);
    }

    @Test
    void ordersAndTradesWaitForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        metrics.orderCreated(OrderSide.SELL, OrderType.LIMIT);
        metrics.tradeExecuted(2, new BigDecimal("10"));

        assertThat(orders(OrderSide.SELL, OrderType.LIMIT)).isZero();
        assertThat(registry.get("gdmarket.trades.executed").counter().count()).isZero();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(orders(OrderSide.SELL, OrderType.LIMIT)).isEqualTo(1);
        assertThat(registry.get("gdmarket.trades.executed").counter().count()).isEqualTo(1);
        assertThat(registry.get("gdmarket.trades.tokens").counter().count()).isEqualTo(2);
    }

    @Test
    void rolledBackOrdersAndTradesAreNotCounted() {
        TransactionSynchronizationManager.initSynchronization();
        metrics.orderCreated(OrderSide.BUY, OrderType.LIMIT);
        metrics.tradeExecuted(1, BigDecimal.ONE);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(orders(OrderSide.BUY, OrderType.LIMIT)).isZero();
        assertThat(registry.get("gdmarket.trades.executed").counter().count()).isZero();
    }

    @Test
    void recalculationRecordsTimerAndLastDuration() {
        metrics.recalculationFinished(RecalculationTrigger.MANUAL, RecalculationOutcome.SUCCESS, Duration.ofSeconds(3));
        assertThat(lastSeconds()).isEqualTo(3.0);

        metrics.recalculationFinished(RecalculationTrigger.SCHEDULED, RecalculationOutcome.FAILURE,
                Duration.ofSeconds(1));
        assertThat(lastSeconds()).isEqualTo(1.0);

        assertThat(registry.get("gdmarket.quotes.recalculation").tags("trigger", "manual", "outcome", "success")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("gdmarket.quotes.recalculation").tags("trigger", "scheduled", "outcome", "failure")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("gdmarket.quotes.recalculation").tags("trigger", "manual", "outcome", "failure")
                .timer().count()).isZero();
    }

    @Test
    void quotesCalculatedCreatesTheStrategyCounterOnFirstUse() {
        metrics.quotesCalculated("OFFICIAL", 5);
        metrics.quotesCalculated("OFFICIAL", 2);
        metrics.quotesCalculated("MOMENTUM", 0);

        assertThat(count("gdmarket.quotes.calculated", "strategy", "OFFICIAL")).isEqualTo(7);
        assertThat(registry.find("gdmarket.quotes.calculated").tag("strategy", "MOMENTUM").counter()).isNull();
    }

    @Test
    void registeredUsersGaugeReadsTheRepository() {
        when(users.count()).thenReturn(42L);
        assertThat(registry.get("gdmarket.users.registered").gauge().value()).isEqualTo(42.0);
    }

    @Test
    void registeredUsersGaugeIsNaNWhenCountFails(CapturedOutput output) {
        when(users.count()).thenThrow(new IllegalStateException("db down"));
        assertThat(registry.get("gdmarket.users.registered").gauge().value()).isNaN();
        assertThat(output).contains("business_metric_failed metric=gdmarket.users.registered");
    }

    @Test
    void failingMeasurementsNeverPropagate(CapturedOutput output) {
        BusinessMetrics broken = new BusinessMetrics(new FailingMeterRegistry(), users);

        assertThatNoException().isThrownBy(() -> {
            broken.orderCreated(OrderSide.BUY, OrderType.MARKET);
            broken.orderRejected(OrderRejectionReason.INSUFFICIENT_BALANCE);
            broken.tradeExecuted(1, BigDecimal.TEN);
            broken.recalculationFinished(RecalculationTrigger.MANUAL, RecalculationOutcome.SUCCESS, Duration.ZERO);
            broken.quotesCalculated("OFFICIAL", 1);
            broken.activeStrategyChanged();
            broken.externalCall(ExternalSource.FOOTBALL_DATA, ExternalCallOutcome.ERROR);
            broken.registration(AuthOutcome.SUCCESS);
            broken.login(AuthOutcome.FAILURE);
        });
        assertThat(output).contains("business_metric_failed metric=gdmarket.orders.placed")
                .contains("business_metric_failed metric=gdmarket.quotes.recalculation errorType=IllegalStateException");
    }

    @Test
    void failingMeasurementAfterCommitDoesNotPropagate() {
        BusinessMetrics broken = new BusinessMetrics(new FailingMeterRegistry(), users);
        TransactionSynchronizationManager.initSynchronization();
        broken.tradeExecuted(1, BigDecimal.TEN);

        assertThatNoException().isThrownBy(() ->
                TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit));
    }

    private double count(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).counter().count();
    }

    private double orders(OrderSide side, OrderType type) {
        return registry.get("gdmarket.orders.placed").tags("side", side.name(), "type", type.name()).counter().count();
    }

    private double lastSeconds() {
        return registry.get("gdmarket.quotes.recalculation.last").timeGauge().value(TimeUnit.SECONDS);
    }
}
