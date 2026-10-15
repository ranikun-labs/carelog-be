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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                SQLException divisionByZero = assertThrows(
                        SQLException.class, () -> statement.executeQuery("SELECT 1 / 0"));
                assertThat(divisionByZero.getSQLState()).isEqualTo("22012");
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET statement_timeout = '100ms'");
                SQLException timeout = assertThrows(
                        SQLException.class, () -> statement.executeQuery("SELECT pg_sleep(2)"));
                assertThat(timeout.getSQLState()).isEqualTo("57014");
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

    @Test
    void resultSetRelationshipsKeepStatementProxyAndRecordFollowUpSql() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(1);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (JdbcMetricsDataSource dataSource = new JdbcMetricsDataSource(
                new HikariDataSource(config), () -> registry);
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet first = statement.executeQuery("SELECT 1")) {
                assertThat(first.getStatement()).isSameAs(statement);
                long beforeFollowUp = selectSuccessCount(registry);
                try (ResultSet followUp = first.getStatement().executeQuery("SELECT 2")) {
                    assertThat(followUp.next()).isTrue();
                    assertThat(followUp.getInt(1)).isEqualTo(2);
                    assertThat(followUp.getStatement()).isSameAs(statement);
                }
                assertThat(selectSuccessCount(registry)).isEqualTo(beforeFollowUp + 1);
            }

            assertThat(statement.execute("SELECT 3")).isTrue();
            try (ResultSet current = statement.getResultSet()) {
                assertThat(current.getStatement()).isSameAs(statement);
                assertThat(current.unwrap(ResultSet.class)).isSameAs(current);
            }

            statement.execute("CREATE TEMP TABLE metric_probe_generated "
                    + "(id BIGSERIAL PRIMARY KEY, value integer)");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO metric_probe_generated(value) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, 7);
                assertThat(insert.executeUpdate()).isEqualTo(1);
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertThat(keys.next()).isTrue();
                    assertThat(keys.getStatement()).isSameAs(insert);
                }
            }
        }
    }

    @Test
    void duplicateCloseReturnsPooledConnectionAndCommitRollbackRemainEffective() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(1);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HikariDataSource hikari = new HikariDataSource(config);
        try (JdbcMetricsDataSource dataSource = new JdbcMetricsDataSource(hikari, () -> registry)) {
            Connection connection = dataSource.getConnection();
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TEMP TABLE metric_probe_transaction (value integer)");
                }

                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO metric_probe_transaction(value) VALUES (1)");
                }
                connection.rollback();

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO metric_probe_transaction(value) VALUES (2)");
                }
                connection.commit();
            } finally {
                connection.close();
                connection.close();
            }

            assertThat(hikari.getHikariPoolMXBean().getActiveConnections()).isZero();
            try (Connection reused = dataSource.getConnection();
                 Statement statement = reused.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT value FROM metric_probe_transaction")) {
                assertThat(rows.getStatement()).isSameAs(statement);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.next()).isFalse();
            }
            assertThat(hikari.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    private static long selectSuccessCount(SimpleMeterRegistry registry) {
        var timer = registry.find(JdbcMetricsDataSource.TIMER_NAME)
                .tag("operation", "select").tag("outcome", "success").timer();
        return timer == null ? 0 : timer.count();
    }
}
