package carelog.carelog.common.config;

import carelog.carelog.auth.web.*;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.*;
import org.springframework.security.config.annotation.method.configuration.*;
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
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1",
            "/error"
    };
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    @Value("${gateway.internal-secret}")
    private String internalSecret;

    @Bean
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

                .authorizeHttpRequests(auth -> auth
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/auth/oauth/kakao/authorization",
                                        "/auth/oauth/kakao/exchange"
                                ).permitAll()
                                .requestMatchers(PUBLIC_URLS).permitAll()
                                .anyRequest().authenticated()
                        // 운영용이라 차후에 활성화
//                        .requestMatchers(
//                                new AntPathRequestMatcher("/"),
//                                new AntPathRequestMatcher("/api/v1"),
//                                new AntPathRequestMatcher("/api/v1/"),
//                                new AntPathRequestMatcher("/login"),
//                                new AntPathRequestMatcher("/api/auth/signup"),
//                                new AntPathRequestMatcher("/users"),
//                                new AntPathRequestMatcher("/api/health"),
//                                new AntPathRequestMatcher("/error**"),
//                                new AntPathRequestMatcher("/api/v1/error**")
//                        ).permitAll()
//                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
//                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll() // API 문서 허용
//                        ---- 아래 authenticated가 보안 로직 ----
//                        .anyRequest().authenticated());
                        // 개발중: 모든 요청 허용
//                        .anyRequest().permitAll());
                )

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
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
