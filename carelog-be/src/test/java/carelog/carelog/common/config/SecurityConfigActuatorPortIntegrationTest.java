package carelog.carelog.common.config;

import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import carelog.carelog.auth.web.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void prodWithDynamicApplicationAndManagementPortsUsesSeparateHttpListeners() throws Exception {
        Map<String, Object> properties = baseProperties();
        properties.put("server.port", 0);
        properties.put("management.server.address", "127.0.0.1");
        properties.put("management.server.port", 0);

        try (ConfigurableApplicationContext context = startApplication(properties, "prod")) {
            int appPort = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            int managementPort = context.getEnvironment()
                    .getRequiredProperty("local.management.port", Integer.class);

            assertTrue(appPort > 0, "Spring Boot must assign an actual application port");
            assertTrue(managementPort > 0, "Spring Boot must assign an actual management port");
            assertNotEquals(appPort, managementPort,
                    "server.port=0 and management.server.port=0 must bind distinct listeners");
            assertEquals(ManagementPortType.DIFFERENT,
                    ManagementPortType.get(context.getEnvironment()));
            assertTrue(context.containsBean("actuatorSecurityFilterChain"));

            assertStatus("prod dynamic application health stays behind Gateway secret",
                    appPort, "/api/v1/actuator/health", null, 403);
            assertStatus("prod dynamic management listener permits health",
                    managementPort, "/actuator/health", null, 200);
            assertGatewayBoundaries("prod dynamic", appPort);
        }
    }

    @Test
    void prodWithDisabledManagementPortDoesNotRegisterPermittingChain() throws Exception {
        Map<String, Object> properties = baseProperties();
        properties.put("server.port", availablePort());
        properties.put("management.server.port", -1);

        try (ConfigurableApplicationContext context = startApplication(properties, "prod")) {
            int appPort = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);

            assertEquals(ManagementPortType.DISABLED,
                    ManagementPortType.get(context.getEnvironment()));
            assertTrue(!context.containsBean("actuatorSecurityFilterChain"),
                    "a disabled management listener must not register the permit-all chain");
            assertNull(context.getEnvironment().getProperty("local.management.port"));
            assertStatus("prod with disabled management has no unauthenticated app health",
                    appPort, "/api/v1/actuator/health", null, 403);
            assertGatewayBoundaries("prod with disabled management", appPort);
        }
    }

    private void verifyProfile(String label, List<String> profiles, boolean sharedPort,
                               boolean separateManagementPort) throws Exception {
        int applicationPort = availablePort();
        int managementPort = sharedPort ? applicationPort : availablePort();
        Map<String, Object> properties = baseProperties();
        properties.put("server.port", applicationPort);
        if (!sharedPort) properties.put("management.server.address", "127.0.0.1");
        properties.put("management.server.port", managementPort);
        try (ConfigurableApplicationContext context = startApplication(properties,
                profiles.toArray(String[]::new))) {
            int appPort = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            int actualManagementPort = sharedPort ? appPort : context.getEnvironment()
                    .getRequiredProperty("local.management.port", Integer.class);
            assertEquals(sharedPort ? ManagementPortType.SAME : ManagementPortType.DIFFERENT,
                    ManagementPortType.get(context.getEnvironment()));
            int expectedManagementStatus = profiles.contains("prod") && separateManagementPort ? 200 : 403;
            assertEquals(profiles.contains("prod") && separateManagementPort,
                    context.containsBean("actuatorSecurityFilterChain"));

            assertStatus(label + " application management URL remains behind Gateway secret",
                    appPort, "/api/v1/actuator/health", null, 403);
            assertStatus(label + " health on management listener",
                    actualManagementPort, sharedPort ? "/api/v1/actuator/health" : "/actuator/health",
                    null, expectedManagementStatus);
            assertGatewayBoundaries(label, appPort);
        }
    }

    private static Map<String, Object> baseProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.config.name", "actuator-security-test");
        properties.put("spring.main.banner-mode", "off");
        properties.put("server.address", "127.0.0.1");
        properties.put("server.servlet.context-path", "/api/v1");
        properties.put("management.endpoints.web.exposure.include", "health");
        properties.put("management.endpoint.health.show-details", "never");
        properties.put("gateway.internal-secret", GATEWAY_SECRET);
        properties.put("carelog.security.swagger.public", false);
        properties.put("carelog.security.cors.allow-any-origin", false);
        properties.put("carelog.security.cors.allowed-origins", "https://carelog.example.com");
        properties.put("carelog.security.cors.allow-credentials", false);
        return properties;
    }

    private static ConfigurableApplicationContext startApplication(Map<String, Object> properties,
                                                                   String... profiles) {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setDefaultProperties(properties);
        if (profiles.length > 0) application.setAdditionalProfiles(profiles);
        return application.run();
    }

    private static void assertGatewayBoundaries(String label, int appPort) throws Exception {
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
