package carelog.carelog.user.web.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import carelog.carelog.common.config.InternalLoopbackOnlyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** 독립적인 내부 쓰기 경계. 사용하지 않으면 일반 internal deny 규칙이 적용된다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "carelog.identity.enrollment", name = "enabled", havingValue = "true")
public class EnrollmentSecurityConfiguration {
    private static final String AUTHORITY = "ENROLLMENT_WRITE";
    private static final List<String> FORBIDDEN_HEADERS = List.of("X-Service-Id", "X-Service-Secret",
            "X-Gateway-Secret", "X-User-Id", "X-Organization-Id", "X-Role", "X-Public-Id");

    @Bean
    @Order(0)
    SecurityFilterChain enrollmentSecurityFilterChain(HttpSecurity http,
            @Value("${carelog.identity.enrollment.service-secret:}") String writeSecret,
            @Value("${carelog.internal.identity-claims.service-secret:}") String readSecret,
            @Value("${gateway.internal-secret:}") String gatewaySecret,
            @Value("${carelog.internal.loopback-only.enabled:false}") boolean loopbackOnly,
            Environment environment) throws Exception {
        validateSecrets(writeSecret, readSecret, gatewaySecret);
        if (loopbackOnly) {
            InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution(environment);
        }
        InternalLoopbackOnlyFilter loopbackFilter = new InternalLoopbackOnlyFilter(loopbackOnly);
        byte[] expected = digest(writeSecret);
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                var headers = request.getHeaders("Authorization");
                String value = headers.hasMoreElements() ? headers.nextElement() : null;
                boolean valid = !headers.hasMoreElements() && value != null && value.startsWith("Bearer ")
                        && MessageDigest.isEqual(expected, digest(value.substring(7)))
                        && FORBIDDEN_HEADERS.stream().noneMatch(h -> request.getHeader(h) != null);
                if (!valid) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                SecurityContextHolder.clearContext();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "enrollment-writer", null, AuthorityUtils.createAuthorityList(AUTHORITY)));
                try { chain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
            }
        };
        http.securityMatcher("/internal/v1/identity/carelog/enrollments/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> res.setStatus(403))
                        .accessDeniedHandler((req, res, e) -> res.setStatus(403)))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.PUT, "/internal/v1/identity/carelog/enrollments/*")
                        .hasAuthority(AUTHORITY).anyRequest().denyAll())
                .addFilterBefore(loopbackFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static void validateSecrets(String writeSecret, String readSecret, String gatewaySecret) {
        if (writeSecret == null || writeSecret.isBlank() || writeSecret.length() < 32
                || gatewaySecret == null || gatewaySecret.isBlank()
                || equal(writeSecret, gatewaySecret) || (readSecret != null && !readSecret.isBlank() && equal(writeSecret, readSecret))) {
            throw new IllegalStateException("Enrollment write credential must be at least 32 characters and distinct from read and gateway credentials");
        }
    }

    private static boolean equal(String first, String second) {
        return MessageDigest.isEqual(digest(first), digest(second));
    }
    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
