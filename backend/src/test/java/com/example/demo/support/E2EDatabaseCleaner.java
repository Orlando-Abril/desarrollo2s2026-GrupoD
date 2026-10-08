package com.example.demo.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands.FlushOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

public class E2EDatabaseCleaner implements BeforeEachCallback {

    private static final String TABLES_QUERY = """
            SELECT table_schema, table_name
            FROM information_schema.tables
            WHERE table_schema = current_schema()
              AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """;

    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        cleanPostgres(applicationContext.getBean(JdbcTemplate.class));
        cleanRedis(applicationContext.getBean(RedisConnectionFactory.class));
    }

    private void cleanPostgres(JdbcTemplate jdbcTemplate) {
        List<TableName> tables = jdbcTemplate.query(TABLES_QUERY,
                (resultSet, rowNumber) -> new TableName(
                        resultSet.getString("table_schema"),
                        resultSet.getString("table_name")));
        if (tables.isEmpty()) {
            return;
        }
        String qualifiedTables = tables.stream()
                .map(TableName::quoted)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        jdbcTemplate.execute("TRUNCATE TABLE " + qualifiedTables + " RESTART IDENTITY CASCADE");
    }

    private void cleanRedis(RedisConnectionFactory connectionFactory) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb(FlushOption.SYNC);
        }
    }

    private record TableName(String schema, String table) {
        String quoted() {
            return quote(schema) + "." + quote(table);
        }

        private static String quote(String identifier) {
            return '"' + identifier.replace("\"", "\"\"") + '"';
        }
    }
}
