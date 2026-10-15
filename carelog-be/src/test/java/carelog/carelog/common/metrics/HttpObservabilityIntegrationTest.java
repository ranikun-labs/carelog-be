package carelog.carelog.common.metrics;

import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import carelog.carelog.common.web.exception.GlobalExceptionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = HttpObservabilityIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.servlet.context-path=/api/v1",
                "management.endpoints.web.exposure.include=health,prometheus",
                "management.metrics.distribution.percentiles-histogram.http.server.requests=true",
                "management.metrics.tags.service=carelog",
                "management.metrics.tags.environment=metrics-test"
        })
class HttpObservabilityIntegrationTest {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PrometheusMeterRegistry prometheusMeterRegistry;

    @LocalServerPort
    private int port;

    @Test
    void realHttpRequestsRecordStatusHistogramAndOneThrowableClassifiedTimeout() throws Exception {
        HttpResponse<String> success = request("/api/v1/probe/ok");
        assertThat(success.statusCode()).isEqualTo(200);

        HttpResponse<String> timeout = request("/api/v1/probe/timeout");
        assertThat(timeout.statusCode()).isEqualTo(500);
        assertThat(timeout.body()).contains("서버에 예상치 못한 오류가 발생했습니다.");

        HttpResponse<String> dependencyUnavailable = request("/api/v1/probe/dependency-unavailable");
        assertThat(dependencyUnavailable.statusCode()).isEqualTo(503);

        Timer successTimer = meterRegistry.find("http.server.requests")
                .tag("uri", "/probe/ok").tag("status", "200").timer();
        Timer errorTimer = meterRegistry.find("http.server.requests")
                .tag("uri", "/probe/timeout").tag("status", "500").timer();
        assertThat(successTimer).isNotNull();
        assertThat(errorTimer).isNotNull();
        assertThat(successTimer.count()).isEqualTo(1);
        assertThat(errorTimer.count()).isEqualTo(1);
        assertThat(successTimer.getId().getTag("service")).isEqualTo("carelog");
        assertThat(successTimer.getId().getTag("environment")).isEqualTo("metrics-test");

        var timeoutCounter = meterRegistry.find("carelog.http.timeouts").tag("kind", "deadline").counter();
        assertThat(timeoutCounter).isNotNull();
        assertThat(timeoutCounter.count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("carelog.http.timeouts").tag("kind", "dependency").counter()).isNull();
        assertThat(timeoutCounter.getId().getTags()).extracting("value")
                .doesNotContain("test-only timeout detail");

        String scrape = prometheusMeterRegistry.scrape();
        assertThat(scrape).contains("http_server_requests_seconds_bucket")
                .contains("http_server_requests_seconds_count")
                .contains("status=\"500\"")
                .contains("carelog_http_timeouts_total")
                .contains("kind=\"deadline\"")
                .contains("status=\"503\"")
                .doesNotContain("kind=\"dependency\"")
                .doesNotContain("test-only timeout detail");
    }

    private HttpResponse<String> request(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class
    })
    @Import({GlobalExceptionHandler.class, HttpTimeoutMetrics.class,
            HttpTimeoutMetricsConfiguration.class, ProbeController.class, PrometheusRegistryTestConfiguration.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PrometheusRegistryTestConfiguration {
        @Bean
        @Primary
        PrometheusMeterRegistry prometheusMeterRegistry() {
            return new PrometheusMeterRegistry(io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/probe/timeout")
        String timeout() throws TimeoutException {
            throw new TimeoutException("test-only timeout detail");
        }

        @GetMapping("/probe/dependency-unavailable")
        String dependencyUnavailable() {
            throw new OAuthStateStoreUnavailableException(
                    "OAuth state store is unavailable",
                    new RedisConnectionFailureException("Valkey connection refused"));
        }
    }
}
