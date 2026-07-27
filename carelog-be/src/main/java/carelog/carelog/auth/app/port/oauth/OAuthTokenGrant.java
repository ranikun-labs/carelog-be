package carelog.carelog.auth.app.port.oauth;

import java.time.Instant;

/** Provider token endpoint와 principal 조회 사이에서만 쓰이는 token grant다. */
public record OAuthTokenGrant(
        String accessToken,
        String idToken,
        Instant expiresAt
) {
}
