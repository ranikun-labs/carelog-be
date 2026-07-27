package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationRequest;
import carelog.carelog.auth.app.port.oauth.OAuthPrincipal;
import carelog.carelog.auth.app.port.oauth.OAuthProviderPort;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthTokenGrant;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthProviderRegistryTest {

    @Test
    void provider_code를_소문자로_정규화해_해결한다() {
        OAuthProviderPort provider = provider("Neutral");
        OAuthProviderRegistry registry = new OAuthProviderRegistry(List.of(provider));

        assertThat(registry.resolve(" NEUTRAL ")).isSameAs(provider);
    }

    @Test
    void 미등록_provider는_명시적으로_거부한다() {
        OAuthProviderRegistry registry = new OAuthProviderRegistry(List.of());

        assertThatThrownBy(() -> registry.resolve("missing"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.UNSUPPORTED_OAUTH_PROVIDER);
    }

    static OAuthProviderPort provider(String code) {
        return new OAuthProviderPort() {
            @Override public String providerCode() { return code; }
            @Override public URI buildAuthorizationUrl(OAuthAuthorizationRequest request) { return URI.create("https://provider.example/authorize"); }
            @Override public OAuthTokenGrant exchangeCode(String authorizationCode, URI redirectUri, String codeVerifier) { return null; }
            @Override public OAuthPrincipal fetchPrincipal(OAuthTokenGrant grant, OAuthStateRecord state) { return null; }
        };
    }
}
