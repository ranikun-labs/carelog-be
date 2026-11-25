package carelog.carelog.auth.web.internal;

import carelog.carelog.auth.app.adapter.LegacyCrmIdentityProjectionAdapter;
import carelog.carelog.common.config.ClockConfig;
import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import carelog.carelog.common.web.exception.GlobalExceptionHandler;
import carelog.carelog.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code carelog.internal.claims-loopback-only.enabled}가 claims에만 독립적으로 작동함을 고정한다.
 * 기존 claims 계약(인증·projection)은 {@link InternalIdentityClaimsControllerTest}가 담당한다.
 */
@WebMvcTest(
        controllers = InternalIdentityClaimsController.class,
        properties = {
                "carelog.internal.identity-claims.enabled=true",
                "carelog.internal.identity-claims.service-secret=test-platform-identity-service-secret-0123456789",
                "carelog.internal.claims-loopback-only.enabled=true",
                "gateway.internal-secret=test-gateway-internal-secret"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        LegacyCrmIdentityProjectionAdapter.class,
        InternalIdentitySecurityConfiguration.class,
        InternalIdentityProjectionExceptionHandler.class,
        GlobalExceptionHandler.class,
        JwtAccessDeniedHandler.class,
        ClockConfig.class
})
class InternalIdentityClaimsLoopbackTest {

    private static final String SERVICE_ID = "platform-identity";
    private static final String SERVICE_SECRET = "test-platform-identity-service-secret-0123456789";
    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void claimsLoopbackOnly_deniesVpcPeerEvenWithValidCredential() throws Exception {
        mockMvc.perform(serviceGet(ACCOUNT_ID).with(remote("10.0.0.5")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(userRepository);
    }

    @Test
    void claimsLoopbackOnly_forwardedForSpoofDoesNotGrantAccess() throws Exception {
        mockMvc.perform(serviceGet(ACCOUNT_ID).with(remote("10.0.0.5"))
                        .header("X-Forwarded-For", "127.0.0.1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    @Test
    void claimsLoopbackOnly_loopbackPeerReachesExistingAuthAndProjection() throws Exception {
        when(userRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(serviceGet(ACCOUNT_ID).with(remote("127.0.0.1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimsLoopbackOnly_loopbackPeerWithWrongSecretStillForbidden() throws Exception {
        mockMvc.perform(get("/internal/identity/claims/{accountId}", ACCOUNT_ID)
                        .with(remote("127.0.0.1"))
                        .header("X-Service-Id", SERVICE_ID)
                        .header("X-Service-Secret", "wrong"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository);
    }

    private static MockHttpServletRequestBuilder serviceGet(UUID accountId) {
        return get("/internal/identity/claims/{accountId}", accountId)
                .header("X-Service-Id", SERVICE_ID)
                .header("X-Service-Secret", SERVICE_SECRET);
    }

    private static RequestPostProcessor remote(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }
}
