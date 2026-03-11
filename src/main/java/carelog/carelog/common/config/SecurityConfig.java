package carelog.carelog.common.config;

import carelog.carelog.auth.app.JwtTokenProvider;
import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import carelog.carelog.auth.web.JwtAuthenticationEntryPoint;
import carelog.carelog.auth.web.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    private static final String[] PUBLIC_URLS = {
            "/auth/login",
            "/auth/refresh",
            "/users/managers",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                .authorizeHttpRequests(auth -> auth
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
                        new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager (
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}