package carelog.carelog.common.config;

import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import carelog.carelog.auth.web.JwtAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigCorsConfigurationTest {

    @Test
    void productionCorsAllowsOnlyConfiguredOriginsWithoutCredentials() {
        SecurityConfig securityConfig = new SecurityConfig(
                new JwtAuthenticationEntryPoint(new ObjectMapper(), Clock.systemUTC()),
                new JwtAccessDeniedHandler(new ObjectMapper(), Clock.systemUTC()));
        ReflectionTestUtils.setField(securityConfig, "corsAllowAnyOrigin", false);
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigins", List.of("https://carelog.example"));
        ReflectionTestUtils.setField(securityConfig, "corsAllowCredentials", false);
        ReflectionTestUtils.setField(securityConfig, "corsAllowedMethods", List.of("GET", "POST", "OPTIONS"));
        ReflectionTestUtils.setField(securityConfig, "corsAllowedHeaders", List.of("Authorization", "Content-Type"));

        CorsConfiguration cors = corsFor(securityConfig, "/api/v1/customers");

        assertThat(cors.checkOrigin("https://carelog.example")).isEqualTo("https://carelog.example");
        assertThat(cors.checkOrigin("https://attacker.example")).isNull();
        assertThat(cors.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(cors.getAllowCredentials()).isFalse();
        assertThat(cors.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
    }

    @Test
    void localCorsRetainsTheExistingWildcardPatternBehavior() {
        SecurityConfig securityConfig = new SecurityConfig(
                new JwtAuthenticationEntryPoint(new ObjectMapper(), Clock.systemUTC()),
                new JwtAccessDeniedHandler(new ObjectMapper(), Clock.systemUTC()));
        ReflectionTestUtils.setField(securityConfig, "corsAllowAnyOrigin", true);
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigins", List.of());
        ReflectionTestUtils.setField(securityConfig, "corsAllowCredentials", true);
        ReflectionTestUtils.setField(securityConfig, "corsAllowedMethods", List.of("GET", "POST", "OPTIONS"));
        ReflectionTestUtils.setField(securityConfig, "corsAllowedHeaders", List.of("*"));

        CorsConfiguration cors = corsFor(securityConfig, "/api/v1/customers");

        assertThat(cors.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThat(cors.getAllowedOriginPatterns()).containsExactly("*");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void productionCorsRejectsAnEmptyOrWildcardOriginList() {
        SecurityConfig securityConfig = new SecurityConfig(null, null);
        ReflectionTestUtils.setField(securityConfig, "corsAllowAnyOrigin", false);
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigins", List.of("*"));

        assertThatThrownBy(securityConfig::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-wildcard Origins");

        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigins", List.of());
        assertThatThrownBy(securityConfig::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class);
    }

    private CorsConfiguration corsFor(SecurityConfig securityConfig, String path) {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        return source.getCorsConfiguration(request);
    }
}
