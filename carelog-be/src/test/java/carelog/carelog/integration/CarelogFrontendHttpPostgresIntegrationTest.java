package carelog.carelog.integration;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises the real browser-to-BE HTTP path with the existing test profile and disposable PostgreSQL.
 * Synthetic X-Gateway-Secret, X-User-Id, X-Organization-Id, X-Role, and X-Public-Id headers mirror the
 * existing BE integration fixture; no Gateway or Identity service is started by this test.
 */
@SpringBootTest(classes = CarelogApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class CarelogFrontendHttpPostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${gateway.internal-secret}")
    private String testGatewaySecret;

    @Test
    @DisplayName("FE Customer adapter writes through HTTP and reads the committed PostgreSQL row")
    void productionCustomerAdapterUsesHttpAndPostgres() throws Exception {
        String configuredFrontendRoot = System.getenv("CARELOG_FE_ROOT");
        if (configuredFrontendRoot == null || configuredFrontendRoot.isBlank()) {
            throw new IllegalStateException("CARELOG_FE_ROOT must point to the pinned Carelog FE checkout.");
        }

        Path frontendRoot = Path.of(configuredFrontendRoot).toAbsolutePath().normalize();
        assertThat(frontendRoot).isDirectory();
        assertThat(frontendRoot.resolve("node_modules")).isDirectory();
        assertThat(testGatewaySecret).isNotBlank();

        Path temporaryDirectory = Files.createTempDirectory("carelog-rpl64-fe-be-http-");
        try {
            Path playwrightConfig = copyTestResource(
                    "rpl64-fe-client/playwright.rpl64-http-postgres.config.mjs",
                    temporaryDirectory.resolve("playwright.rpl64-http-postgres.config.mjs"));
            Path frontendSpec = copyTestResource(
                    "rpl64-fe-client/carelog-customer-http-postgres.real.spec.mjs",
                    temporaryDirectory.resolve("carelog-customer-http-postgres.real.spec.mjs"));
            Path resultFile = temporaryDirectory.resolve("customer-result.json");
            Path playwrightLog = temporaryDirectory.resolve("playwright.log");

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "pnpm",
                    "exec",
                    "playwright",
                    "test",
                    "--config=" + playwrightConfig,
                    frontendSpec.toString());
            processBuilder.directory(frontendRoot.toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(playwrightLog.toFile());

            Map<String, String> environment = processBuilder.environment();
            environment.put("CARELOG_FE_ROOT", frontendRoot.toString());
            environment.put("CARELOG_RPL64_BE_TARGET", "http://127.0.0.1:" + port);
            environment.put("CARELOG_RPL64_CUSTOMER_RESULT_FILE", resultFile.toString());
            environment.put("CARELOG_RPL64_TEST_GATEWAY_SECRET", testGatewaySecret);

            Process playwright = processBuilder.start();
            if (!playwright.waitFor(3, TimeUnit.MINUTES)) {
                playwright.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                fail("The FE Playwright request timed out.\n" + readTextIfPresent(playwrightLog));
            }

            String playwrightOutput = readTextIfPresent(playwrightLog);
            assertThat(playwright.exitValue())
                    .as("The FE browser test must succeed. Output:\n%s", playwrightOutput)
                    .isZero();
            System.out.println(playwrightOutput);
            assertThat(resultFile).exists();

            JsonNode result = objectMapper.readTree(Files.readString(resultFile));
            UUID customerPublicId = UUID.fromString(result.path("id").asText());
            UUID organizationId = UUID.fromString(result.path("organizationId").asText());
            String displayName = result.path("displayName").asText();
            String customerMemo = result.path("customerMemo").asText();

            assertThat(result.path("listedDisplayName").asText()).isEqualTo(displayName);
            assertThat(result.path("visibleInOtherOrganization").asBoolean()).isFalse();

            // This query runs after the FE browser's HTTP request has completed and the API transaction committed.
            Map<String, Object> persistedRow = jdbcTemplate.queryForMap("""
                    SELECT public_id, organization_id, role, name, customer_memo
                    FROM users
                    WHERE public_id = ?
                    """, customerPublicId);
            assertThat(persistedRow.get("public_id")).isEqualTo(customerPublicId);
            assertThat(persistedRow.get("organization_id")).isEqualTo(organizationId);
            assertThat(persistedRow.get("role")).isEqualTo("CUSTOMER");
            assertThat(persistedRow.get("name")).isEqualTo(displayName);
            assertThat(persistedRow.get("customer_memo")).isEqualTo(customerMemo);
        } finally {
            deleteDirectory(temporaryDirectory);
        }
    }

    private static Path copyTestResource(String resourceName, Path destination) throws IOException {
        try (InputStream resource = CarelogFrontendHttpPostgresIntegrationTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (resource == null) {
                throw new IOException("Missing FE integration resource: " + resourceName);
            }
            Files.copy(resource, destination);
            return destination;
        }
    }

    private static String readTextIfPresent(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
