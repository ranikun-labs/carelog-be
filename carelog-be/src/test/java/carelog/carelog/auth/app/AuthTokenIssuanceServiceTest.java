package carelog.carelog.auth.app;

import carelog.carelog.auth.app.port.AuthTokenBundle;
import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.TokenSessionPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenIssuanceServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenSessionPort tokenSessionPort;

    @Test
    void accountId와_claim으로_access_refresh를_발급하고_session을_교체한다() {
        AuthTokenIssuanceService service = new AuthTokenIssuanceService(jwtTokenProvider, tokenSessionPort);
        CRMIdentityClaims claims = new CRMIdentityClaims(ORGANIZATION_ID, "MANAGER", PUBLIC_ID);
        OffsetDateTime expiry = OffsetDateTime.parse("2026-08-10T00:00:00Z");
        when(jwtTokenProvider.generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, "MANAGER", PUBLIC_ID))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(ACCOUNT_ID)).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiryDate()).thenReturn(expiry);

        AuthTokenBundle result = service.issue(ACCOUNT_ID, claims);

        assertThat(result).isEqualTo(new AuthTokenBundle("access-token", "refresh-token"));
        InOrder inOrder = inOrder(jwtTokenProvider, tokenSessionPort);
        inOrder.verify(jwtTokenProvider).generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, "MANAGER", PUBLIC_ID);
        inOrder.verify(jwtTokenProvider).generateRefreshToken(ACCOUNT_ID);
        inOrder.verify(tokenSessionPort).replaceForAccount(ACCOUNT_ID, "refresh-token", expiry);
    }

    @Test
    void refresh_session_저장에_실패하면_토큰_결과를_반환하지_않고_예외를_전파한다() {
        AuthTokenIssuanceService service = new AuthTokenIssuanceService(jwtTokenProvider, tokenSessionPort);
        CRMIdentityClaims claims = new CRMIdentityClaims(ORGANIZATION_ID, "MANAGER", PUBLIC_ID);
        OffsetDateTime expiry = OffsetDateTime.parse("2026-08-10T00:00:00Z");
        when(jwtTokenProvider.generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, "MANAGER", PUBLIC_ID))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(ACCOUNT_ID)).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiryDate()).thenReturn(expiry);
        doThrow(new IllegalStateException("session persistence failed"))
                .when(tokenSessionPort).replaceForAccount(ACCOUNT_ID, "refresh-token", expiry);

        assertThatThrownBy(() -> service.issue(ACCOUNT_ID, claims))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("session persistence failed");
    }
}
