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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code carelog.internal.loopback-only.enabled=true}일 때 enrollment 피어 경계를 전체 context에서 고정한다.
 * 인증 계약 자체는 {@link EnrollmentBoundaryIntegrationTest}가 담당한다.
 */
@SpringBootTest(classes = CarelogApplication.class, properties = {
        "carelog.identity.enrollment.enabled=true",
        "carelog.identity.enrollment.service-secret=test-write-credential-01234567890123456789",
        "carelog.internal.identity-claims.enabled=false",
        "gateway.internal-secret=test-gateway-credential-012345678901234567",
        "carelog.internal.loopback-only.enabled=true"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(PostgreSqlTestContainerConfiguration.class)
class EnrollmentLoopbackIntegrationTest {

    private static final String WRITE = "test-write-credential-01234567890123456789";
    private static final String PATH = "/internal/v1/identity/carelog/enrollments/";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void vpcPeerIsForbiddenEvenWithValidCredentialAndForwardedForLoopback() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(enroll(id, "10.0.0.5").header("X-Forwarded-For", "127.0.0.1"))
                .andExpect(status().isForbidden());
        assertThat(users.findByExternalAccountId(id)).isEmpty();
    }

    @Test
    void loopbackPeerReachesExistingAuthAndController() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(enroll(id, "127.0.0.1")).andExpect(status().isCreated());
        assertThat(users.findByExternalAccountId(id)).isPresent();
    }

    @Test
    void loopbackPeerWithoutCredentialStillForbidden() throws Exception {
        mvc.perform(put(PATH + UUID.randomUUID()).with(remote("127.0.0.1"))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder enroll(UUID id, String remoteAddr) {
        return put(PATH + id).with(remote(remoteAddr))
                .header("Authorization", "Bearer " + WRITE)
                .contentType(MediaType.APPLICATION_JSON).content(body());
    }

    private static String body() {
        String userId = "u" + UUID.randomUUID();
        return "{\"userId\":\"" + userId + "\",\"email\":\"" + userId + "@example.test\",\"name\":\"Test\"}";
    }

    private static RequestPostProcessor remote(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }
}
