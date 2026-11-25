package carelog.carelog.auth.web.internal;

import carelog.carelog.common.config.aop.LoggingAspect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-0019 §6/§11/§16 opt-in lifecycle과 AOP 격리를 실제 클래스({@code InternalIdentitySecurityConfiguration}
 * 등)로 조립해 고정하는 composition 증거.
 *
 * <p>이 sandbox 환경에서는 Docker Desktop CLI({@code docker version`)는 동작하지만, Gradle
 * 테스트 JVM에서 Testcontainers가 매번 "Previous attempts to find a Docker environment failed"로
 * 실패한다({@code CarelogApplicationTests} 등 기존 baseline Testcontainers 테스트도 동일하게
 * 실패함 — 이번 변경으로 생긴 회귀가 아니라 사전에 존재하던 환경 제약이다). 따라서 실제
 * {@code CarelogApplication} + Postgres/Flyway 전체 부팅 대신, {@link WebApplicationContextRunner}로
 * 이 경계에 필요한 실제 Bean 그래프(Configuration/Verifier/Filter/Chain, 그리고 실제
 * {@link LoggingAspect})만 조립한다. DB가 전혀 필요 없고, secret 검증 예외가 무관한 다른 실패로
 * 가려지지 않는다는 장점도 있다.
 */
class InternalIdentityClaimsLifecycleContextTest {

    private static final String VALID_SERVICE_SECRET =
            "full-context-service-secret-0123456789-abcdef";
    private static final String GATEWAY_SECRET = "test-gateway-internal-secret-0123456789";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    DispatcherServletAutoConfiguration.class,
                    WebMvcAutoConfiguration.class))
            .withUserConfiguration(LoggingAspectTestConfig.class, InternalIdentitySecurityConfiguration.class)
            .withPropertyValues("gateway.internal-secret=" + GATEWAY_SECRET);

    @Test
    @DisplayName("enabled=false(기본값) + secret 없음 → context가 정상 기동하고 internal 경계가 완전히 부재한다")
    void disabled_contextBootsWithNoInternalBoundary() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(InternalServiceCredentialVerifier.class);
            assertThat(context).doesNotHaveBean(InternalServiceAuthenticationFilter.class);
            assertThat(context).doesNotHaveBean(InternalServiceForbiddenHandler.class);
            // 이 minimal runner에는 우리 chain 외의 Product SecurityConfig가 없으므로, disabled일 때
            // Spring Boot의 fallback `defaultSecurityFilterChain`만 남는다 — 우리 internal chain
            // (`internalIdentitySecurityFilterChain`)이 부재함을 이름으로 명시적으로 확인한다.
            assertThat(context).doesNotHaveBean("internalIdentitySecurityFilterChain");
        });
    }

    @Test
    @DisplayName("enabled=true + 유효한 secret → context가 정상 기동하고 정확한 경계가 조립된다")
    void enabled_validSecret_contextBootsWithExactBoundaryComposed() {
        enabledRunner(VALID_SERVICE_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InternalServiceCredentialVerifier.class);
            assertThat(context).hasSingleBean(InternalServiceAuthenticationFilter.class);
            assertThat(context).hasSingleBean(InternalServiceForbiddenHandler.class);
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
        });
    }

    @Test
    @DisplayName("InternalServiceAuthenticationFilter는 독립 servlet filter로 등록되지 않는다")
    void enabled_authenticationFilterRegistrationIsDisabled() {
        enabledRunner(VALID_SERVICE_SECRET).run(context -> {
            @SuppressWarnings("unchecked")
            FilterRegistrationBean<InternalServiceAuthenticationFilter> registration =
                    context.getBean(
                            "internalServiceAuthenticationFilterRegistration",
                            FilterRegistrationBean.class);
            assertThat(registration.isEnabled()).isFalse();
        });
    }

    @Test
    @DisplayName("LoggingAspect가 활성 상태에서도 internal 경계 컴포넌트 때문에 context 기동이 실패하지 않는다")
    void enabled_loggingAspectActive_noCgLibFailureForInternalBoundary() {
        enabledRunner(VALID_SERVICE_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LoggingAspect.class);
            // context가 여기까지 조립됐다는 것 자체가 RPL-55 B3 CGLIB 결함(및 이번 확장 대상인
            // 내부 service-auth 경계 컴포넌트에 대한 동일 결함)이 재발하지 않았다는 증거다.
            assertThat(context.getBean(InternalServiceCredentialVerifier.class)).isNotNull();
        });
    }

    // 일반(비-internal) 컴포넌트가 여전히 advice 대상인지는
    // LoggingAspectConfigurationPropertiesIsolationTest.ordinaryComponentIsStillAdvised()가
    // 실제 LoggingAspect로 이미 고정한다 — 이 클래스에서 같은 대역을 다시 두면 그 대역 자체가
    // carelog.carelog.auth.web.internal 패키지(중첩 클래스도 동일 패키지로 취급됨)에 놓여
    // 방금 추가한 제외 대상에 함께 걸려버리므로 의도적으로 중복시키지 않는다.

    @Test
    @DisplayName("secret 누락 시 startup이 실패한다")
    void enabled_missingServiceSecret_failsStartup() {
        contextRunner
                .withPropertyValues("carelog.internal.identity-claims.enabled=true")
                .run(this::assertFailedDueToCredentialValidation);
    }

    @Test
    @DisplayName("secret이 blank이면 startup이 실패한다")
    void enabled_blankServiceSecret_failsStartup() {
        enabledRunner("   ").run(this::assertFailedDueToCredentialValidation);
    }

    @Test
    @DisplayName("secret이 32자 미만이면 startup이 실패한다")
    void enabled_shortServiceSecret_failsStartup() {
        enabledRunner("too-short-secret").run(this::assertFailedDueToCredentialValidation);
    }

    @Test
    @DisplayName("secret이 gateway.internal-secret과 같으면 startup이 실패한다")
    void enabled_serviceSecretEqualToGatewaySecret_failsStartup() {
        contextRunner
                .withPropertyValues(
                        "carelog.internal.identity-claims.enabled=true",
                        "carelog.internal.identity-claims.service-secret=" + GATEWAY_SECRET)
                .run(this::assertFailedDueToEqualCredentialValidation);
    }

    private void assertFailedDueToCredentialValidation(AssertableWebApplicationContext context) {
        assertThat(context).hasFailed();
        assertThatThrownBy(() -> context.getBean(InternalServiceCredentialVerifier.class))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    private void assertFailedDueToEqualCredentialValidation(AssertableWebApplicationContext context) {
        assertThat(context).hasFailed();
        assertThatThrownBy(() -> context.getBean(InternalServiceCredentialVerifier.class))
                .hasRootCauseMessage(
                        "carelog.internal.identity-claims.service-secret must differ from gateway.internal-secret");
    }

    private WebApplicationContextRunner enabledRunner(String serviceSecret) {
        return contextRunner.withPropertyValues(
                "carelog.internal.identity-claims.enabled=true",
                "carelog.internal.identity-claims.service-secret=" + serviceSecret);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class LoggingAspectTestConfig {

        @Bean
        LoggingAspect loggingAspect() {
            return new LoggingAspect();
        }
    }
}
