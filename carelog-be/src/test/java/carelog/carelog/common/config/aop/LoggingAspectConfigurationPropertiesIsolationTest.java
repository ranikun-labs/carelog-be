package carelog.carelog.common.config.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import carelog.carelog.auth.web.internal.IdentityClaimsInternalApiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Reproduces the real B3 runtime failure: enabling the B1 Identity claims feature
 * ({@code carelog.internal.identity-claims.enabled=true}) loads {@link IdentityClaimsInternalApiProperties},
 * a final {@code record} {@code @ConfigurationProperties} bean, into a context where
 * {@link LoggingAspect}'s {@code logException} advice is active.
 *
 * <p>Before the fix, Spring's auto-proxy creator tried to CGLIB-subclass the record because the
 * broad {@code logException} pointcut matched it, and startup failed with
 * "Could not generate CGLIB subclass ... using a final class". This test loads the real AspectJ
 * auto-proxy infrastructure and the real aspect (not a mock) to prove that collision is gone,
 * while the same advice remains active for ordinary Carelog components.
 */
@SpringJUnitConfig(LoggingAspectConfigurationPropertiesIsolationTest.TestConfig.class)
@TestPropertySource(properties = {
        "carelog.internal.identity-claims.enabled=true",
        "carelog.internal.identity-claims.service-secret=test-only-service-secret-0123456789"
})
class LoggingAspectConfigurationPropertiesIsolationTest {

    @Autowired
    private IdentityClaimsInternalApiProperties identityClaimsInternalApiProperties;

    @Autowired
    private ThrowingComponent throwingComponent;

    @Test
    @DisplayName("logException이 활성화된 context에서 @ConfigurationProperties record가 그대로(비-proxy) 로드된다")
    void configurationPropertiesRecordIsNotProxied() {
        // A CGLIB proxy subclass would report a different, non-final runtime class.
        assertThat(identityClaimsInternalApiProperties.getClass())
                .isEqualTo(IdentityClaimsInternalApiProperties.class);
        assertThat(AopUtils.isAopProxy(identityClaimsInternalApiProperties)).isFalse();
    }

    @Test
    @DisplayName("@ConfigurationProperties record는 여전히 configuration data holder로 정상 사용 가능하다")
    void configurationPropertiesRecordRemainsUsable() {
        assertThat(identityClaimsInternalApiProperties.enabled()).isTrue();
        assertThat(identityClaimsInternalApiProperties.serviceSecret())
                .isEqualTo("test-only-service-secret-0123456789");
    }

    @Test
    @DisplayName("일반 Carelog 컴포넌트는 여전히 logException advice의 대상이다")
    void ordinaryComponentIsStillAdvised() {
        assertThat(AopUtils.isAopProxy(throwingComponent)).isTrue();
        // The advice re-throws after logging: proves the aspect actually intercepted the call
        // rather than the exception logging having been disabled globally.
        assertThatThrownBy(throwingComponent::explode).isInstanceOf(IllegalStateException.class);
    }

    /** A minimal, non-Filter, non-@ConfigurationProperties bean under carelog.carelog for contrast. */
    static class ThrowingComponent {
        void explode() {
            throw new IllegalStateException("boom");
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableConfigurationProperties(IdentityClaimsInternalApiProperties.class)
    static class TestConfig {

        @Bean
        LoggingAspect loggingAspect() {
            return new LoggingAspect();
        }

        @Bean
        ThrowingComponent throwingComponent() {
            return new ThrowingComponent();
        }
    }
}
