package carelog.carelog.common.metrics;

import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    void registryFailureDoesNotChangeExistingResolverResponseOrReplaceOriginalException() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                throw new IllegalStateException("registry unavailable");
            }
        });
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("httpTimeoutMetrics", new HttpTimeoutMetrics(registry));

        List<HandlerExceptionResolver> resolvers = new ArrayList<>();
        new HttpTimeoutMetricsConfiguration(beans.getBeanProvider(HttpTimeoutMetrics.class))
                .extendHandlerExceptionResolvers(resolvers);
        RuntimeException original = new RedisCommandTimeoutException("command timed out");
        HandlerExceptionResolver existing = (request, response, handler, exception) -> {
            assertThat(exception).isSameAs(original);
            try {
                response.setStatus(503);
                response.getWriter().write("existing response");
            } catch (Exception writeFailure) {
                throw new IllegalStateException(writeFailure);
            }
            return new ModelAndView();
        };
        resolvers.add(existing);

        MockHttpServletResponse actual = resolve(resolvers, original);
        MockHttpServletResponse baseline = resolve(List.of(existing), original);

        assertThat(actual.getStatus()).isEqualTo(baseline.getStatus()).isEqualTo(503);
        assertThat(actual.getContentAsString()).isEqualTo(baseline.getContentAsString())
                .isEqualTo("existing response");
    }

    private static MockHttpServletResponse resolve(List<HandlerExceptionResolver> resolvers,
                                                  Exception original) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        for (HandlerExceptionResolver resolver : resolvers) {
            ModelAndView result = resolver.resolveException(request, response, null, original);
            if (result != null) break;
        }
        return response;
    }
}
