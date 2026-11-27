package carelog.carelog.user.web.internal;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code carelog.internal.loopback-only.enabled} 기본값(false)에서 기존 enrollment 동작이 바뀌지 않음을 고정한다.
 */
@SpringBootTest(classes = CarelogApplication.class, properties = {
        "carelog.identity.enrollment.enabled=true",
        "carelog.identity.enrollment.service-secret=test-write-credential-01234567890123456789",
        "carelog.internal.identity-claims.enabled=false",
        "gateway.internal-secret=test-gateway-credential-012345678901234567"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(PostgreSqlTestContainerConfiguration.class)
class EnrollmentLoopbackDisabledIntegrationTest {

    private static final String WRITE = "test-write-credential-01234567890123456789";
    private static final String PATH = "/internal/v1/identity/carelog/enrollments/";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void defaultFlagKeepsNonLoopbackPeerBehaviorUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        String userId = "u" + id;
        String body = "{\"userId\":\"" + userId + "\",\"email\":\"" + userId + "@example.test\",\"name\":\"Test\"}";
        mvc.perform(put(PATH + id).with(remote("10.0.0.5"))
                        .header("Authorization", "Bearer " + WRITE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertThat(users.findByExternalAccountId(id)).isPresent();
    }

    private static RequestPostProcessor remote(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }
}
