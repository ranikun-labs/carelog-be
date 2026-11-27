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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 롤백 window 구성을 한 context에서 고정한다: enrollment loopback ON, claims 활성, claims loopback OFF.
 * 구 Core(rev:3)가 8080 VPC에서 claims를 계속 호출할 수 있고, enrollment 쓰기는 loopback에서만 된다.
 */
@SpringBootTest(classes = CarelogApplication.class, properties = {
        "carelog.identity.enrollment.enabled=true",
        "carelog.identity.enrollment.service-secret=test-write-credential-01234567890123456789",
        "carelog.internal.identity-claims.enabled=true",
        "carelog.internal.identity-claims.service-secret=test-read-credential-012345678901234567890",
        "carelog.internal.loopback-only.enabled=true",
        "carelog.internal.claims-loopback-only.enabled=false",
        "gateway.internal-secret=test-gateway-credential-012345678901234567"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(PostgreSqlTestContainerConfiguration.class)
class EnrollmentRollbackWindowIntegrationTest {

    private static final String WRITE = "test-write-credential-01234567890123456789";
    private static final String READ = "test-read-credential-012345678901234567890";
    private static final String ENROLL = "/internal/v1/identity/carelog/enrollments/";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void rollbackWindowCombination() throws Exception {
        UUID vpcId = UUID.randomUUID();
        mvc.perform(enroll(vpcId, "10.0.0.5")).andExpect(status().isForbidden());
        assertThat(users.findByExternalAccountId(vpcId)).isEmpty();

        // claims는 claims-loopback OFF이므로 VPC peer도 controller까지 도달한다(부재 계정 → 404, 403 아님).
        mvc.perform(get("/internal/identity/claims/" + UUID.randomUUID())
                        .header("X-Service-Id", "platform-identity")
                        .header("X-Service-Secret", READ)
                        .with(remote("10.0.0.5")))
                .andExpect(status().isNotFound());

        UUID loopbackId = UUID.randomUUID();
        mvc.perform(enroll(loopbackId, "127.0.0.1")).andExpect(status().isCreated());
        assertThat(users.findByExternalAccountId(loopbackId)).isPresent();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder enroll(UUID id, String addr) {
        String userId = "u" + id;
        String body = "{\"userId\":\"" + userId + "\",\"email\":\"" + userId + "@example.test\",\"name\":\"Test\"}";
        return put(ENROLL + id).with(remote(addr))
                .header("Authorization", "Bearer " + WRITE)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor remote(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }
}
