package com.example.demo.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@UnitTestProfile
class UnitTestProfileTest {

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void activatesOnlyTheTestProfile() {
        assertThat(environment.getActiveProfiles()).containsExactly("test");
    }

    @Test
    void usesH2InMemoryWithPostgreSqlCompatibilityAndCreateDrop() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }

        assertThat(environment.getRequiredProperty("spring.datasource.url"))
                .contains("MODE=PostgreSQL");
        assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("create-drop");
    }

    @Test
    void usesTheSimpleInMemoryCacheManager() {
        assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
    }

    @Test
    void keepsFootballDataOnTheInaccessibleLoopbackEndpoint() {
        URI baseUrl = URI.create(environment.getRequiredProperty("football-data.base-url"));

        assertThat(baseUrl.getHost()).isEqualTo("127.0.0.1");
        assertThat(baseUrl.getPort()).isEqualTo(1);
        assertThat(baseUrl.getPath()).isEqualTo("/v4");
    }

    @Test
    void keepsWhoScoredOnTheInaccessibleLoopbackEndpoint() {
        URI baseUrl = URI.create(environment.getRequiredProperty("whoscored.base-url"));

        assertThat(baseUrl.getHost()).isEqualTo("127.0.0.1");
        assertThat(baseUrl.getPort()).isEqualTo(1);
    }
}
