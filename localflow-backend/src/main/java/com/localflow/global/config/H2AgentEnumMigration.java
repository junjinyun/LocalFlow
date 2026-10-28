package com.localflow.global.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate's H2 enum definition is not expanded when a Java enum gains a value.
 * Existing local databases therefore keep rejecting newer run/progress states.
 */
@Component
public class H2AgentEnumMigration implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public H2AgentEnumMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        }
        convertEnumColumn("AGENT_RUNS", "STATUS", 30);
        convertEnumColumn("AGENT_RUN_PROGRESS_EVENTS", "STAGE", 40);
    }

    private void convertEnumColumn(String table, String column, int length) {
        Integer enumColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_SCHEMA = 'PUBLIC'
                   AND TABLE_NAME = ?
                   AND COLUMN_NAME = ?
                   AND DATA_TYPE = 'ENUM'
                """, Integer.class, table, column);
        if (enumColumnCount != null && enumColumnCount > 0) {
            jdbcTemplate.execute("ALTER TABLE " + table
                    + " ALTER COLUMN " + column + " VARCHAR(" + length + ") NOT NULL");
        }
    }
}
