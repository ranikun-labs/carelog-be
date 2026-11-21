package carelog.carelog.user.web.internal;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CarelogApplication.class, properties = {
        "carelog.internal.identity-claims.enabled=true",
        "carelog.internal.identity-claims.service-secret=test-read-credential-012345678901234567890",
        "gateway.internal-secret=test-gateway-credential-012345678901234567"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(PostgreSqlTestContainerConfiguration.class)
class EnrollmentDisabledIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void defaultHasNoControllerAndDeniesWrite() throws Exception {
        assertThat(context.getBeansOfType(EnrollmentController.class)).isEmpty();
        mvc.perform(put("/internal/v1/identity/carelog/enrollments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer test-write-credential-01234567890123456789")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
