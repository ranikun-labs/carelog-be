package carelog.carelog.auth.app;

import carelog.carelog.auth.domain.RefreshToken;
import carelog.carelog.auth.domain.RefreshTokenRepository;
import carelog.carelog.auth.web.dto.request.LoginRequest;
import carelog.carelog.auth.web.dto.request.TokenRefreshRequest;
import carelog.carelog.auth.web.dto.response.LoginResponse;
import carelog.carelog.auth.web.dto.response.TokenRefreshResponse;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

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
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl의 현재 구현 계약을 고정하는 Characterization Test.
 * Phase 1A: docs/context/identity/auth-extraction-audit.md 기준.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplCharacterizationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
    private static final String USER_ID = "manager@example.com";
    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private UserRepository userRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private RedisBlacklistService redisBlacklistService;
    @Mock private Authentication authentication;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, authenticationManager, jwtTokenProvider,
                refreshTokenRepository, redisBlacklistService, FIXED_CLOCK
        );
    }

    private User newManagerUser(String userId) {
        User user = User.builder()
                .userId(userId)
                .email(userId)
                .password("encoded-password")
                .name("Test Manager")
                .role(UserRole.MANAGER)
                .managerType(ManagerType.PHYSICAL_THERAPIST)
                .build();
        user.assignOrganization(ORGANIZATION_ID);
        return user;
    }

    private RefreshToken newRefreshToken(String userId, String token, OffsetDateTime expiresAt) {
        return RefreshToken.builder()
                .userId(userId)
                .refreshToken(token)
                .tokenExpiresAt(expiresAt)
                .build();
    }

    // 5.1 로그인 성공
    @DisplayName("로그인 성공 시 기존 Refresh Token을 삭제한 뒤 신규 토큰을 저장하고 두 토큰을 반환한다")
    @Test
    void login_success_replacesStoredRefreshTokenAndReturnsBothTokens() {
        User user = newManagerUser(USER_ID);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        LoginRequest request = new LoginRequest(USER_ID, "raw-password");

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtTokenProvider.generateAccessToken(USER_ID, ORGANIZATION_ID, UserRole.MANAGER.name(), user.getPublicId()))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(USER_ID)).thenReturn("refresh-token");
        OffsetDateTime newExpiry = OffsetDateTime.now(FIXED_CLOCK).plusDays(14);
        when(jwtTokenProvider.getRefreshTokenExpiryDate()).thenReturn(newExpiry);

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        InOrder inOrder = inOrder(refreshTokenRepository);
        inOrder.verify(refreshTokenRepository).deleteByUserId(USER_ID);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        inOrder.verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(saved.getTokenExpiresAt()).isEqualTo(newExpiry);
    }

    // 5.2 인증 실패
    @DisplayName("인증 실패 시 모든 AuthenticationException을 INVALID_CREDENTIALS로 변환하고 토큰을 발급하지 않는다")
    @Test
    void login_authenticationFailure_mapsToInvalidCredentialsWithoutIssuingTokens() {
        LoginRequest request = new LoginRequest(USER_ID, "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_CREDENTIALS);

        verifyNoInteractions(jwtTokenProvider);
        verify(refreshTokenRepository, never()).deleteByUserId(anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    // 5.3 유효하지 않은 JWT Refresh Token
    @DisplayName("Refresh Token JWT 자체가 유효하지 않으면 DB 조회 전에 INVALID_REFRESH_TOKEN을 던진다")
    @Test
    void refresh_invalidJwt_throwsInvalidRefreshTokenBeforeRepositoryLookup() {
        String refreshToken = "invalid-refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_REFRESH_TOKEN);

        verifyNoInteractions(refreshTokenRepository);
        verifyNoInteractions(userRepository);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
    }

    // 5.4 DB에 Refresh Token 없음
    @DisplayName("JWT는 유효하지만 DB에 Refresh Token이 없으면 REFRESH_TOKEN_NOT_FOUND를 던진다")
    @Test
    void refresh_missingPersistedToken_throwsRefreshTokenNotFound() {
        String refreshToken = "refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(USER_ID);
        when(refreshTokenRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.REFRESH_TOKEN_NOT_FOUND);

        verifyNoInteractions(userRepository);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
    }

    // 5.5 DB Refresh Token 만료
    @DisplayName("DB의 Refresh Token이 만료되었으면 해당 row를 삭제한 뒤 REFRESH_TOKEN_EXPIRED를 던진다")
    @Test
    void refresh_expiredPersistedToken_deletesItThenThrowsExpired() {
        String refreshToken = "refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        RefreshToken saved = newRefreshToken(USER_ID, refreshToken, OffsetDateTime.now(FIXED_CLOCK).minusSeconds(1));

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(USER_ID);
        when(refreshTokenRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.REFRESH_TOKEN_EXPIRED);

        verify(refreshTokenRepository).delete(saved);
        verifyNoInteractions(userRepository);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
    }

    // 5.6 JWT subject와 저장 사용자 불일치
    @DisplayName("JWT subject와 저장된 Refresh Token의 userId가 다르면 User 조회 없이 INVALID_REFRESH_TOKEN을 던진다")
    @Test
    void refresh_subjectMismatch_throwsInvalidRefreshTokenWithoutUserLookup() {
        String refreshToken = "refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        RefreshToken saved = newRefreshToken(
                "other-user@example.com", refreshToken, OffsetDateTime.now(FIXED_CLOCK).plusDays(1)
        );

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(USER_ID);
        when(refreshTokenRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_REFRESH_TOKEN);

        verifyNoInteractions(userRepository);
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateRefreshToken(any());
        verify(refreshTokenRepository, never()).delete(any());
        assertThat(saved.getRefreshToken()).isEqualTo(refreshToken);
    }

    // 5.7 Refresh 성공
    @DisplayName("Refresh 성공 시 두 토큰을 재발급하고 기존 RefreshToken Entity를 dirty checking으로 갱신한다")
    @Test
    void refresh_success_rotatesBothTokensAndMutatesPersistedToken() {
        String refreshToken = "refresh-token";
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        RefreshToken saved = newRefreshToken(USER_ID, refreshToken, OffsetDateTime.now(FIXED_CLOCK).plusDays(1));
        User user = newManagerUser(USER_ID);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(USER_ID);
        when(refreshTokenRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(saved));
        when(userRepository.findByUserId(USER_ID)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(USER_ID, ORGANIZATION_ID, UserRole.MANAGER.name(), user.getPublicId()))
                .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(USER_ID)).thenReturn("new-refresh-token");
        OffsetDateTime newExpiry = OffsetDateTime.now(FIXED_CLOCK).plusDays(14);
        when(jwtTokenProvider.getRefreshTokenExpiryDate()).thenReturn(newExpiry);

        TokenRefreshResponse response = authService.refreshToken(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(saved.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(saved.getTokenExpiresAt()).isEqualTo(newExpiry);
    }

    // 5.8 Logout 성공
    @DisplayName("Logout 성공 시 Access Token을 provider가 계산한 TTL로 blacklist에 등록한 뒤 Refresh Token을 삭제한다")
    @Test
    void logout_success_blacklistsAccessTokenWithProviderTtlAndDeletesRefreshToken() {
        String accessToken = "access-token";
        Duration ttl = Duration.ofMinutes(5);
        when(jwtTokenProvider.getRemainingValidity(accessToken)).thenReturn(ttl);

        authService.logout(USER_ID, accessToken);

        InOrder inOrder = inOrder(redisBlacklistService, refreshTokenRepository);
        inOrder.verify(redisBlacklistService).addToBlacklist(accessToken, ttl);
        inOrder.verify(refreshTokenRepository).deleteByUserId(USER_ID);
    }

    // 5.9 Logout blacklist 실패
    @DisplayName("Redis blacklist 등록이 실패하면 예외가 그대로 전파되고 Refresh Token은 삭제되지 않는다")
    @Test
    void logout_blacklistFailure_propagatesAndDoesNotDeleteRefreshToken() {
        String accessToken = "access-token";
        Duration ttl = Duration.ofMinutes(5);
        when(jwtTokenProvider.getRemainingValidity(accessToken)).thenReturn(ttl);
        doThrow(new RuntimeException("redis down"))
                .when(redisBlacklistService).addToBlacklist(accessToken, ttl);

        assertThatThrownBy(() -> authService.logout(USER_ID, accessToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis down");

        verify(refreshTokenRepository, never()).deleteByUserId(anyString());
    }
}
