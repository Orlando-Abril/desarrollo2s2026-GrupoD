package com.example.demo.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class CatalogSyncAuditIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();
    }

    @Test
    void auditRequiresTraceFieldsAndRejectsUpdateAndDelete() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users(username,email,password_hash,role,balance,created_at) "
                    + "values ('admin','admin@test','hash','ADMIN',0,now())");
            statement.executeUpdate("insert into catalog_sync_audit_events(actor_user_id,correlation_id,occurred_at,action,detail,entity_type,entity_id,before_state,after_state) "
                    + "values (1,'" + UUID.randomUUID() + "',now(),'PLAYER_CREATED','created','Player','1',null,'id=1')");
            assertThatThrownBy(() -> statement.executeUpdate("update catalog_sync_audit_events set detail='changed'"))
                    .hasMessageContaining("append-only");
            assertThatThrownBy(() -> statement.executeUpdate("delete from catalog_sync_audit_events"))
                    .hasMessageContaining("append-only");
        }
    }
}
