package carelog.carelog.auth.app.port.oauth;

import java.net.URI;

/** Provider adapter에만 전달되는 authorization URL 생성 입력이다. */
public record OAuthAuthorizationRequest(
        String provider,
        URI redirectUri,
        String state,
        String codeChallenge,
        String codeChallengeMethod,
        String nonce
) {
}
