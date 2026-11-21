package carelog.carelog.user.web.internal;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CarelogApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.servlet.context-path=/api/v1",
        "carelog.identity.enrollment.enabled=true",
        "carelog.identity.enrollment.service-secret=test-write-credential-01234567890123456789",
        "gateway.internal-secret=test-gateway-credential-012345678901234567"
})
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class EnrollmentHttpContextIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;

    @Test
    void realHttpUsesContextPrefixedPathAndStillRequiresWriteCredential() {
        UUID externalId = UUID.randomUUID();
        String userId = "u" + UUID.randomUUID();
        String body = "{\"userId\":\"" + userId + "\",\"email\":\"" + userId + "@example.test\",\"name\":\"Test\"}";
        String base = "http://127.0.0.1:" + port;
        String path = "/api/v1/internal/v1/identity/carelog/enrollments/" + externalId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("test-write-credential-01234567890123456789");
        var request = new HttpEntity<>(body, headers);
        var first = http.exchange(base + path, HttpMethod.PUT, request, String.class);
        var replay = http.exchange(base + path, HttpMethod.PUT, request, String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isNullOrEmpty();
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(users.findByExternalAccountId(externalId)).isPresent();

        headers.setBearerAuth("incorrect");
        assertThat(http.exchange(base + path, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange(base + "/internal/v1/identity/carelog/enrollments/" + externalId,
                HttpMethod.PUT, request, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
