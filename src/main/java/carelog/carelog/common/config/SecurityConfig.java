package carelog.carelog.common.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.*;
import org.springframework.security.config.annotation.web.builders.*;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
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
                        .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}