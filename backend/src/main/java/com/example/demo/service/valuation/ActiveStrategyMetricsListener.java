package com.example.demo.service.valuation;

import com.example.demo.metrics.BusinessMetrics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Spec 036: cuenta los cambios de estrategia activa recién cuando se confirman; uno revertido no cuenta. */
@Component
public class ActiveStrategyMetricsListener {

    private final BusinessMetrics metrics;

    public ActiveStrategyMetricsListener(BusinessMetrics metrics) {
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActiveStrategyChanged(ActiveStrategyChangedEvent event) {
        metrics.activeStrategyChanged();
    }
}
