package carelog.carelog.common.metrics;

import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
        AtomicBoolean filterInvoked = new AtomicBoolean();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                if ("carelog.http.timeouts".equals(id.getName())) {
                    filterInvoked.set(true);
                    throw new IllegalStateException("registry unavailable");
                }
                return MeterFilterReply.NEUTRAL;
            }
        });
        SimpleMeterRegistry baselineRegistry = new SimpleMeterRegistry();
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

        MockHttpServletResponse actual = resolve(withMetrics(registry, existing), original);
        MockHttpServletResponse baseline = resolve(withMetrics(baselineRegistry, existing), original);

        assertThat(filterInvoked).isTrue();
        assertThat(actual.getStatus()).isEqualTo(baseline.getStatus()).isEqualTo(503);
        assertThat(actual.getContentAsString()).isEqualTo(baseline.getContentAsString())
                .isEqualTo("existing response");
    }

    private static List<HandlerExceptionResolver> withMetrics(SimpleMeterRegistry registry,
                                                              HandlerExceptionResolver existing) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("httpTimeoutMetrics", new HttpTimeoutMetrics(registry));
        List<HandlerExceptionResolver> resolvers = new ArrayList<>();
        new HttpTimeoutMetricsConfiguration(beans.getBeanProvider(HttpTimeoutMetrics.class))
                .extendHandlerExceptionResolvers(resolvers);
        resolvers.add(existing);
        return resolvers;
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
