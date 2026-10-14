package carelog.carelog.common.config;

import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import carelog.carelog.auth.web.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigActuatorPortIntegrationTest {

    private static final String GATEWAY_SECRET = "test-gateway-internal-secret";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @Test
    void defaultProdProdLocalAndSharedPortKeepManagementAndGatewayBoundaries() throws Exception {
        verifyProfile("default", List.of(), false, true);
        verifyProfile("prod", List.of("prod"), false, true);
        verifyProfile("prod+local", List.of("prod", "local"), false, true);
        verifyProfile("prod with a shared port", List.of("prod"), true, false);
    }

    private void verifyProfile(String label, List<String> profiles, boolean sharedPort,
                               boolean separateManagementPort) throws Exception {
        int applicationPort = availablePort();
        int managementPort = sharedPort ? applicationPort : availablePort();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.config.name", "actuator-security-test");
        properties.put("spring.main.banner-mode", "off");
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", applicationPort);
        properties.put("server.servlet.context-path", "/api/v1");
        if (!sharedPort) properties.put("management.server.address", "127.0.0.1");
        properties.put("management.server.port", managementPort);
        properties.put("management.endpoints.web.exposure.include", "health");
        properties.put("management.endpoint.health.show-details", "never");
        properties.put("gateway.internal-secret", GATEWAY_SECRET);
        properties.put("carelog.security.swagger.public", false);
        properties.put("carelog.security.cors.allow-any-origin", false);
        properties.put("carelog.security.cors.allowed-origins", "https://carelog.example.com");
        properties.put("carelog.security.cors.allow-credentials", false);

        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setDefaultProperties(properties);
        if (!profiles.isEmpty()) application.setAdditionalProfiles(profiles.toArray(String[]::new));

        try (ConfigurableApplicationContext context = application.run()) {
            int appPort = context.getEnvironment().getProperty("local.server.port", Integer.class, applicationPort);
            int expectedManagementStatus = profiles.contains("prod") && separateManagementPort ? 200 : 403;

            assertStatus(label + " application management URL remains behind Gateway secret",
                    appPort, "/api/v1/actuator/health", null, 403);
            assertStatus(label + " health on management listener",
                    managementPort, sharedPort ? "/api/v1/actuator/health" : "/actuator/health",
                    null, expectedManagementStatus);
            assertStatus(label + " public login still requires the Gateway secret",
                    appPort, "/api/v1/auth/login", null, 403);
            assertStatus(label + " public login remains available through Gateway",
                    appPort, "/api/v1/auth/login", GATEWAY_SECRET, 200);
            assertStatus(label + " internal routes remain denied after Gateway authentication",
                    appPort, "/api/v1/internal/other", GATEWAY_SECRET, 401);
            assertStatus(label + " Swagger remains denied after Gateway authentication",
                    appPort, "/api/v1/swagger-ui.html", GATEWAY_SECRET, 401);
            assertStatus(label + " API Docs remain denied after Gateway authentication",
                    appPort, "/api/v1/v3/api-docs", GATEWAY_SECRET, 401);
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void assertStatus(String label, int port, String path, String gatewaySecret,
                                     int expectedStatus) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET();
        if (gatewaySecret != null) request.header("X-Gateway-Secret", gatewaySecret);
        HttpResponse<String> response = HTTP.send(request.build(), BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), label + " returned " + response.body());
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({SecurityConfig.class, ClockConfig.class, JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class, LoginProbeController.class})
    static class TestApplication {
    }

    @RestController
    static class LoginProbeController {
        @GetMapping("/auth/login")
        String login() {
            return "login";
        }
    }
}
