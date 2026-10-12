package carelog.carelog.common.config;

import carelog.carelog.auth.web.*;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.*;
import org.springframework.security.config.annotation.method.configuration.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.*;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.config.http.*;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.*;
import org.springframework.web.cors.*;

import java.util.*;


@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_URLS = {
            "/auth/login",
            "/auth/refresh",
            "/users/managers",
            "/api/v1",
            "/error"
    };
    private static final String[] SWAGGER_URLS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    @Value("${gateway.internal-secret}")
    private String internalSecret;
    @Value("${carelog.security.swagger.public:true}")
    private boolean swaggerPublic;
    @Value("${carelog.security.cors.allow-any-origin:true}")
    private boolean corsAllowAnyOrigin;
    @Value("${carelog.security.cors.allowed-origins:}")
    private List<String> corsAllowedOrigins;
    @Value("${carelog.security.cors.allow-credentials:true}")
    private boolean corsAllowCredentials;
    @Value("${carelog.security.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private List<String> corsAllowedMethods;
    @Value("${carelog.security.cors.allowed-headers:*}")
    private List<String> corsAllowedHeaders;

    /**
     * Actuator는 별도 management port에 두고 Compose에서 host loopback에만 publish한다.
     * 이 체인은 health/metrics 전용 port에서만 사용하며 Product API의 Gateway 경계는 바꾸지 않는다.
     */
    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    /**
     * 일반 Product chain — ADR-0019 internal 경계와 별개로 {@code /internal/**}은 이 chain에서도
     * 항상 denyAll이다. {@code carelog.internal.identity-claims.enabled=false}일 때는
     * {@link carelog.carelog.auth.web.internal.InternalIdentitySecurityConfiguration}이 아예
     * 등록되지 않으므로, {@code /internal/**}에 대한 유일한 차단 지점이 이 chain이 된다.
     */
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/internal/**").denyAll();
                    auth.requestMatchers(
                            HttpMethod.POST,
                            "/auth/oauth/kakao/authorization",
                            "/auth/oauth/kakao/exchange"
                    ).permitAll();
                    auth.requestMatchers(PUBLIC_URLS).permitAll();
                    if (swaggerPublic) {
                        auth.requestMatchers(SWAGGER_URLS).permitAll();
                    } else {
                        auth.requestMatchers(SWAGGER_URLS).denyAll();
                    }
                    auth.anyRequest().authenticated();
                })

                .addFilterBefore(
                        new GatewayHeaderAuthFilter(internalSecret),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(new TenantFilter(), GatewayHeaderAuthFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        if (corsAllowAnyOrigin) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()
                    || corsAllowedOrigins.stream().anyMatch(origin ->
                    origin == null || origin.isBlank() || origin.contains("*"))) {
                throw new IllegalStateException(
                        "Production CORS requires one or more exact, non-wildcard Origins");
            }
            config.setAllowedOrigins(corsAllowedOrigins);
        }
        config.setAllowedMethods(corsAllowedMethods);
        config.setAllowedHeaders(corsAllowedHeaders);
        config.setAllowCredentials(corsAllowCredentials);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
