package carelog.carelog.common.config;

import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import carelog.carelog.auth.web.JwtAuthenticationEntryPoint;
import carelog.carelog.customer.app.CustomerService;
import carelog.carelog.customer.web.CustomerController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0019 §9/§17 불변식: {@code carelog.internal.identity-claims.enabled=false}일 때(기본값)
 * {@link InternalIdentitySecurityConfiguration은 아예 등록되지 않으므로, {@code /internal/**}에 대한
 * 유일한 차단 지점은 일반 Product {@link SecurityConfig} chain의 명시적 denyAll이다.
 * ({@code InternalIdentitySecurityConfiguration}은 여기서 import하지 않는다.)
 *
 * <p>이 slice는 오직 일반 chain만으로 차단됨을 증명하기 위한 것이다.
 */
@WebMvcTest(
        controllers = CustomerController.class,
        properties = "gateway.internal-secret=test-gateway-internal-secret"
)
@Import({
        SecurityConfig.class,
        ClockConfig.class,
        carelog.carelog.common.web.exception.GlobalExceptionHandler.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class SecurityConfigInternalPathDenialTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    @DisplayName("identity-claims가 disabled인 일반 chain만으로도 /internal/identity/claims/**는 403이다")
    void internalIdentityClaimsPath_isForbiddenByTheNormalChainAlone() throws Exception {
        mockMvc.perform(get("/internal/identity/claims/33333333-3333-3333-3333-333333333333"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("identity-claims가 disabled인 일반 chain만으로도 임의의 /internal/**는 403이다")
    void unrelatedInternalPath_isForbiddenByTheNormalChainAlone() throws Exception {
        mockMvc.perform(get("/internal/other")).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/other")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/internal/other")).andExpect(status().isForbidden());
    }
}
