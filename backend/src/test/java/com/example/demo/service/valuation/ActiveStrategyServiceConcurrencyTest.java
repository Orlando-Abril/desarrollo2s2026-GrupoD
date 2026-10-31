package com.example.demo.service.valuation;

import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditLog;
import com.example.demo.repository.ActiveStrategySettingRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.AuditLogRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 031, research R13: cambios simultáneos sobre una fila inexistente se serializan y auditan su estado real. */
@SpringBootTest
@UnitTestProfile
class ActiveStrategyServiceConcurrencyTest {

    @Autowired ActiveStrategyService service;
    @Autowired ActiveStrategySettingRepository settings;
    @Autowired AuditLogRepository auditLogs;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        settings.deleteAll();
        jdbc.update("delete from audit_logs where entity_type = 'VALUATION_STRATEGY'");
    }

    @RepeatedTest(3)
    void simultaneousChangesOnAMissingRowAreSerialized() throws Exception {
        User admin = AuthFixtures.user(users, "concurrent-admin-" + UUID.randomUUID(), Role.ADMIN);
        Authentication authentication = new UsernamePasswordAuthenticationToken(admin.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Boolean> toMomentum = CompletableFuture.supplyAsync(() -> {
            await(start);
            return service.change("MOMENTUM", authentication);
        });
        CompletableFuture<Boolean> toMomentumAgain = CompletableFuture.supplyAsync(() -> {
            await(start);
            return service.change("MOMENTUM", authentication);
        });
        start.countDown();

        List<Boolean> results = List.of(toMomentum.get(30, TimeUnit.SECONDS), toMomentumAgain.get(30, TimeUnit.SECONDS));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        List<AuditLog> logs = auditLogs.findByActionOrderByIdAsc(AuditAction.ACTIVE_STRATEGY_CHANGED);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getBeforeState()).contains("\"OFFICIAL\"");
        assertThat(logs.get(0).getAfterState()).contains("\"MOMENTUM\"");
        assertThat(settings.findById(ActiveStrategySetting.SINGLETON_ID).orElseThrow().getActiveCode())
                .isEqualTo("MOMENTUM");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
