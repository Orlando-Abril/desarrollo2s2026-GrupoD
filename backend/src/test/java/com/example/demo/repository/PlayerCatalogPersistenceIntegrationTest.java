package com.example.demo.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PlayerCatalogPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway flyway;

    @BeforeEach
    void migrate() {
        flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false).load();
        flyway.clean();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
    }

    @Test
    void cleanDatabaseGetsBaselineFeatureTablesAndIndexes() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var rs = statement.executeQuery("select count(*) from pg_indexes where indexname in "
                    + "('uq_players_external_id_not_null','idx_players_league','idx_players_team_lower','idx_player_positions_position')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(4);
            }
            statement.executeUpdate("insert into players(external_id,full_name,team,league,market_value) "
                    + "values ('99','One','Team','PREMIER_LEAGUE',1.00)");
            assertThatThrownBy(() -> statement.executeUpdate("insert into players(external_id,full_name,team,league,market_value) "
                    + "values ('99','Two','Team','PREMIER_LEAGUE',1.00)"))
                    .hasMessageContaining("uq_players_external_id_not_null");
        }
    }

    @Test
    void migrationFailsDiagnosticallyWhenExternalIdsAreDuplicated() throws Exception {
        flyway.clean();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("1").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("insert into players(external_id,full_name,team,league,market_value) values "
                    + "('dup','One','Team','PREMIER_LEAGUE',1),('dup','Two','Team','PREMIER_LEAGUE',1)");
        }
        assertThatThrownBy(() -> Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate())
                .hasMessageContaining("duplicate values exist");
    }

    @Test
    void existingNonEmptySchemaIsBaselinedAtOneAndReceivesV2() throws Exception {
        flyway.clean();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("create table users(id bigserial primary key, username varchar(255) not null unique, email varchar(255) not null unique, password_hash varchar(255) not null, role varchar(32) not null, balance numeric, created_at timestamp not null)");
            statement.execute("create table players(id bigserial primary key, external_id varchar(255), full_name varchar(255) not null, team varchar(255) not null, league varchar(32) not null, nationality varchar(255), age integer, height_cm integer, market_value numeric not null)");
            statement.execute("create table player_positions(player_id bigint not null references players(id), position varchar(32) not null)");
            statement.execute("insert into users(username,email,password_hash,role,balance,created_at) values ('existing','existing@test','hash','ADMIN',0,now())");
        }
        var result = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true).baselineVersion("1").load().migrate();
        assertThat(result.migrationsExecuted).isOne();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var rs = statement.executeQuery("select count(*) from player_token_allocations")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void tokenAllocationCannotBeIssuedTwiceForAPlayer() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users(username,email,password_hash,role,balance,created_at) values ('owner','owner@test','hash','ADMIN',0,now())");
            statement.executeUpdate("insert into players(external_id,full_name,team,league,market_value) values ('allocation','One','Team','PREMIER_LEAGUE',1)");
            statement.executeUpdate("insert into player_token_allocations(player_id,owner_user_id,total_supply,owner_quantity,base_price,created_at) values (1,1,100,100,1,now())");
            assertThatThrownBy(() -> statement.executeUpdate("insert into player_token_allocations(player_id,owner_user_id,total_supply,owner_quantity,base_price,created_at) values (1,1,100,100,1,now())"))
                    .hasMessageContaining("player_token_allocations_player_id_key");
        }
    }
}
