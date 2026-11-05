package carelog.carelog.customer;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.common.config.TenantContext;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.customer.app.CustomerService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
@AutoConfigureMockMvc
class CustomerProductApiIntegrationTest {

    private static final String GATEWAY_SECRET = "test-gateway-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // No outer @Transactional: controller requests commit through their service transaction;
    // JdbcTemplate assertions read outside the JPA persistence context.
    @Test
    @DisplayName("A/B Customer lifecycle matrix는 명시적 organization predicate와 Product 경계를 함께 검증한다")
    void customerLifecycle_isOrganizationScopedAndMutationBounded() throws Exception {
        UUID organizationA = UUID.randomUUID();
        UUID organizationB = UUID.randomUUID();
        User customerA = saveCustomer(organizationA, "A 고객");
        User customerB = saveCustomer(organizationB, "B 고객");

        // A MANAGER: create owner는 body가 아니라 Principal organization이다.
        String createResponse = mockMvc.perform(withManager(
                        post("/users/customers"), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "A 신규 고객",
                                  "customerMemo": "A 메모",
                                  "organizationId": "%s",
                                  "role": "MANAGER",
                                  "userId": "attacker-controlled"
                                }
                                """.formatted(organizationB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.displayName").value("A 신규 고객"))
                .andExpect(jsonPath("$.data.customerMemo").value("A 메모"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID createdPublicId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.publicId"));
        Map<String, Object> createdRow = customerRow(createdPublicId);
        assertThat(createdRow.get("organization_id")).isEqualTo(organizationA);
        assertThat(createdRow.get("role")).isEqualTo("CUSTOMER");
        assertThat(createdRow.get("name")).isEqualTo("A 신규 고객");
        assertThat(createdRow.get("customer_memo")).isEqualTo("A 메모");

        // A list는 A 고객만 반환한다.
        mockMvc.perform(withManager(get("/users/customers"), organizationA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].displayName").value(
                        org.hamcrest.Matchers.containsInAnyOrder("A 고객", "A 신규 고객")))
                .andExpect(jsonPath("$.data[*].email").doesNotExist())
                .andExpect(jsonPath("$.data[*].role").doesNotExist());

        // A detail/update 성공.
        mockMvc.perform(withManager(get("/users/customers/" + customerA.getPublicId()), organizationA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").value(customerA.getPublicId().toString()))
                .andExpect(jsonPath("$.data.displayName").value("A 고객"));

        mockMvc.perform(withManager(patch("/users/customers/" + customerA.getPublicId()), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "A 수정 고객",
                                  "customerMemo": "A 수정 메모",
                                  "organizationId": "%s",
                                  "role": "MANAGER",
                                  "publicId": "%s"
                                }
                                """.formatted(organizationB, customerB.getPublicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("A 수정 고객"))
                .andExpect(jsonPath("$.data.customerMemo").value("A 수정 메모"));

        mockMvc.perform(withManager(get("/users/customers/" + customerA.getPublicId()), organizationA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("A 수정 고객"))
                .andExpect(jsonPath("$.data.customerMemo").value("A 수정 메모"));

        Map<String, Object> updatedA = customerRow(customerA.getPublicId());
        assertThat(updatedA.get("organization_id")).isEqualTo(organizationA);
        assertThat(updatedA.get("role")).isEqualTo("CUSTOMER");
        assertThat(updatedA.get("name")).isEqualTo("A 수정 고객");
        assertThat(updatedA.get("customer_memo")).isEqualTo("A 수정 메모");

        // B list에는 A가 없고, A detail/update는 모두 404 conceal이다.
        mockMvc.perform(withManager(get("/users/customers"), organizationB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].displayName").value("B 고객"));

        mockMvc.perform(withManager(get("/users/customers/" + customerA.getPublicId()), organizationB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ExceptionStatus.CUSTOMER_NOT_FOUND.getMessage()));

        String nameBeforeCrossTenantUpdate = (String) updatedA.get("name");
        String memoBeforeCrossTenantUpdate = (String) updatedA.get("customer_memo");
        mockMvc.perform(withManager(patch("/users/customers/" + customerA.getPublicId()), organizationB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"B가 바꾸려는 이름\",\"customerMemo\":\"공격\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ExceptionStatus.CUSTOMER_NOT_FOUND.getMessage()));

        // MockMvc mutation이 반환된 뒤, JPA 영속성 컨텍스트 밖의 JDBC 조회로 A 행을 확인한다.
        Map<String, Object> afterCrossTenantUpdate = customerRow(customerA.getPublicId());
        assertThat(afterCrossTenantUpdate.get("name")).isEqualTo(nameBeforeCrossTenantUpdate);
        assertThat(afterCrossTenantUpdate.get("customer_memo")).isEqualTo(memoBeforeCrossTenantUpdate);
        assertThat(afterCrossTenantUpdate.get("organization_id")).isEqualTo(organizationA);

        // TenantContext가 없는 internal 경로도 organization을 인자로 요구하는 scoped method만 사용한다.
        TenantContext.clear();
        assertThatThrownBy(() -> customerService.findByPublicId(organizationB, customerA.getPublicId()))
                .hasMessage(ExceptionStatus.CUSTOMER_NOT_FOUND.getMessage());

        // Non-MANAGER는 Product Customer capability가 없다.
        mockMvc.perform(withRole(get("/users/customers"), organizationA, "CUSTOMER"))
                .andExpect(status().isForbidden());

        // Generic User mutation으로 Customer PII/password/delete를 우회할 수 없다.
        mockMvc.perform(withManager(patch("/users/" + customerA.getPublicId()), organizationA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"newPassword123","phoneEncrypted":"attacker-phone",
                                 "addressEncrypted":"attacker-address"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(withManager(delete("/users/" + customerA.getPublicId()), organizationA))
                .andExpect(status().isForbidden());

        Map<String, Object> afterGenericMutationAttempts = customerRow(customerA.getPublicId());
        assertThat(afterGenericMutationAttempts.get("deleted_at")).isNull();
        assertThat(afterGenericMutationAttempts.get("password")).isNull();
        assertThat(afterGenericMutationAttempts.get("organization_id")).isEqualTo(organizationA);
    }

    @Test
    @DisplayName("Gateway Secret이 없으면 403이고 사용자 식별이 없으면 401로 Customer API를 차단한다")
    void gatewayAuthenticationFailures_return403And401() throws Exception {
        UUID organizationId = UUID.randomUUID();

        mockMvc.perform(get("/users/customers")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Organization-Id", organizationId.toString())
                        .header("X-Role", "MANAGER")
                        .header("X-Public-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users/customers")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-Organization-Id", organizationId.toString())
                        .header("X-Role", "MANAGER")
                        .header("X-Public-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    private Map<String, Object> customerRow(UUID publicId) {
        return jdbcTemplate.queryForMap("""
                select organization_id, role, name, customer_memo, deleted_at, password
                from users
                where public_id = ?
                """, publicId);
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
