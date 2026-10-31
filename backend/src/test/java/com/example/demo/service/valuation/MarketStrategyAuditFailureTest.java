package com.example.demo.service.valuation;

import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.service.audit.TransactionAuditService;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Spec 031, research R4: si la auditoría falla, la estrategia de mercado no cambia. */
@SpringBootTest
@UnitTestProfile
class MarketStrategyAuditFailureTest {

    @Autowired ActiveStrategyService service;
    @Autowired ActiveStrategySettingRepository settings;
    @MockitoBean TransactionAuditService auditService;

    @BeforeEach
    @AfterEach
    void clean() {
        settings.deleteAll();
    }

    @Test
    void aFailedAuditRollsBackTheMarketStrategy() {
        when(auditService.audit(any(), any(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException("audit down"));

        assertThatThrownBy(() -> service.recordMarketStrategy("MOMENTUM", Instant.now(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(settings.findById(ActiveStrategySetting.SINGLETON_ID)
                .map(ActiveStrategySetting::getMarketStrategyCode)).isEmpty();
    }
}
