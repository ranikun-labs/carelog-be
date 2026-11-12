package carelog.carelog.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Boot가 만든 DataSource를 감싸며 원래 Connection Pool은 그대로 위임해 사용한다. */
@Component
public final class JdbcMetricsDataSourcePostProcessor implements BeanPostProcessor, AopInfrastructureBean {

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public JdbcMetricsDataSourcePostProcessor(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!"dataSource".equals(beanName) || !(bean instanceof DataSource dataSource)
                || bean instanceof JdbcMetricsDataSource) {
            return bean;
        }
        return new JdbcMetricsDataSource(dataSource, meterRegistryProvider::getIfAvailable);
    }
}
