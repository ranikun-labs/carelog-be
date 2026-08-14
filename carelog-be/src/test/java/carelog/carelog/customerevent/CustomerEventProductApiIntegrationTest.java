package carelog.carelog.customerevent;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
@AutoConfigureMockMvc
class CustomerEventProductApiIntegrationTest {

    private static final String GATEWAY_SECRET = "test-gateway-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    @DisplayName("CustomerEvent API는 canonical lifecycle, ownership, bounded list와 PATCH wire contract를 보존한다")
    void customerEventApi_preservesCanonicalContract() throws Exception {
        UUID organizationA = UUID.randomUUID();
        UUID organizationB = UUID.randomUUID();
        User customerA = saveCustomer(organizationA, "A 고객");
        User customerB = saveCustomer(organizationA, "B 고객");

        mockMvc.perform(withManager(post("/customer-events"), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "status": "PLANNED",
                                  "scheduledAt": "2026-08-20T09:00:00+09:00"
                                }
                                """.formatted(customerB.getPublicId())))
                .andExpect(status().isCreated());

        String createBody = mockMvc.perform(withManager(post("/customer-events"), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "status": "PLANNED",
                                  "scheduledAt": "2026-08-20T10:00:00+09:00",
                                  "descriptor": "상담",
                                  "note": "초기 메모",
                                  "overdue": true,
                                  "organizationId": "%s"
                                }
                                """.formatted(customerA.getPublicId(), organizationB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerId").value(customerA.getPublicId().toString()))
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-20T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.occurredAt").doesNotExist())
                .andExpect(jsonPath("$.data.overdue").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String eventId = com.jayway.jsonpath.JsonPath.read(createBody, "$.data.id");

        mockMvc.perform(withManager(get("/customer-events/" + eventId), organizationA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eventId));
        mockMvc.perform(withManager(get("/customer-events/" + eventId), organizationB))
                .andExpect(status().isNotFound());

        // 아무 범위도 없는 workspace 전체 dump와 부분 범위는 거부한다.
        mockMvc.perform(withManager(get("/customer-events"), organizationA))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withManager(get("/customer-events")
                        .param("from", "2026-08-01T00:00:00Z"), organizationA))
                .andExpect(status().isBadRequest());

        mockMvc.perform(withManager(get("/customer-events")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-31T23:59:59Z"), organizationA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // customerId와 기간을 함께 주면 limit 적용 전에 DB에서 고객을 좁힌다.
        mockMvc.perform(withManager(get("/customer-events")
                        .param("customerId", customerA.getPublicId().toString())
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-31T23:59:59Z")
                        .param("limit", "1"), organizationA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].customerId")
                        .value(customerA.getPublicId().toString()));

        // customer history는 server limit으로 bounded하며 상한을 넘기면 거부한다.
        mockMvc.perform(withManager(get("/customer-events")
                        .param("customerId", customerA.getPublicId().toString())
                        .param("limit", "50"), organizationA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(withManager(get("/customer-events")
                        .param("customerId", customerA.getPublicId().toString())
                        .param("limit", "101"), organizationA))
                .andExpect(status().isBadRequest());

        // absent note는 유지하고, present null descriptor는 clear한다.
        mockMvc.perform(withManager(patch("/customer-events/" + eventId), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descriptor\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.descriptor").doesNotExist())
                .andExpect(jsonPath("$.data.note").value("초기 메모"));

        // PLANNED lifecycle에서 occurredAt 직접 수정과 scheduledAt clear는 거부한다.
        mockMvc.perform(withManager(patch("/customer-events/" + eventId), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occurredAt\":\"2026-08-20T11:00:00+09:00\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(withManager(patch("/customer-events/" + eventId), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":null}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(withManager(post("/customer-events/" + eventId + "/occur"), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occurredAt\":\"2026-08-20T10:30:00+09:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OCCURRED"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-08-20T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.occurredAt").value("2026-08-20T10:30:00+09:00"));

        mockMvc.perform(withManager(post("/customer-events/" + eventId + "/cancel"), organizationA))
                .andExpect(status().isConflict());

        mockMvc.perform(withRole(get("/customer-events/" + eventId), organizationA, "USER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withManager(patch("/customer-events/" + eventId), organizationB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"침범\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @DisplayName("CustomerEvent 요청 본문이 JSON null이면 500 대신 잘못된 요청으로 거부한다")
    void requestBodies_null_returnBadRequest() throws Exception {
        UUID organizationId = UUID.randomUUID();
        User customer = saveCustomer(organizationId, "본문 검증 고객");

        mockMvc.perform(withManager(post("/customer-events"), organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        String createBody = mockMvc.perform(withManager(post("/customer-events"), organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "status": "PLANNED",
                                  "scheduledAt": "2026-08-20T10:00:00+09:00"
                                }
                                """.formatted(customer.getPublicId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String eventId = com.jayway.jsonpath.JsonPath.read(createBody, "$.data.id");

        mockMvc.perform(withManager(patch("/customer-events/" + eventId), organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(withManager(post("/customer-events/" + eventId + "/occur"), organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    private User saveCustomer(UUID organizationId, String name) {
        User customer = User.builder().name(name).role(UserRole.CUSTOMER).build();
        customer.assignOrganization(organizationId);
        return userRepository.saveAndFlush(customer);
    }

    private MockHttpServletRequestBuilder withManager(
            MockHttpServletRequestBuilder request, UUID organizationId) {
        return withRole(request, organizationId, "MANAGER");
    }

    private MockHttpServletRequestBuilder withRole(
            MockHttpServletRequestBuilder request, UUID organizationId, String role) {
        return request
                .header("X-Gateway-Secret", GATEWAY_SECRET)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-Organization-Id", organizationId.toString())
                .header("X-Role", role)
                .header("X-Public-Id", UUID.randomUUID().toString());
    }
}
