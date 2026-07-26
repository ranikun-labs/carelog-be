package carelog.carelog.auth.app;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import javax.crypto.*;
import java.lang.SecurityException;
import java.nio.charset.*;
import java.time.*;
import java.time.Clock;
import java.util.*;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${jwt.secret-key}") String secretKey,
            @Value("${jwt.access-token-validity}") long accessTokenValidity,
            @Value("${jwt.refresh-token-validity}") long refreshTokenValidity,
            Clock clock
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
        this.clock = clock;
    }

    /**
     * Access Token 생성. subject는 Identity Foundation B0부터 PlatformAccount.accountId다(과거 loginId 대체).
     */
    public String generateAccessToken(
            UUID accountId, UUID organizationId, String role, UUID publicId
    ) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(accountId.toString())
                .claim("organizationId", organizationId.toString())
                .claim("role", role)
                .claim("publicId", publicId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenValidity)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Refresh Token 생성. subject는 Access Token과 동일하게 accountId다.
     */
    public String generateRefreshToken(UUID accountId) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(accountId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshTokenValidity)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Token에서 accountId 추출. subject가 UUID 형식이 아니면(예: B0 전환 이전에 발급된 loginId subject
     * 토큰) {@link IllegalArgumentException}을 던진다 — 호출자가 기존 INVALID_REFRESH_TOKEN 계약으로 매핑한다.
     */
    public UUID getAccountIdFromToken(String token) {
        return UUID.fromString(getClaims(token).getSubject());
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Refresh Token의 만료 시간 계산
     */
    public OffsetDateTime getRefreshTokenExpiryDate() {
        return OffsetDateTime.now(clock).plus(Duration.ofMillis(refreshTokenValidity));
    }

    /**
     * 토큰에서 organizationId 추출 (Hibernate Filter 활성화용)
     */
    public UUID getOrganizationIdFromToken(String token) {
        return UUID.fromString(getClaims(token).get(
                "organizationId", String.class));
    }

    /**
     * 토큰에서 role 추출 (SCG 권한 체크용)
     */
    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * 토큰에서 publicId 추출 (FastAPI 연동용)
     */
    public UUID getPublicIdFromToken(String token) {
        return UUID.fromString(getClaims(token).get(
                "publicId", String.class));
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Duration getRemainingValidity(String token) {
        Date expiration = getClaims(token).getExpiration();

        long remaining = expiration.getTime() - Instant.now(clock).toEpochMilli();
        return Duration.ofMillis(Math.max(remaining, 0));
    }
}
