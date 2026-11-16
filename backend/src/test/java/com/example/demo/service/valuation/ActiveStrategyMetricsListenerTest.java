package com.example.demo.service.valuation;

import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.support.UnitTestProfile;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 036, US2: los cambios de estrategia activa se cuentan sólo cuando se confirman. */
@SpringBootTest
@UnitTestProfile
class ActiveStrategyMetricsListenerTest {

    @Autowired ActiveStrategyService activeStrategyService;
    @Autowired ActiveStrategySettingRepository settings;
    @Autowired MeterRegistry meterRegistry;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        settings.deleteAll();
        jdbc.update("delete from audit_logs where entity_type = 'VALUATION_STRATEGY'");
    }

    @Test
    void aConfirmedChangeIsCounted() {
        double before = changes();

        assertThat(activeStrategyService.change("MOMENTUM", null)).isTrue();

        assertThat(changes() - before).isEqualTo(1);
    }

    @Test
    void changingToTheActiveStrategyIsNotCounted() {
        activeStrategyService.change("MOMENTUM", null);
        double before = changes();

        assertThat(activeStrategyService.change("MOMENTUM", null)).isFalse();

        assertThat(changes()).isEqualTo(before);
    }

    @Test
    void aRolledBackChangeIsNotCounted() {
        double before = changes();

        transactionTemplate.executeWithoutResult(status -> {
            activeStrategyService.change("MOMENTUM", null);
            status.setRollbackOnly();
        });

        assertThat(changes()).isEqualTo(before);
    }

    private double changes() {
        return meterRegistry.get("gdmarket.strategy.changes").counter().count();
    }
}
