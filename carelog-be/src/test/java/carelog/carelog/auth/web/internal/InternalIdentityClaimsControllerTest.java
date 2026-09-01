package carelog.carelog.auth.web.internal;

import carelog.carelog.auth.app.adapter.LegacyCrmIdentityProjectionAdapter;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0019 wire/failure 계약(§9, §12)을 고정하는 slice test.
 *
 * <p>{@code enabled=true}로 실제 {@link InternalIdentitySecurityConfiguration}을 조립해
 * 전용 SecurityFilterChain + 실제 {@link LegacyCrmIdentityProjectionAdapter}를 통해 검증한다.
 */
@WebMvcTest(
        controllers = InternalIdentityClaimsController.class,
        properties = {
                "carelog.internal.identity-claims.enabled=true",
                "carelog.internal.identity-claims.service-secret=test-platform-identity-service-secret-0123456789",
                "gateway.internal-secret=test-gateway-internal-secret"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        LegacyCrmIdentityProjectionAdapter.class,
        InternalIdentitySecurityConfiguration.class,
        InternalIdentityProjectionExceptionHandler.class
})
class InternalIdentityClaimsControllerTest {

    private static final String SERVICE_ID = "platform-identity";
    private static final String SERVICE_SECRET = "test-platform-identity-service-secret-0123456789";
    private static final UUID ORGANIZATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void resetRepository() {
        reset(userRepository);
    }

    @Test
    void activeAccount_returnsExactlyTheThreeApprovedClaims() throws Exception {
        User user = manager();
        when(userRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(user));

        mockMvc.perform(serviceGet(ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID.toString()))
                .andExpect(jsonPath("$.role").value(UserRole.MANAGER.name()))
                .andExpect(jsonPath("$.publicId").value(user.getPublicId().toString()))
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void absentAccount_isNotFound() throws Exception {
        when(userRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(serviceGet(ACCOUNT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void softDeletedAccount_isExcludedByTheSqlRestrictionAndIsNotFound() throws Exception {
        // User.@SQLRestriction("deleted_at IS NULL")로 인해 UserRepository.findByAccountId는
        // soft-deleted 행을 애초에 반환하지 않는다 — 인증 실패가 아니라 정상 projection 부재다.
        assertThat(User.class.getAnnotation(SQLRestriction.class).value())
                .isEqualTo("deleted_at IS NULL");
        when(userRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(serviceGet(ACCOUNT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingServiceCredential_isForbiddenAndNeverReachesTheProjection() throws Exception {
        mockMvc.perform(get("/internal/identity/claims/{accountId}", ACCOUNT_ID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    @Test
    void wrongServiceId_isForbidden() throws Exception {
        mockMvc.perform(get("/internal/identity/claims/{accountId}", ACCOUNT_ID)
                        .header("X-Service-Id", "not-platform-identity")
                        .header("X-Service-Secret", SERVICE_SECRET))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    @Test
    void wrongServiceSecret_isForbidden() throws Exception {
        mockMvc.perform(get("/internal/identity/claims/{accountId}", ACCOUNT_ID)
                        .header("X-Service-Id", SERVICE_ID)
                        .header("X-Service-Secret", "wrong-secret"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    @Test
    void userOrGatewayHeadersCannotSubstituteOrAugmentTheServicePrincipal() throws Exception {
        mockMvc.perform(get("/internal/identity/claims/{accountId}", ACCOUNT_ID)
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(serviceGet(ACCOUNT_ID)
                        .header("X-Gateway-Secret", "test-gateway-internal-secret")
                        .header("X-User-Id", ACCOUNT_ID.toString())
                        .header("X-Organization-Id", ORGANIZATION_ID.toString())
                        .header("X-Role", "MANAGER")
                        .header("X-Public-Id", ACCOUNT_ID.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    @Test
    void malformedAccountId_isBadRequestAfterServiceAuthentication() throws Exception {
        mockMvc.perform(serviceGet("not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userRepository);
    }

    @Test
    void projectionRuntimeFailure_isInternalServerError() throws Exception {
        when(userRepository.findByAccountId(ACCOUNT_ID))
                .thenThrow(new IllegalStateException("database unavailable"));

        mockMvc.perform(serviceGet(ACCOUNT_ID))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void wrongHttpMethodOnTheExactRoute_isForbidden() throws Exception {
        mockMvc.perform(post("/internal/identity/claims/{accountId}", ACCOUNT_ID)
                        .header("X-Service-Id", SERVICE_ID)
                        .header("X-Service-Secret", SERVICE_SECRET))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    @Test
    void unrelatedInternalPathsAreDeniedEvenWithValidServiceCredential() throws Exception {
        mockMvc.perform(get("/internal/other")
                        .header("X-Service-Id", SERVICE_ID)
                        .header("X-Service-Secret", SERVICE_SECRET))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/identity/claims/{accountId}/extra", ACCOUNT_ID)
                        .header("X-Service-Id", SERVICE_ID)
                        .header("X-Service-Secret", SERVICE_SECRET))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    @Test
    void oldPr44RouteAndItsControllerNoLongerExistOnTheClasspath() {
        assertThat(InternalIdentityClaimsController.class.getAnnotation(
                        org.springframework.web.bind.annotation.RequestMapping.class).value())
                .containsExactly("/internal/identity/claims");

        // 구 PR #44 boundary가 재도입되는 회귀를 컴파일 타임이 아니라 여기서도 명시적으로 잡는다.
        assertThat(classExists("carelog.carelog.user.web.IdentityClaimsController")).isFalse();
        assertThat(classExists("carelog.carelog.user.web.IdentityClaimsServiceTokenFilter")).isFalse();
        assertThat(classExists("carelog.carelog.user.web.IdentityClaimsInternalApiConfiguration")).isFalse();
        assertThat(classExists("carelog.carelog.user.app.ProductIdentityClaimsQueryService")).isFalse();
    }

    private boolean classExists(String fullyQualifiedName) {
        try {
            Class.forName(fullyQualifiedName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private MockHttpServletRequestBuilder serviceGet(UUID accountId) {
        return serviceGet(accountId.toString());
    }

    private MockHttpServletRequestBuilder serviceGet(String accountId) {
        return get("/internal/identity/claims/{accountId}", accountId)
                .header("X-Service-Id", SERVICE_ID)
                .header("X-Service-Secret", SERVICE_SECRET);
    }

    private User manager() {
        User user = User.builder()
                .userId("manager@example.com")
                .email("manager@example.com")
                .password("encoded-password")
                .name("Test Manager")
                .role(UserRole.MANAGER)
                .managerType(ManagerType.PHYSICAL_THERAPIST)
                .build();
        user.assignOrganization(ORGANIZATION_ID);
        user.assignAccountId(ACCOUNT_ID);
        return user;
    }
}
