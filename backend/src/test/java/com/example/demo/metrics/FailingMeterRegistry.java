package com.example.demo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.TimeUnit;

/**
 * Registry de prueba cuyos contadores y timers se registran bien pero fallan al medir. Simula una medición rota sin
 * romper la construcción de {@link BusinessMetrics} (un mock de Mockito no puede stubbear los métodos internos de
 * registro de Micrometer, y un {@code SimpleMeterRegistry} cerrado devuelve meters no-op que no fallan).
 */
public class FailingMeterRegistry extends SimpleMeterRegistry {

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new CumulativeCounter(id) {
            @Override
            public void increment(double amount) {
                throw new IllegalStateException("counter_unavailable");
            }
        };
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
                             PauseDetector pauseDetector) {
        return new CumulativeTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(), false) {
            @Override
            protected void recordNonNegative(long amount, TimeUnit unit) {
                throw new IllegalStateException("timer_unavailable");
            }
        };
    }
}
