package carelog.carelog.auth.app;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider의 현재 claim/TTL/예외 계약을 고정하는 Characterization Test.
 * Phase 1A: docs/context/identity/auth-extraction-audit.md 기준.
 *
 * 테스트 secret은 characterization test 전용이며 실제 운영 환경 값과 무관하다.
 */
class JwtTokenProviderCharacterizationTest {

    private static final String TEST_SECRET =
            "characterization-test-only-secret-key-not-used-in-any-real-environment-0123456789";
    private static final long ACCESS_TOKEN_VALIDITY_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final long REFRESH_TOKEN_VALIDITY_MILLIS = Duration.ofDays(14).toMillis();

    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ROLE = "MANAGER";

    private Clock fixedClock;
    private JwtTokenProvider jwtTokenProvider;
    private SecretKey verificationKey;

    @BeforeEach
    void setUp() {
        // JJWT parser는 exp 검증 시 실제 시스템 시각을 기준으로 삼으므로, 파싱 검증이
        // 필요한 토큰은 실제 wall-clock보다 충분히 미래인 고정 시각으로 발급해야 한다.
        fixedClock = Clock.fixed(Instant.parse("2099-01-01T00:00:00Z"), ZoneOffset.UTC);
        jwtTokenProvider = new JwtTokenProvider(
                TEST_SECRET, ACCESS_TOKEN_VALIDITY_MILLIS, REFRESH_TOKEN_VALIDITY_MILLIS, fixedClock
        );
        verificationKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    // 7.1 Access Token claims와 TTL
    @DisplayName("Access Token은 subject=accountId, claim(organizationId/role/publicId)과 설정된 TTL을 담고 managerType은 포함하지 않는다")
    @Test
    void accessToken_containsCurrentClaimsAndConfiguredLifetime() {
        String accessToken = jwtTokenProvider.generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, ROLE, PUBLIC_ID);

        Claims claims = Jwts.parser().verifyWith(verificationKey).build()
                .parseSignedClaims(accessToken).getPayload();

        assertThat(claims.getSubject()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(claims.get("organizationId", String.class)).isEqualTo(ORGANIZATION_ID.toString());
        assertThat(claims.get("role", String.class)).isEqualTo(ROLE);
        assertThat(claims.get("publicId", String.class)).isEqualTo(PUBLIC_ID.toString());
        assertThat(claims.get("managerType")).isNull();
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isEqualTo(ACCESS_TOKEN_VALIDITY_MILLIS);
    }

    // 7.2 Refresh Token의 최소 claims와 TTL
    @DisplayName("Refresh Token은 subject(accountId)만 담고 organizationId/role/publicId 없이 설정된 TTL을 갖는다")
    @Test
    void refreshToken_containsOnlySubjectAndConfiguredLifetime() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(ACCOUNT_ID);

        Claims claims = Jwts.parser().verifyWith(verificationKey).build()
                .parseSignedClaims(refreshToken).getPayload();

        assertThat(claims.getSubject()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isEqualTo(REFRESH_TOKEN_VALIDITY_MILLIS);
        assertThat(claims.get("organizationId")).isNull();
        assertThat(claims.get("role")).isNull();
        assertThat(claims.get("publicId")).isNull();
    }

    // 7.3 만료 Token remaining validity
    @DisplayName("만료된 Token의 remaining validity 조회는 현재 구현대로 ExpiredJwtException을 전파한다")
    @Test
    void remainingValidity_ofExpiredToken_propagatesJwtExpiryException() {
        // JJWT parser는 검증 시 실제 시스템 시각을 기준으로 exp를 판정하므로,
        // 발급 시각을 먼 과거로 고정해 실시간 기준으로 확실히 만료된 토큰을 만든다.
        Clock farPastClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider expiredTokenIssuer = new JwtTokenProvider(
                TEST_SECRET, 1_000L, REFRESH_TOKEN_VALIDITY_MILLIS, farPastClock
        );
        String expiredAccessToken =
                expiredTokenIssuer.generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, ROLE, PUBLIC_ID);

        assertThatThrownBy(() -> jwtTokenProvider.getRemainingValidity(expiredAccessToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    // 7.4 accountId 추출 (B0)
    @DisplayName("getAccountIdFromToken은 발급 시 사용한 accountId를 그대로 되돌려준다")
    @Test
    void getAccountIdFromToken_roundTripsIssuedAccountId() {
        String accessToken = jwtTokenProvider.generateAccessToken(ACCOUNT_ID, ORGANIZATION_ID, ROLE, PUBLIC_ID);
        String refreshToken = jwtTokenProvider.generateRefreshToken(ACCOUNT_ID);

        assertThat(jwtTokenProvider.getAccountIdFromToken(accessToken)).isEqualTo(ACCOUNT_ID);
        assertThat(jwtTokenProvider.getAccountIdFromToken(refreshToken)).isEqualTo(ACCOUNT_ID);
    }

    // 7.5 B0 cut-over: subject가 UUID가 아닌 legacy(loginId) 토큰
    @DisplayName("subject가 UUID 형식이 아닌 legacy 토큰은 getAccountIdFromToken에서 IllegalArgumentException을 던진다")
    @Test
    void getAccountIdFromToken_legacyLoginIdSubject_throwsIllegalArgumentException() {
        String legacySubjectToken = Jwts.builder()
                .subject("manager@example.com")
                .issuedAt(java.util.Date.from(Instant.now(fixedClock)))
                .expiration(java.util.Date.from(Instant.now(fixedClock).plusSeconds(60)))
                .signWith(verificationKey)
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.getAccountIdFromToken(legacySubjectToken))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
