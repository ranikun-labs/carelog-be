package carelog.carelog.customer.web;

import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import carelog.carelog.auth.web.JwtAuthenticationEntryPoint;
import carelog.carelog.common.config.ClockConfig;
import carelog.carelog.common.config.SecurityConfig;
import carelog.carelog.common.web.exception.GlobalExceptionHandler;
import carelog.carelog.customer.app.CustomerService;
import carelog.carelog.customer.web.dto.CustomerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CustomerController.class,
        properties = "gateway.internal-secret=test-gateway-internal-secret"
)
@Import({
        SecurityConfig.class,
        ClockConfig.class,
        GlobalExceptionHandler.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class CustomerControllerTest {

    private static final String GATEWAY_SECRET = "test-gateway-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    @DisplayName("Customer Product response는 publicId·displayName·customerMemo allowlist만 노출한다")
    void create_returnsProductCustomerContractOnly() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(customerService.create(any(), eq(organizationId)))
                .thenReturn(new CustomerResponse(publicId, "고객 A", "메모"));

        mockMvc.perform(withManager(post("/users/customers"), organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "고객 A",
                                  "customerMemo": "메모",
                                  "organizationId": "00000000-0000-0000-0000-000000000001",
                                  "role": "MANAGER",
                                  "password": "should-not-bind"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.publicId").value(publicId.toString()))
                .andExpect(jsonPath("$.data.displayName").value("고객 A"))
                .andExpect(jsonPath("$.data.customerMemo").value("메모"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.role").doesNotExist())
                .andExpect(jsonPath("$.data.organizationId").doesNotExist())
                .andExpect(jsonPath("$.data.internalId").doesNotExist());

        verify(customerService).create(any(), eq(organizationId));
    }

    @Test
    @DisplayName("Customer collection도 현재 Principal organization scope를 service에 전달한다")
    void list_passesPrincipalOrganization() throws Exception {
        UUID organizationId = UUID.randomUUID();
        when(customerService.findAll(organizationId, "고객"))
                .thenReturn(List.of(new CustomerResponse(UUID.randomUUID(), "고객 A", null)));

        mockMvc.perform(withManager(get("/users/customers"), organizationId)
                        .param("name", "고객"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("고객 A"));

        verify(customerService).findAll(organizationId, "고객");
    }

    @Test
    @DisplayName("MANAGER가 아닌 Principal은 Customer Product capability를 사용할 수 없다")
    void customerRole_isForbidden() throws Exception {
        mockMvc.perform(withRole(get("/users/customers"), UUID.randomUUID(), "CUSTOMER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(customerService);
    }

    @Test
    @DisplayName("필수 displayName validation은 기존 ApiResponse error envelope의 400으로 매핑한다")
    void create_blankDisplayName_isBadRequest() throws Exception {
        mockMvc.perform(withManager(post("/users/customers"), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerService);
    }

    private MockHttpServletRequestBuilder withManager(
            MockHttpServletRequestBuilder request, UUID organizationId) {
        return withRole(request, organizationId, "MANAGER");
    }

    private MockHttpServletRequestBuilder withRole(
            MockHttpServletRequestBuilder request, UUID organizationId, String role) {
        UUID accountId = UUID.randomUUID();
        UUID principalPublicId = UUID.randomUUID();
        return request
                .header("X-Gateway-Secret", GATEWAY_SECRET)
                .header("X-User-Id", accountId.toString())
                .header("X-Organization-Id", organizationId.toString())
                .header("X-Role", role)
                .header("X-Public-Id", principalPublicId.toString());
    }
}
