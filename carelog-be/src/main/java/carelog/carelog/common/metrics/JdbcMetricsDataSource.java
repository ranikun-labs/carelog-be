package carelog.carelog.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** SQL 원문을 보관하거나 내보내지 않고 JDBC 실행 시간을 기록한다. */
final class JdbcMetricsDataSource extends DelegatingDataSource
        implements AutoCloseable, AopInfrastructureBean {

    static final String TIMER_NAME = "ranikun.db.query.duration";
    private static final Duration[] HISTOGRAM_BOUNDARIES = {
            Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(10),
            Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100),
            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1)
    };

    private final Supplier<MeterRegistry> meterRegistrySupplier;
    private final ConcurrentMap<TimerKey, Timer> timers = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    JdbcMetricsDataSource(DataSource targetDataSource, Supplier<MeterRegistry> meterRegistrySupplier) {
        super(targetDataSource);
        this.meterRegistrySupplier = Objects.requireNonNull(meterRegistrySupplier);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return instrument(getTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return instrument(getTargetDataSource().getConnection(username, password));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        DataSource target = getTargetDataSource();
        if (target instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new IllegalStateException("JDBC 데이터 소스를 닫지 못했습니다", exception);
            }
        }
    }

    private Connection instrument(Connection connection) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (isObjectMethod(method)) return objectMethod(proxy, method, arguments, "JdbcMetricsConnectionProxy");
            if (isWrapperMethod(method)) return wrapperMethod(proxy, connection, method, arguments);

            Object result = invoke(connection, method, arguments);
            String preparedOperation = "other";
            if ("prepareCall".equals(method.getName())) {
                preparedOperation = "call";
            } else if ("prepareStatement".equals(method.getName())
                    && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
                preparedOperation = classifyOperation(sql);
            }
            if (result instanceof CallableStatement statement) {
                return instrumentStatement(statement, (Connection) proxy, preparedOperation);
            }
            if (result instanceof PreparedStatement statement) {
                return instrumentStatement(statement, (Connection) proxy, preparedOperation);
            }
            if (result instanceof Statement statement) {
                return instrumentStatement(statement, (Connection) proxy, preparedOperation);
            }
            return result;
        };
        return proxy(Connection.class, connection, handler);
    }

    private Statement instrumentStatement(Statement statement, Connection connection, String preparedOperation) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            String methodName = method.getName();
            if (isObjectMethod(method)) return objectMethod(proxy, method, arguments, "JdbcMetricsStatementProxy");
            if (isWrapperMethod(method)) return wrapperMethod(proxy, statement, method, arguments);
            if ("getConnection".equals(methodName)) return connection;

            String operation = operationFor(methodName, arguments, preparedOperation);
            if (!isExecutionMethod(methodName)) {
                return wrapResultSet(invoke(statement, method, arguments), (Statement) proxy);
            }

            long startedAt = System.nanoTime();
            Throwable failure = null;
            try {
                return wrapResultSet(invoke(statement, method, arguments), (Statement) proxy);
            } catch (Throwable throwable) {
                failure = throwable;
                throw throwable;
            } finally {
                record(operation, failure, System.nanoTime() - startedAt);
            }
        };

        Class<?> statementType = statement instanceof CallableStatement
                ? CallableStatement.class
                : statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return (Statement) proxy(statementType, statement, handler);
    }

    private static Object wrapResultSet(Object result, Statement statement) {
        if (!(result instanceof ResultSet resultSet)) return result;

        InvocationHandler handler = (proxy, method, arguments) -> {
            if (isObjectMethod(method)) return objectMethod(proxy, method, arguments, "JdbcMetricsResultSetProxy");
            if (isWrapperMethod(method)) return wrapperMethod(proxy, resultSet, method, arguments);
            if ("getStatement".equals(method.getName()) && method.getParameterCount() == 0) {
                Object relatedStatement = invoke(resultSet, method, arguments);
                return relatedStatement == null ? null : statement;
            }
            return invoke(resultSet, method, arguments);
        };
        return proxy(ResultSet.class, resultSet, handler);
    }

    private static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass() == Object.class
                || (method.getParameterCount() == 1 && "equals".equals(method.getName())
                && method.getParameterTypes()[0] == Object.class)
                || (method.getParameterCount() == 0
                && ("hashCode".equals(method.getName()) || "toString".equals(method.getName())));
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments, String proxyName) {
        return switch (method.getName()) {
            case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxyName + "@" + Integer.toHexString(System.identityHashCode(proxy));
            default -> throw new IllegalArgumentException("Unsupported Object method: " + method.getName());
        };
    }

    private static boolean isWrapperMethod(Method method) {
        return method.getDeclaringClass() == java.sql.Wrapper.class
                || "unwrap".equals(method.getName()) || "isWrapperFor".equals(method.getName());
    }

    private static Object wrapperMethod(Object proxy, Object delegate, Method method, Object[] arguments)
            throws Throwable {
        Class<?> requestedType = arguments != null && arguments.length == 1
                ? (arguments[0] instanceof Class<?> type ? type : null)
                : null;
        if ("unwrap".equals(method.getName()) && requestedType != null && requestedType.isInstance(proxy)) {
            return proxy;
        }
        if ("isWrapperFor".equals(method.getName()) && requestedType != null
                && requestedType.isInstance(proxy)) {
            return true;
        }
        return invoke(delegate, method, arguments);
    }

    private void record(String operation, Throwable failure, long elapsedNanos) {
        try {
            MeterRegistry meterRegistry = meterRegistrySupplier.get();
            if (meterRegistry == null) return;
            String outcome = failure == null ? "success"
                    : HttpTimeoutMetrics.classifyTimeout(failure) == null ? "error" : "timeout";
            TimerKey key = new TimerKey(operation, outcome);
            Timer timer = timers.computeIfAbsent(key, ignored -> Timer.builder(TIMER_NAME)
                    .description("Time spent executing JDBC statements; SQL and parameter values are never recorded")
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .serviceLevelObjectives(HISTOGRAM_BOUNDARIES)
                    .register(meterRegistry));
            timer.record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // 계측 오류가 데이터베이스 연산 결과를 바꾸지 않도록 한다.
        }
    }

    private static boolean isExecutionMethod(String methodName) {
        return switch (methodName) {
            case "execute", "executeQuery", "executeUpdate", "executeLargeUpdate",
                    "executeBatch", "executeLargeBatch" -> true;
            default -> false;
        };
    }

    private static String operationFor(String methodName, Object[] arguments, String preparedOperation) {
        if (methodName.endsWith("Batch")) return "batch";
        if (!"other".equals(preparedOperation) && !"call".equals(preparedOperation)) {
            return preparedOperation;
        }
        if ("call".equals(preparedOperation)) return "call";
        if (arguments != null && arguments.length > 0 && arguments[0] instanceof String sql) {
            return classifyOperation(sql);
        }
        return preparedOperation;
    }

    static String classifyOperation(String sql) {
        if (sql == null || sql.isBlank()) return "other";
        String firstWord = firstSqlWord(sql);
        return switch (firstWord.toLowerCase(Locale.ROOT)) {
            case "select", "values", "show", "explain" -> "select";
            case "insert" -> "insert";
            case "update" -> "update";
            case "delete" -> "delete";
            case "call", "exec", "execute" -> "call";
            case "create", "alter", "drop", "truncate", "grant", "revoke", "comment" -> "ddl";
            default -> "other";
        };
    }

    private static String firstSqlWord(String sql) {
        int index = 0;
        while (index < sql.length()) {
            while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) index++;
            if (sql.startsWith("--", index)) {
                int endOfLine = sql.indexOf('\n', index + 2);
                index = endOfLine < 0 ? sql.length() : endOfLine + 1;
            } else if (sql.startsWith("/*", index)) {
                int endOfComment = sql.indexOf("*/", index + 2);
                index = endOfComment < 0 ? sql.length() : endOfComment + 2;
            } else {
                break;
            }
        }
        int end = index;
        while (end < sql.length() && (Character.isLetter(sql.charAt(end)) || sql.charAt(end) == '_')) end++;
        return end == index ? "" : sql.substring(index, end);
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static <T> T proxy(Class<T> contract, Object delegate, InvocationHandler handler) {
        return contract.cast(Proxy.newProxyInstance(
                contract.getClassLoader(), new Class<?>[]{contract}, handler));
    }

    private record TimerKey(String operation, String outcome) {
    }
}
