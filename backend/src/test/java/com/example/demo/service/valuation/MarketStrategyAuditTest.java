package com.example.demo.service.valuation;

import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActorType;
import com.example.demo.model.market.AuditLog;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.market.AuditLogRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 031, FR-022 y research R4: auditoría {@code MARKET_STRATEGY_APPLIED}, una por corrida que la cambia. */
@SpringBootTest
@UnitTestProfile
class MarketStrategyAuditTest {

    private static final Instant FIRST = Instant.parse("2026-10-20T06:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-27T06:00:00Z");

    @Autowired ActiveStrategyService service;
    @Autowired ActiveStrategySettingRepository settings;
    @Autowired AuditLogRepository auditLogs;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        settings.deleteAll();
        jdbc.update("delete from audit_logs where entity_type = 'VALUATION_STRATEGY'");
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void firstRunAndEveryChangeAuditOncePerRunWithSystemActor() {
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        UUID thirdRun = UUID.randomUUID();
        MDC.put(CorrelationIdFilter.MDC_KEY, "run-correlation-1");

        service.recordMarketStrategy("OFFICIAL", FIRST, firstRun);
        service.recordMarketStrategy("OFFICIAL", SECOND, secondRun);
        MDC.put(CorrelationIdFilter.MDC_KEY, "run-correlation-3");
        service.recordMarketStrategy("MOMENTUM", SECOND, thirdRun);

        List<AuditLog> logs = strategyLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AuditAction.MARKET_STRATEGY_APPLIED);
            assertThat(log.getActorType()).isEqualTo(AuditActorType.SYSTEM);
            assertThat(log.getActorUserId()).isNull();
            assertThat(log.getEntityId()).isEqualTo("market");
        });
        assertThat(logs.get(0).getBeforeState()).isNull();
        assertThat(logs.get(0).getAfterState()).contains("\"OFFICIAL\"", firstRun.toString());
        assertThat(logs.get(0).getCorrelationId()).isEqualTo("run-correlation-1");
        assertThat(logs.get(1).getBeforeState()).contains("\"OFFICIAL\"");
        assertThat(logs.get(1).getAfterState()).contains("\"MOMENTUM\"", thirdRun.toString());
        assertThat(logs.get(1).getCorrelationId()).isEqualTo("run-correlation-3");

        ActiveStrategySetting setting = settings.findById(ActiveStrategySetting.SINGLETON_ID).orElseThrow();
        assertThat(setting.getMarketStrategyCode()).isEqualTo("MOMENTUM");
        assertThat(setting.getMarketStrategyAppliedAt()).isEqualTo(SECOND);
        assertThat(setting.getActiveCode()).isNull();
        assertThat(service.view().market().code()).isEqualTo("MOMENTUM");
        assertThat(service.view().pendingRecalculation()).isTrue();
    }

    private List<AuditLog> strategyLogs() {
        return auditLogs.findByActionOrderByIdAsc(AuditAction.MARKET_STRATEGY_APPLIED);
    }
}
