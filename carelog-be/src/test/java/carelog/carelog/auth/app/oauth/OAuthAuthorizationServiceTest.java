package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.ClientChannel;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationCommand;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationRequest;
import carelog.carelog.auth.app.port.oauth.OAuthPrincipal;
import carelog.carelog.auth.app.port.oauth.OAuthProviderPort;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthStateStore;
import carelog.carelog.auth.app.port.oauth.OAuthTokenGrant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthAuthorizationServiceTest {

    @Test
    void 응답에는_authorizationUrl과_state만_있고_verifier는_서버_state에만_보관한다() {
        CapturingStateStore store = new CapturingStateStore();
        CapturingProvider provider = new CapturingProvider(false);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("oauth.redirect-uris.neutral.WEB", "https://app.example.com/callback")
                .withProperty("oauth.state.ttl", "5m");
        OAuthAuthorizationService service = new OAuthAuthorizationService(
                new OAuthProviderRegistry(List.of(provider)),
                new OAuthRedirectUriResolver(environment),
                new ReturnToValidator(List.of()),
                store,
                environment,
                Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC)
        );

        OAuthAuthorizationService.AuthorizationUrlResult result = service.startAuthorization(
                new OAuthAuthorizationCommand("NEUTRAL", ClientChannel.WEB, "/journals/42")
        );

        assertThat(result.authorizationUrl()).isEqualTo(URI.create("https://provider.example/authorize"));
        assertThat(result.state()).hasSize(43);
        assertThat(store.record.codeVerifier()).hasSize(43);
        assertThat(store.record.codeVerifier()).isNotEqualTo(result.state());
        assertThat(provider.request.codeChallenge()).isNotEqualTo(store.record.codeVerifier());
        assertThat(provider.request.redirectUri()).isEqualTo(store.record.redirectUri());
        assertThat(store.record.nonce()).isNull();
        assertThat(provider.request.nonce()).isNull();
        assertThat(OAuthAuthorizationService.AuthorizationUrlResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("authorizationUrl", "state");
    }

    @Test
    void nonce_필요_provider는_state와_authorization_request에_동일한_nonce를_받는다() {
        CapturingStateStore firstStore = new CapturingStateStore();
        CapturingStateStore secondStore = new CapturingStateStore();
        CapturingProvider firstProvider = new CapturingProvider(true);
        CapturingProvider secondProvider = new CapturingProvider(true);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("oauth.redirect-uris.neutral.WEB", "https://app.example.com/callback");

        service(firstProvider, firstStore, environment).startAuthorization(
                new OAuthAuthorizationCommand("neutral", ClientChannel.WEB, "/journals/42")
        );
        OAuthAuthorizationService.AuthorizationUrlResult secondResult = service(secondProvider, secondStore, environment)
                .startAuthorization(new OAuthAuthorizationCommand("neutral", ClientChannel.WEB, "/journals/42"));

        assertThat(firstStore.record.nonce()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(firstProvider.request.nonce()).isEqualTo(firstStore.record.nonce());
        assertThat(secondStore.record.nonce()).isNotEqualTo(firstStore.record.nonce());
        assertThat(OAuthAuthorizationService.AuthorizationUrlResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("authorizationUrl", "state");
        assertThat(secondResult.state()).isNotEqualTo(secondStore.record.nonce());
    }

    private OAuthAuthorizationService service(
            CapturingProvider provider, CapturingStateStore store, MockEnvironment environment
    ) {
        return new OAuthAuthorizationService(
                new OAuthProviderRegistry(List.of(provider)),
                new OAuthRedirectUriResolver(environment),
                new ReturnToValidator(List.of()),
                store,
                environment,
                Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static class CapturingStateStore implements OAuthStateStore {
        private OAuthStateRecord record;

        @Override
        public void save(String state, OAuthStateRecord record, Duration ttl) {
            this.record = record;
        }

        @Override
        public java.util.Optional<OAuthStateRecord> consume(String state) {
            return java.util.Optional.empty();
        }
    }

    private static class CapturingProvider implements OAuthProviderPort {
        private final boolean requiresNonce;
        private OAuthAuthorizationRequest request;

        private CapturingProvider(boolean requiresNonce) {
            this.requiresNonce = requiresNonce;
        }

        @Override public String providerCode() { return "neutral"; }
        @Override public boolean requiresNonce() { return requiresNonce; }
        @Override public URI buildAuthorizationUrl(OAuthAuthorizationRequest request) {
            this.request = request;
            return URI.create("https://provider.example/authorize");
        }
        @Override public OAuthTokenGrant exchangeCode(String authorizationCode, URI redirectUri, String codeVerifier) { return null; }
        @Override public OAuthPrincipal fetchPrincipal(OAuthTokenGrant grant, OAuthStateRecord state) { return null; }
    }
}
