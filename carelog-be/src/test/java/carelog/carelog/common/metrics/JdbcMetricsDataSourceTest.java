package carelog.carelog.common.metrics;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcMetricsDataSourceTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) postgres.stop();
    }

    @Test
    void statementExecutionRecordsBoundedHistogramWithoutSqlOrParameterTags() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(2);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (JdbcMetricsDataSource dataSource = new JdbcMetricsDataSource(
                new HikariDataSource(config), () -> registry);
             Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE metric_probe (value integer)");
                statement.executeUpdate("INSERT INTO metric_probe(value) VALUES (7)");
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT value FROM metric_probe WHERE value = ?")) {
                statement.setInt(1, 7);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(7);
                }
            }

            try (Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.executeQuery("SELECT 1 / 0"))
                        .isInstanceOf(SQLException.class);
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET statement_timeout = '100ms'");
                assertThatThrownBy(() -> statement.executeQuery("SELECT pg_sleep(2)"))
                        .isInstanceOf(SQLException.class);
            }

            var selectSuccess = registry.get(JdbcMetricsDataSource.TIMER_NAME)
                    .tag("operation", "select").tag("outcome", "success").timer();
            var selectError = registry.get(JdbcMetricsDataSource.TIMER_NAME)
                    .tag("operation", "select").tag("outcome", "error").timer();
            var selectTimeout = registry.get(JdbcMetricsDataSource.TIMER_NAME)
                    .tag("operation", "select").tag("outcome", "timeout").timer();
            var insert = registry.get(JdbcMetricsDataSource.TIMER_NAME)
                    .tag("operation", "insert").tag("outcome", "success").timer();
            var ddl = registry.get(JdbcMetricsDataSource.TIMER_NAME)
                    .tag("operation", "ddl").tag("outcome", "success").timer();

            assertThat(selectSuccess.count()).isEqualTo(1);
            assertThat(selectError.count()).isEqualTo(1);
            assertThat(selectTimeout.count()).isEqualTo(1);
            assertThat(insert.count()).isEqualTo(1);
            assertThat(ddl.count()).isEqualTo(1);
            assertThat(selectSuccess.takeSnapshot().histogramCounts()).isNotEmpty();
            assertThat(selectSuccess.getId().getTag("sql")).isNull();
            assertThat(selectSuccess.getId().getTag("parameter")).isNull();
            assertThat(selectSuccess.getId().getTags()).extracting("key")
                    .containsExactlyInAnyOrder("operation", "outcome");
        }
    }
}
