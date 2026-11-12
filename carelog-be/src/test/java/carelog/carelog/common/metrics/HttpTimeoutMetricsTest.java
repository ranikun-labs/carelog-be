package carelog.carelog.common.metrics;

import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTimeoutMetricsTest {

    @Test
    void connectionRefusalDoesNotCountAsTimeoutButLettuceCommandTimeoutDoes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpTimeoutMetrics metrics = new HttpTimeoutMetrics(registry);
        MockHttpServletRequest request = new MockHttpServletRequest();

        metrics.recordIfTimeout(request, new OAuthStateStoreUnavailableException(
                "OAuth state store is unavailable",
                new RedisConnectionFailureException("Valkey connection refused")));
        var dependencyCounter = registry.find("carelog.http.timeouts").tag("kind", "dependency").counter();
        assertThat(dependencyCounter).isNull();

        metrics.recordIfTimeout(request, new RedisCommandTimeoutException("command timed out"));
        assertThat(registry.find("carelog.http.timeouts").tag("kind", "dependency").counter().count())
                .isEqualTo(1.0);
    }
}
