package carelog.carelog.common.metrics;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/** 기존 예외 Resolver의 응답 처리를 바꾸지 않고 실제 Throwable을 먼저 관측한다. */
@Configuration(proxyBeanMethods = false)
public class HttpTimeoutMetricsConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<HttpTimeoutMetrics> timeoutMetricsProvider;

    public HttpTimeoutMetricsConfiguration(ObjectProvider<HttpTimeoutMetrics> timeoutMetricsProvider) {
        this.timeoutMetricsProvider = timeoutMetricsProvider;
    }

    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add(0, new HandlerExceptionResolver() {
            @Override
            public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                                 Object handler, Exception exception) {
                timeoutMetricsProvider.ifAvailable(metrics -> metrics.recordIfTimeout(request, exception));
                return null;
            }
        });
    }
}
