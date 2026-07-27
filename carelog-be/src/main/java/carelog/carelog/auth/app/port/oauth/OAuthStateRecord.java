package carelog.carelog.auth.app.port.oauth;

import java.net.URI;
import java.time.Instant;

/** 서버 Redis에만 보관되는 OAuth state의 민감한 연결 정보다. */
public record OAuthStateRecord(
        String provider,
        URI redirectUri,
        String returnTo,
        String codeVerifier,
        String nonce,
        Instant issuedAt
) {
}
