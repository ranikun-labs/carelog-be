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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CarelogApplication.class, properties = {
        "carelog.identity.enrollment.enabled=true",
        "carelog.identity.enrollment.service-secret=test-write-credential-01234567890123456789",
        "carelog.internal.identity-claims.enabled=true",
        "carelog.internal.identity-claims.service-secret=test-read-credential-012345678901234567890",
        "gateway.internal-secret=test-gateway-credential-012345678901234567"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(PostgreSqlTestContainerConfiguration.class)
class EnrollmentBoundaryIntegrationTest {
    private static final String WRITE = "test-write-credential-01234567890123456789";
    private static final String READ = "test-read-credential-012345678901234567890";
    private static final String PATH = "/internal/v1/identity/carelog/enrollments/";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void exactPutCreatesAndReplaysWithoutExposingData() throws Exception {
        UUID id = UUID.randomUUID();
        String userId = "u" + UUID.randomUUID();
        String body = "{\"userId\":\"" + userId + "\",\"email\":\"" + userId + "@example.test\",\"name\":\"Test\"}";
        for (int i = 0; i < 2; i++) mvc.perform(put(PATH + id).header("Authorization", "Bearer " + WRITE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(content().string(""));
        assertThat(users.findByExternalAccountId(id)).isPresent();
        mvc.perform(put(PATH + id).header("Authorization", "Bearer " + WRITE)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("Test", "Changed")))
                .andExpect(status().isConflict()).andExpect(content().string(""));
        mvc.perform(get("/internal/identity/claims/" + id).header("X-Service-Id", "platform-identity")
                        .header("X-Service-Secret", READ)).andExpect(status().isOk());
    }

    @Test
    void credentialsMethodsAndPathsRemainSeparated() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"userId\":\"synthetic\",\"email\":\"x@example.test\",\"name\":\"Test\"}";
        mvc.perform(put(PATH + id).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(PATH + id).header("Authorization", "Bearer " + READ).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(PATH + id).header("X-Service-Id", "platform-identity").header("X-Service-Secret", READ).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(PATH + id).header("Authorization", "Bearer " + WRITE).header("X-Gateway-Secret", "other").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(PATH + id).header("Authorization", "Bearer " + WRITE).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(PATH + id + "/extra").header("Authorization", "Bearer " + WRITE).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/internal/identity/claims/" + id).header("Authorization", "Bearer " + WRITE)).andExpect(status().isForbidden());
        assertThat(users.findByExternalAccountId(id)).isEmpty();
    }

    @Test
    void malformedRequestsAreBadRequest() throws Exception {
        mvc.perform(put(PATH + "not-uuid").header("Authorization", "Bearer " + WRITE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(""));
        mvc.perform(put(PATH + UUID.randomUUID()).header("Authorization", "Bearer " + WRITE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(content().string(""));
    }
}
