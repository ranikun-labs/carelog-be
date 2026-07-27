package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationCommand;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationRequest;
import carelog.carelog.auth.app.port.oauth.OAuthProviderPort;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthStateStore;
import carelog.carelog.auth.app.port.oauth.PkceChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** OAuth authorization 시작에 필요한 state와 PKCE를 서버 측에서 생성·보관한다. */
@Service
@RequiredArgsConstructor
public class OAuthAuthorizationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthProviderRegistry providerRegistry;
    private final OAuthRedirectUriResolver redirectUriResolver;
    private final ReturnToValidator returnToValidator;
    private final OAuthStateStore stateStore;
    private final Environment environment;
    private final Clock clock;

    public AuthorizationUrlResult startAuthorization(OAuthAuthorizationCommand command) {
        OAuthProviderPort provider = providerRegistry.resolve(command.provider());
        String providerCode = OAuthProviderRegistry.normalize(provider.providerCode());
        URI redirectUri = redirectUriResolver.resolve(providerCode, command.clientChannel());
        String returnTo = returnToValidator.validate(command.returnTo());
        String state = generateState();
        PkceChallenge pkce = PkceChallenge.generate();
        String nonce = provider.requiresNonce() ? generateRandomValue() : null;

        OAuthStateRecord record = new OAuthStateRecord(
                providerCode,
                redirectUri,
                returnTo,
                pkce.codeVerifier(),
                nonce,
                Instant.now(clock)
        );
        stateStore.save(state, record, stateTtl());

        URI authorizationUrl = provider.buildAuthorizationUrl(new OAuthAuthorizationRequest(
                providerCode,
                redirectUri,
                state,
                pkce.codeChallenge(),
                pkce.method(),
                nonce
        ));
        return new AuthorizationUrlResult(authorizationUrl, state);
    }

    private String generateState() {
        return generateRandomValue();
    }

    private String generateRandomValue() {
        byte[] stateBytes = new byte[32];
        SECURE_RANDOM.nextBytes(stateBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
    }

    private Duration stateTtl() {
        return DurationStyle.detectAndParse(environment.getProperty("oauth.state.ttl", "5m"));
    }

    /** API 응답으로 안전하게 노출 가능한 authorization 시작 결과다. */
    public record AuthorizationUrlResult(URI authorizationUrl, String state) {
    }
}
