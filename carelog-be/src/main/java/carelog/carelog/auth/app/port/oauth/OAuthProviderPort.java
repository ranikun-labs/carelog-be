package carelog.carelog.auth.app.port.oauth;

import java.net.URI;

/** Provider SDK와 HTTP 구현을 OAuth Core 밖으로 격리하는 포트다. */
public interface OAuthProviderPort {

    String providerCode();

    default boolean supportsPkce() {
        return true;
    }

    /** ID Token 검증에 nonce가 필요한 Provider만 true를 반환한다. */
    default boolean requiresNonce() {
        return false;
    }

    URI buildAuthorizationUrl(OAuthAuthorizationRequest request);

    OAuthTokenGrant exchangeCode(String authorizationCode, URI redirectUri, String codeVerifier);

    OAuthPrincipal fetchPrincipal(OAuthTokenGrant grant, OAuthStateRecord state);
}
