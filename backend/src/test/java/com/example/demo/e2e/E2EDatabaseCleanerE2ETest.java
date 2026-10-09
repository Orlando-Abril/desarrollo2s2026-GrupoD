package com.example.demo.e2e;

import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class E2EDatabaseCleanerE2ETest {

    private static final String REDIS_KEY = "e2e:cleaner:probe";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    E2EDatabaseCleanerE2ETest(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Test
    void firstMethodStartsCleanAndCanReuseFixedIdentifiers() {
        assertCleanThenSeed();
    }

    @Test
    void secondMethodStartsCleanAndCanReuseFixedIdentifiers() {
        assertCleanThenSeed();
    }

    private void assertCleanThenSeed() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
        assertThat(redisTemplate.keys("*")).isEmpty();

        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (username, email, password_hash, created_at)
                VALUES ('e2e-cleaner-user', 'e2e-cleaner@example.com', 'test-hash', CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO api_keys (key_hash, key_prefix, user_id, active, created_at)
                VALUES ('e2e-cleaner-hash', 'e2e-clea', ?, true, CURRENT_TIMESTAMP)
                """, userId);
        redisTemplate.opsForValue().set(REDIS_KEY, "present");

        assertThat(userId).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM api_keys", Long.class)).isOne();
        assertThat(redisTemplate.opsForValue().get(REDIS_KEY)).isEqualTo("present");
    }
}
