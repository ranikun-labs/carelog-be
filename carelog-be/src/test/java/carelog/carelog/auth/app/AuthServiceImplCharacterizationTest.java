package carelog.carelog.auth.app;

import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.auth.app.port.CredentialPort;
import carelog.carelog.auth.app.port.RefreshSession;
import carelog.carelog.auth.app.port.TokenBlacklistPort;
import carelog.carelog.auth.app.port.TokenSessionPort;
import carelog.carelog.auth.web.dto.request.LoginRequest;
import carelog.carelog.auth.web.dto.request.TokenRefreshRequest;
import carelog.carelog.auth.web.dto.response.LoginResponse;
import carelog.carelog.auth.web.dto.response.TokenRefreshResponse;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl의 현재 구현 계약을 고정하는 Characterization Test.
 *
 * <p>Phase 3A-3: AuthServiceImpl이 CRM Repository/Concrete Principal 대신
 * Identity 경계 Port(CredentialPort/CRMIdentityProjectionPort/TokenSessionPort)에만
 * 의존하도록 전환됐다. 이 테스트는 Port 전환 전후의 Login/Refresh/Logout 행동 동등성을 고정한다.
 * (Phase 1A 기준: docs/context/identity/auth-extraction-audit.md)
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplCharacterizationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String USER_ID = "manager@example.com";
    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    // CRM UserRole.MANAGER.name()과 동일한 문자열. 경계 밖 CRM enum을 import하지 않는다.
    private static final String ROLE = "MANAGER";

    @Mock private CredentialPort credentialPort;
    @Mock private CRMIdentityProjectionPort crmIdentityProjectionPort;
    @Mock private TokenSessionPort tokenSessionPort;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenBlacklistPort tokenBlacklistPort;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                credentialPort, crmIdentityProjectionPort, tokenSessionPort,
                jwtTokenProvider, tokenBlacklistPort, FIXED_CLOCK
        );
    }

    private UserPrincipal principal(UUID accountId, String userId, UUID organizationId, String role, UUID publicId) {
        return new UserPrincipal() {
            @Override public UUID getAccountId() { return accountId; }
            @Override public String getUserId() { return userId; }
            @Override public UUID getOrganizationId() { return organizationId; }
            @Override public String getRole() { return role; }
            @Override public UUID getPublicId() { return publicId; }
        };
    }

    private RefreshSession newSession(UUID accountId, String token, OffsetDateTime expiresAt) {
        return new RefreshSession(token, accountId, expiresAt);
    }

    // 5.1 로그인 성공
    @DisplayName("로그인 성공 시 인증 → 토큰 생성 → Refresh Session 교체 순서로 진행하고 두 토큰을 반환한다")
    @Test
    void login_success_authenticatesThenIssuesTokensThenReplacesSession() {
        LoginRequest request = new LoginRequest(USER_ID, "raw-password");
        UserPrincipal userPrincipal = principal(ACCOUNT_ID, USER_ID, ORGANIZATION_ID, ROLE, PUBLIC_ID);

        when(credentialPort.authenticate(USER_ID, "raw-password")).thenReturn(userPrincipal);
        when(jwtTokenProvider.generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, ROLE, PUBLIC_ID))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(ACCOUNT_ID)).thenReturn("refresh-token");
        OffsetDateTime newExpiry = OffsetDateTime.now(FIXED_CLOCK).plusDays(14);
        when(jwtTokenProvider.getRefreshTokenExpiryDate()).thenReturn(newExpiry);

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        // 인증 → Access/Refresh 생성 → Session 교체 순서 보존
        InOrder inOrder = inOrder(credentialPort, jwtTokenProvider, tokenSessionPort);
        inOrder.verify(credentialPort).authenticate(USER_ID, "raw-password");
        inOrder.verify(jwtTokenProvider).generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, ROLE, PUBLIC_ID);
        inOrder.verify(jwtTokenProvider).generateRefreshToken(ACCOUNT_ID);
        inOrder.verify(tokenSessionPort).replaceForAccount(ACCOUNT_ID, "refresh-token", newExpiry);

        verifyNoInteractions(crmIdentityProjectionPort);
    }

    // 5.2 인증 실패
    @DisplayName("인증 실패 시 Port가 던진 INVALID_CREDENTIALS를 그대로 전파하고 이후 토큰/세션 부수효과가 없다")
    @Test
    void login_authenticationFailure_propagatesInvalidCredentialsWithoutSideEffects() {
        LoginRequest request = new LoginRequest(USER_ID, "wrong-password");
        when(credentialPort.authenticate(USER_ID, "wrong-password"))
                .thenThrow(new CustomException(ExceptionStatus.INVALID_CREDENTIALS));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_CREDENTIALS);

        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(crmIdentityProjectionPort);
        verify(tokenSessionPort, never()).replaceForAccount(any(), anyString(), any());
    }

    // 5.3 유효하지 않은 JWT Refresh Token
    @DisplayName("Refresh Token JWT 자체가 유효하지 않으면 Session 조회 전에 INVALID_REFRESH_TOKEN을 던진다")
    @Test
    void refresh_invalidJwt_throwsInvalidRefreshTokenBeforeSessionLookup() {
        String refreshToken = "invalid-refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_REFRESH_TOKEN);

        verifyNoInteractions(tokenSessionPort);
        verifyNoInteractions(crmIdentityProjectionPort);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
    }

    // 5.3b subject가 UUID가 아닌 legacy Refresh Token (cut-over 이전 loginId subject)
    @DisplayName("subject가 accountId(UUID) 형식이 아니면 Session 조회 전에 INVALID_REFRESH_TOKEN을 던진다")
    @Test
    void refresh_nonUuidSubject_throwsInvalidRefreshTokenBeforeSessionLookup() {
        String refreshToken = "legacy-refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getAccountIdFromToken(refreshToken))
                .thenThrow(new IllegalArgumentException("Invalid UUID string: manager@example.com"));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_REFRESH_TOKEN);

        verifyNoInteractions(tokenSessionPort);
        verifyNoInteractions(crmIdentityProjectionPort);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
    }

    // 5.4 저장된 Session 없음
    @DisplayName("JWT는 유효하지만 저장된 Session이 없으면 REFRESH_TOKEN_NOT_FOUND를 던진다")
    @Test
    void refresh_missingPersistedSession_throwsRefreshTokenNotFound() {
        String refreshToken = "refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getAccountIdFromToken(refreshToken)).thenReturn(ACCOUNT_ID);
        when(tokenSessionPort.findByToken(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.REFRESH_TOKEN_NOT_FOUND);

        verifyNoInteractions(crmIdentityProjectionPort);
        verify(tokenSessionPort, never()).delete(any());
        verify(tokenSessionPort, never()).rotate(any(), any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
    }

    // 5.5 저장된 Session 만료
    @DisplayName("저장된 Session이 만료되었으면 해당 Session을 삭제한 뒤 REFRESH_TOKEN_EXPIRED를 던진다")
    @Test
    void refresh_expiredPersistedSession_deletesItThenThrowsExpired() {
        String refreshToken = "refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        RefreshSession session = newSession(ACCOUNT_ID, refreshToken, OffsetDateTime.now(FIXED_CLOCK).minusSeconds(1));

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getAccountIdFromToken(refreshToken)).thenReturn(ACCOUNT_ID);
        when(tokenSessionPort.findByToken(refreshToken)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.REFRESH_TOKEN_EXPIRED);

        // 만료 시 조회한 바로 그 Session을 삭제 (삭제 순서 보존)
        verify(tokenSessionPort).delete(session);
        verifyNoInteractions(crmIdentityProjectionPort);
        verify(tokenSessionPort, never()).rotate(any(), any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
    }

    // 5.6 JWT subject와 저장 사용자 불일치
    @DisplayName("JWT subject와 저장된 Session의 accountId가 다르면 CRM claim 조회 없이 INVALID_REFRESH_TOKEN을 던진다")
    @Test
    void refresh_subjectMismatch_throwsInvalidRefreshTokenWithoutClaimLookup() {
        String refreshToken = "refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        UUID otherAccountId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        RefreshSession session = newSession(
                otherAccountId, refreshToken, OffsetDateTime.now(FIXED_CLOCK).plusDays(1)
        );

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getAccountIdFromToken(refreshToken)).thenReturn(ACCOUNT_ID);
        when(tokenSessionPort.findByToken(refreshToken)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_REFRESH_TOKEN);

        verifyNoInteractions(crmIdentityProjectionPort);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
        // 만료가 아니므로 삭제/회전 모두 없어야 한다
        verify(tokenSessionPort, never()).delete(any());
        verify(tokenSessionPort, never()).rotate(any(), any(), any());
    }

    // 5.7 Refresh 성공
    @DisplayName("Refresh 성공 시 최신 CRM claim으로 두 토큰을 재발급하고 기존 Session을 그대로 rotate에 전달한다")
    @Test
    void refresh_success_rotatesBothTokensWithLatestClaimsAndOriginalSession() {
        String refreshToken = "refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        RefreshSession session = newSession(ACCOUNT_ID, refreshToken, OffsetDateTime.now(FIXED_CLOCK).plusDays(1));

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getAccountIdFromToken(refreshToken)).thenReturn(ACCOUNT_ID);
        when(tokenSessionPort.findByToken(refreshToken)).thenReturn(Optional.of(session));
        when(crmIdentityProjectionPort.getIdentityClaims(ACCOUNT_ID))
                .thenReturn(new CRMIdentityClaims(ORGANIZATION_ID, ROLE, PUBLIC_ID));
        when(jwtTokenProvider.generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, ROLE, PUBLIC_ID))
                .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(ACCOUNT_ID)).thenReturn("new-refresh-token");
        OffsetDateTime newExpiry = OffsetDateTime.now(FIXED_CLOCK).plusDays(14);
        when(jwtTokenProvider.getRefreshTokenExpiryDate()).thenReturn(newExpiry);

        TokenRefreshResponse response = authService.refreshToken(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");

        // 조회 → CRM claim 재조회 → 토큰 생성 → rotate 순서 보존
        InOrder inOrder = inOrder(tokenSessionPort, crmIdentityProjectionPort, jwtTokenProvider);
        inOrder.verify(tokenSessionPort).findByToken(refreshToken);
        inOrder.verify(crmIdentityProjectionPort).getIdentityClaims(ACCOUNT_ID);
        inOrder.verify(jwtTokenProvider).generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, ROLE, PUBLIC_ID);
        inOrder.verify(jwtTokenProvider).generateRefreshToken(ACCOUNT_ID);

        // rotate에는 findByToken이 반환한 바로 그 Session 인스턴스가 전달되어야 한다
        ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
        inOrder.verify(tokenSessionPort).rotate(sessionCaptor.capture(), eq("new-refresh-token"), eq(newExpiry));
        assertThat(sessionCaptor.getValue()).isSameAs(session);
        // rotate 입력 Session의 tokenValue는 기존 토큰이어야 한다
        assertThat(sessionCaptor.getValue().tokenValue()).isEqualTo(refreshToken);

        verify(tokenSessionPort, never()).delete(any());
    }

    // 5.8 Logout 성공
    @DisplayName("Logout 성공 시 Access Token을 provider가 계산한 TTL로 blacklist에 등록한 뒤 Refresh Session을 삭제한다")
    @Test
    void logout_success_blacklistsAccessTokenWithProviderTtlThenDeletesSession() {
        String accessToken = "access-token";
        Duration ttl = Duration.ofMinutes(5);
        when(jwtTokenProvider.getRemainingValidity(accessToken)).thenReturn(ttl);

        authService.logout(ACCOUNT_ID, accessToken);

        InOrder inOrder = inOrder(tokenBlacklistPort, tokenSessionPort);
        inOrder.verify(tokenBlacklistPort).addToBlacklist(accessToken, ttl);
        inOrder.verify(tokenSessionPort).deleteForAccount(ACCOUNT_ID);
    }

    // 5.9 Logout blacklist 실패
    @DisplayName("Redis blacklist 등록이 실패하면 예외가 그대로 전파되고 Refresh Session은 삭제되지 않는다")
    @Test
    void logout_blacklistFailure_propagatesAndDoesNotDeleteSession() {
        String accessToken = "access-token";
        Duration ttl = Duration.ofMinutes(5);
        when(jwtTokenProvider.getRemainingValidity(accessToken)).thenReturn(ttl);
        doThrow(new RuntimeException("redis down"))
                .when(tokenBlacklistPort).addToBlacklist(accessToken, ttl);

        assertThatThrownBy(() -> authService.logout(ACCOUNT_ID, accessToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis down");

        verify(tokenSessionPort, never()).deleteForAccount(any());
    }
}
