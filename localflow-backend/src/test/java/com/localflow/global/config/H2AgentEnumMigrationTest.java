package com.localflow.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class H2AgentEnumMigrationTest {
    @Test
    void convertsLegacyEnumsSoNewCancellationStatesCanBeStored() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:enum-migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE agent_runs (status ENUM('DRAFT','RUNNING') NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE agent_run_progress_events "
                + "(stage ENUM('PREPARING','COMPLETED') NOT NULL)");

        new H2AgentEnumMigration(dataSource, jdbcTemplate).run(null);

        assertThatCode(() -> {
            jdbcTemplate.update("INSERT INTO agent_runs(status) VALUES ('CANCEL_REQUESTED')");
            jdbcTemplate.update("INSERT INTO agent_run_progress_events(stage) "
                    + "VALUES ('CANCELLATION_REQUESTED')");
        }).doesNotThrowAnyException();
    }
}
