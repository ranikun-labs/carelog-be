package carelog.carelog.user.web;

import carelog.carelog.common.config.ClockConfig;
import carelog.carelog.common.config.SecurityConfig;
import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import carelog.carelog.auth.web.JwtAuthenticationEntryPoint;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.common.web.exception.GlobalExceptionHandler;
import carelog.carelog.user.app.ProductIdentityClaimsQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = IdentityClaimsController.class,
        properties = {
                "carelog.internal.identity-claims.enabled=true",
                "carelog.internal.identity-claims.service-token=test-platform-service-token",
                "gateway.internal-secret=test-gateway-internal-secret"
        }
)
@Import({
        SecurityConfig.class,
        ClockConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        IdentityClaimsInternalApiConfiguration.class,
        GlobalExceptionHandler.class
})
class IdentityClaimsControllerTest {

    private static final String SERVICE_TOKEN = "test-platform-service-token";
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORGANIZATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PUBLIC_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CLAIMS_PATH = "/internal/v1/identity/accounts/" + ACCOUNT_ID + "/claims";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductIdentityClaimsQueryService claimsQueryService;

    @Test
    @DisplayName("유효한 service token과 기존 MANAGER profile은 필요한 claim만 200으로 반환한다")
    void existingManager_returnsOnlyProductClaims() throws Exception {
        when(claimsQueryService.getClaims(ACCOUNT_ID)).thenReturn(
                new ProductIdentityClaimsQueryService.ProductIdentityClaims(
                        ORGANIZATION_ID, "MANAGER", PUBLIC_ID
                )
        );

        mockMvc.perform(get(CLAIMS_PATH).header("X-Platform-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID.toString()))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.publicId").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.role").value(org.hamcrest.Matchers.not("ROLE_MANAGER")))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.managerType").doesNotExist());
    }

    @Test
    @DisplayName("Product profile이 없으면 404로 응답하고 자동 생성하지 않는다")
    void missingProfile_returnsNotFound() throws Exception {
        when(claimsQueryService.getClaims(ACCOUNT_ID))
                .thenThrow(new CustomException(ExceptionStatus.USER_NOT_FOUND));

        mockMvc.perform(get(CLAIMS_PATH).header("X-Platform-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(ExceptionStatus.USER_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("Product query 내부 장애는 5xx와 안전한 메시지로 응답한다")
    void queryFailure_returnsSafeServerError() throws Exception {
        when(claimsQueryService.getClaims(ACCOUNT_ID))
                .thenThrow(new IllegalStateException("SQL password_hash detail"));

        mockMvc.perform(get(CLAIMS_PATH).header("X-Platform-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("서버에 예상치 못한 오류가 발생했습니다."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SQL password_hash detail"))));
    }

    @Test
    @DisplayName("service token이 없으면 401이고 Product query를 호출하지 않는다")
    void missingServiceToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(CLAIMS_PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(claimsQueryService);
    }

    @Test
    @DisplayName("service token이 다르면 401이고 Product query를 호출하지 않는다")
    void invalidServiceToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(CLAIMS_PATH).header("X-Platform-Service-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(claimsQueryService);
    }

    @Test
    @DisplayName("accountId가 UUID가 아니면 400으로 응답한다")
    void malformedAccountId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/internal/v1/identity/accounts/not-a-uuid/claims")
                        .header("X-Platform-Service-Token", SERVICE_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(claimsQueryService);
    }
}
