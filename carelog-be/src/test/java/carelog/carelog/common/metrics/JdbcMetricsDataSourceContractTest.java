package carelog.carelog.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class JdbcMetricsDataSourceContractTest {

    @Test
    void resultSetReturnsInstrumentedStatementAndFollowUpSqlIsRecorded() throws Exception {
        DataSource rawDataSource = mock(DataSource.class);
        Connection rawConnection = mock(Connection.class);
        Statement rawStatement = mock(Statement.class);
        ResultSet rawResultSet = mock(ResultSet.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(rawDataSource.getConnection()).thenReturn(rawConnection);
        when(rawConnection.createStatement()).thenReturn(rawStatement);
        when(rawStatement.executeQuery("SELECT 1")).thenReturn(rawResultSet);
        when(rawStatement.executeQuery("SELECT 2")).thenReturn(rawResultSet);
        when(rawResultSet.getStatement()).thenReturn(rawStatement);

        try (JdbcMetricsDataSource dataSource = new JdbcMetricsDataSource(rawDataSource, () -> registry);
             Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertThat(resultSet.getStatement()).isSameAs(statement);

                long beforeFollowUp = selectCount(registry);
                try (ResultSet followUp = resultSet.getStatement().executeQuery("SELECT 2")) {
                    assertThat(followUp.getStatement()).isSameAs(statement);
                }
                assertThat(selectCount(registry)).isEqualTo(beforeFollowUp + 1);
            }
        }
    }

    @Test
    void executeGetResultSetAndGeneratedKeysAllPreserveStatementRelationship() throws Exception {
        DataSource rawDataSource = mock(DataSource.class);
        Connection rawConnection = mock(Connection.class);
        Statement rawStatement = mock(Statement.class);
        PreparedStatement rawPreparedStatement = mock(PreparedStatement.class);
        ResultSet rawCurrentResultSet = mock(ResultSet.class);
        ResultSet rawGeneratedKeys = mock(ResultSet.class);
        ResultSet rawPreparedResultSet = mock(ResultSet.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        when(rawDataSource.getConnection()).thenReturn(rawConnection);
        when(rawConnection.createStatement()).thenReturn(rawStatement);
        when(rawConnection.prepareStatement("SELECT ?")).thenReturn(rawPreparedStatement);
        when(rawStatement.execute("SELECT 1")).thenReturn(true);
        when(rawStatement.getResultSet()).thenReturn(rawCurrentResultSet);
        when(rawStatement.getGeneratedKeys()).thenReturn(rawGeneratedKeys);
        when(rawPreparedStatement.executeQuery()).thenReturn(rawPreparedResultSet);
        when(rawCurrentResultSet.getStatement()).thenReturn(rawStatement);
        when(rawGeneratedKeys.getStatement()).thenReturn(rawStatement);
        when(rawPreparedResultSet.getStatement()).thenReturn(rawPreparedStatement);

        try (JdbcMetricsDataSource dataSource = new JdbcMetricsDataSource(rawDataSource, () -> registry);
             Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            assertThat(statement.execute("SELECT 1")).isTrue();
            try (ResultSet current = statement.getResultSet()) {
                assertThat(current.getStatement()).isSameAs(statement);
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.getStatement()).isSameAs(statement);
            }

            PreparedStatement preparedStatement = connection.prepareStatement("SELECT ?");
            try (ResultSet prepared = preparedStatement.executeQuery()) {
                assertThat(prepared.getStatement()).isSameAs(preparedStatement);
            }
        }
    }

    @Test
    void connectionStatementAndResultSetPreserveObjectAndWrapperContracts() throws Exception {
        DataSource rawDataSource = mock(DataSource.class);
        Connection rawConnection = mock(Connection.class, withSettings().extraInterfaces(VendorConnection.class));
        Statement rawStatement = mock(Statement.class, withSettings().extraInterfaces(VendorStatement.class));
        PreparedStatement rawPreparedStatement = mock(
                PreparedStatement.class, withSettings().extraInterfaces(VendorPreparedStatement.class));
        ResultSet rawResultSet = mock(ResultSet.class, withSettings().extraInterfaces(VendorResultSet.class));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        when(rawDataSource.getConnection()).thenReturn(rawConnection);
        when(rawConnection.createStatement()).thenReturn(rawStatement);
        when(rawConnection.prepareStatement("SELECT ?")).thenReturn(rawPreparedStatement);
        when(rawStatement.executeQuery("SELECT 1")).thenReturn(rawResultSet);
        when(rawResultSet.getStatement()).thenReturn(rawStatement);
        when(rawConnection.isWrapperFor(VendorConnection.class)).thenReturn(true);
        when(rawConnection.unwrap(VendorConnection.class)).thenReturn((VendorConnection) rawConnection);
        when(rawStatement.isWrapperFor(VendorStatement.class)).thenReturn(true);
        when(rawStatement.unwrap(VendorStatement.class)).thenReturn((VendorStatement) rawStatement);
        when(rawResultSet.isWrapperFor(VendorResultSet.class)).thenReturn(true);
        when(rawResultSet.unwrap(VendorResultSet.class)).thenReturn((VendorResultSet) rawResultSet);

        try (JdbcMetricsDataSource dataSource = new JdbcMetricsDataSource(rawDataSource, () -> registry);
             Connection connection = dataSource.getConnection()) {
            assertThat(connection).isEqualTo(connection);
            assertThat(connection.hashCode()).isEqualTo(System.identityHashCode(connection));
            assertThat(connection.toString()).isNotEqualTo(rawConnection.toString());
            assertThat(connection.isWrapperFor(Connection.class)).isTrue();
            assertThat(connection.unwrap(Connection.class)).isSameAs(connection);
            assertThat(connection.isWrapperFor(VendorConnection.class)).isTrue();
            assertThat(connection.unwrap(VendorConnection.class)).isSameAs(rawConnection);

            Statement statement = connection.createStatement();
            assertThat(statement).isEqualTo(statement);
            assertThat(statement.hashCode()).isEqualTo(System.identityHashCode(statement));
            assertThat(statement.getConnection()).isSameAs(connection);
            assertThat(statement.isWrapperFor(Statement.class)).isTrue();
            assertThat(statement.unwrap(Statement.class)).isSameAs(statement);
            assertThat(statement.isWrapperFor(VendorStatement.class)).isTrue();
            assertThat(statement.unwrap(VendorStatement.class)).isSameAs(rawStatement);

            try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertThat(resultSet).isEqualTo(resultSet);
                assertThat(resultSet.hashCode()).isEqualTo(System.identityHashCode(resultSet));
                assertThat(resultSet.getStatement()).isSameAs(statement);
                assertThat(resultSet.isWrapperFor(ResultSet.class)).isTrue();
                assertThat(resultSet.unwrap(ResultSet.class)).isSameAs(resultSet);
                assertThat(resultSet.isWrapperFor(VendorResultSet.class)).isTrue();
                assertThat(resultSet.unwrap(VendorResultSet.class)).isSameAs(rawResultSet);
            }

            PreparedStatement preparedStatement = connection.prepareStatement("SELECT ?");
            assertThat(preparedStatement.isWrapperFor(PreparedStatement.class)).isTrue();
            assertThat(preparedStatement.unwrap(PreparedStatement.class)).isSameAs(preparedStatement);
            preparedStatement.close();

            statement.close();
        }
        verify(rawResultSet).close();
        verify(rawStatement).close();
        verify(rawConnection).close();
    }

    @Test
    void originalSQLExceptionAndSqlStateArePreserved() throws Exception {
        DataSource rawDataSource = mock(DataSource.class);
        Connection rawConnection = mock(Connection.class);
        Statement rawStatement = mock(Statement.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SQLException original = new SQLException("query cancelled", "57014", 0);
        when(rawDataSource.getConnection()).thenReturn(rawConnection);
        when(rawConnection.createStatement()).thenReturn(rawStatement);
        when(rawStatement.executeQuery("SELECT pg_sleep(1)")).thenThrow(original);

        try (JdbcMetricsDataSource dataSource = new JdbcMetricsDataSource(rawDataSource, () -> registry);
             Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            assertThatThrownBy(() -> statement.executeQuery("SELECT pg_sleep(1)"))
                    .isSameAs(original)
                    .isInstanceOf(SQLException.class);
            assertThat(original.getSQLState()).isEqualTo("57014");
        }
    }

    private static long selectCount(SimpleMeterRegistry registry) {
        var timer = registry.find(JdbcMetricsDataSource.TIMER_NAME)
                .tag("operation", "select").tag("outcome", "success").timer();
        return timer == null ? 0 : timer.count();
    }

    public interface VendorConnection extends Connection {
    }

    public interface VendorStatement extends Statement {
    }

    public interface VendorPreparedStatement extends PreparedStatement {
    }

    public interface VendorResultSet extends ResultSet {
    }
}
