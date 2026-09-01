package carelog.carelog.user.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Identity claims private API의 opt-in composition.
 *
 * <p>별도 chain을 사용해 기존 GatewayHeaderAuthFilter와 사용자 JWT 경계를 재사용하지 않는다.
 * property가 false이면 controller, service, filter, chain 모두 등록되지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "carelog.internal.identity-claims",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(IdentityClaimsInternalApiProperties.class)
public class IdentityClaimsInternalApiConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain identityClaimsSecurityFilterChain(
            HttpSecurity http,
            IdentityClaimsInternalApiProperties properties
    ) throws Exception {
        IdentityClaimsServiceTokenFilter serviceTokenFilter =
                new IdentityClaimsServiceTokenFilter(properties.requiredServiceToken());

        http
                .securityMatcher("/internal/identity/accounts/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(serviceTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
