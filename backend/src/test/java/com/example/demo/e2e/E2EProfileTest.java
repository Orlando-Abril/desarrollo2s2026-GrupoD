package com.example.demo.e2e;

import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class E2EProfileTest {

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void usesTheRealE2EInfrastructureConfiguration() throws SQLException {
        assertThat(environment.getActiveProfiles()).containsExactly("e2e");
        assertThat(environment.getRequiredProperty("spring.datasource.url"))
                .startsWith("jdbc:postgresql://");
        assertThat(environment.getRequiredProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.postgresql.Driver")
                .isNotEqualTo("org.h2.Driver");
        assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("create-drop");
        assertThat(environment.getRequiredProperty("spring.cache.type")).isEqualTo("redis");
        assertThat(environment.getRequiredProperty("spring.data.redis.host")).isNotBlank();
        assertThat(environment.getRequiredProperty("spring.data.redis.port")).isNotBlank();
        assertThat(environment.getRequiredProperty("football-data.base-url"))
                .isEqualTo("http://127.0.0.1:1/v4");
        assertThat(environment.getRequiredProperty("whoscored.base-url"))
                .isEqualTo("http://127.0.0.1:1");

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDriverName()).containsIgnoringCase("PostgreSQL");
        }
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }
}
