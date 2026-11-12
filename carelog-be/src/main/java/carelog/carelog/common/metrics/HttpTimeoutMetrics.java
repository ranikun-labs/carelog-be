package carelog.carelog.common.metrics;

import io.lettuce.core.RedisCommandTimeoutException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.hibernate.QueryTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/** Spring MVC가 전달한 실제 예외를 분류해 요청당 한 번만 제한된 Timeout 지표를 기록한다. */
@Component
public final class HttpTimeoutMetrics implements AopInfrastructureBean {

    private static final String RECORDED_ATTRIBUTE = HttpTimeoutMetrics.class.getName() + ".recorded";
    private final MeterRegistry meterRegistry;

    public HttpTimeoutMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void recordIfTimeout(HttpServletRequest request, Throwable throwable) {
        String kind = classifyTimeout(throwable);
        if (kind == null) return;

        synchronized (request) {
            if (Boolean.TRUE.equals(request.getAttribute(RECORDED_ATTRIBUTE))) return;
            request.setAttribute(RECORDED_ATTRIBUTE, Boolean.TRUE);
        }

        Counter.builder("carelog.http.timeouts")
                .description("HTTP requests whose resolved Throwable is a recognized timeout")
                .tag("kind", kind)
                .register(meterRegistry)
                .increment();
    }

    static String classifyTimeout(Throwable throwable) {
        if (throwable == null) return null;
        Map<Throwable, Boolean> seen = Collections.synchronizedMap(new IdentityHashMap<>());
        for (Throwable cause = throwable; cause != null && seen.put(cause, Boolean.TRUE) == null;
             cause = cause.getCause()) {
            if (cause instanceof AsyncRequestTimeoutException) return "servlet_async";
            if (cause instanceof SQLTimeoutException || cause instanceof QueryTimeoutException) return "jdbc";
            if (cause instanceof SQLException sqlException && "57014".equals(sqlException.getSQLState())) {
                return "jdbc";
            }
            if (cause instanceof RedisCommandTimeoutException) return "dependency";
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return "network";
            if (cause instanceof TimeoutException) return "deadline";
        }
        return null;
    }
}
