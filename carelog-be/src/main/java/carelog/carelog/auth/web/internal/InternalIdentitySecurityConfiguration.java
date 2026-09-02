package carelog.carelog.auth.web.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ADR-0019 Slice A′ 단일 {@code platform-identity} projection read를 위한 opt-in composition.
 *
 * <p>{@code carelog.internal.identity-claims.enabled=false}(기본값)이면 이 Configuration 전체가
 * 등록되지 않는다 — controller, filter, verifier, 전용 {@code SecurityFilterChain} 모두 부재하며,
 * {@code /internal/**}은 {@link carelog.carelog.common.config.SecurityConfig}의 일반 chain이
 * 그대로 차단한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "carelog.internal.identity-claims",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(IdentityClaimsInternalApiProperties.class)
public class InternalIdentitySecurityConfiguration {

    @Bean
    InternalServiceCredentialVerifier internalServiceCredentialVerifier(
            IdentityClaimsInternalApiProperties properties,
            @Value("${gateway.internal-secret:}") String gatewayInternalSecret
    ) {
        return new InternalServiceCredentialVerifier(properties.serviceSecret(), gatewayInternalSecret);
    }

    @Bean
    InternalServiceAuthenticationFilter internalServiceAuthenticationFilter(
            InternalServiceCredentialVerifier credentialVerifier
    ) {
        return new InternalServiceAuthenticationFilter(credentialVerifier);
    }

    /**
     * {@link InternalServiceAuthenticationFilter}는 {@code SecurityFilterChain}
     * {@code addFilterBefore}를 통해서만 실행된다 — 독립적인 범용 servlet filter로 컨테이너에
     * 다시 등록되지 않도록 이 registration은 항상 비활성화한다.
     */
    @Bean
    FilterRegistrationBean<InternalServiceAuthenticationFilter> internalServiceAuthenticationFilterRegistration(
            InternalServiceAuthenticationFilter authenticationFilter
    ) {
        FilterRegistrationBean<InternalServiceAuthenticationFilter> registration =
                new FilterRegistrationBean<>(authenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    InternalServiceForbiddenHandler internalServiceForbiddenHandler() {
        return new InternalServiceForbiddenHandler();
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalIdentitySecurityFilterChain(
            HttpSecurity http,
            InternalServiceAuthenticationFilter authenticationFilter,
            InternalServiceForbiddenHandler forbiddenHandler
    ) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(forbiddenHandler)
                        .accessDeniedHandler(forbiddenHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/internal/identity/claims/*")
                        .hasAuthority(InternalServiceCredentialVerifier.PROJECTION_READ_AUTHORITY)
                        .anyRequest().denyAll())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
