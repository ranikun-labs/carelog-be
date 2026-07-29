package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.ClientChannel;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationCommand;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationRequest;
import carelog.carelog.auth.app.port.oauth.OAuthPrincipal;
import carelog.carelog.auth.app.port.oauth.OAuthProviderPort;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthStateStore;
import carelog.carelog.auth.app.port.oauth.OAuthTokenGrant;
import carelog.carelog.auth.app.port.productclient.ProductClientReader;
import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
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
                defaultClientResolver(),
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

    @Test
    void PKCE를_지원하지_않는_provider에는_verifier와_challenge를_전달하지_않는다() {
        CapturingStateStore store = new CapturingStateStore();
        CapturingProvider provider = new CapturingProvider(false, false);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("oauth.redirect-uris.neutral.WEB", "https://app.example.com/callback");

        service(provider, store, environment).startAuthorization(
                new OAuthAuthorizationCommand("neutral", ClientChannel.WEB, "/")
        );

        assertThat(store.record.codeVerifier()).isNull();
        assertThat(provider.request.codeChallenge()).isNull();
        assertThat(provider.request.codeChallengeMethod()).isNull();
    }

    @Test
    void 기존_MOBILE_요청은_MOBILE_기본_Client_검증_후_기존_Redirect를_선택한다() {
        CapturingStateStore store = new CapturingStateStore();
        CapturingProvider provider = new CapturingProvider(false);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("oauth.redirect-uris.neutral.MOBILE", "carelog://oauth/callback")
                .withProperty("oauth.state.ttl", "5m");

        service(provider, store, environment).startAuthorization(
                new OAuthAuthorizationCommand("neutral", ClientChannel.MOBILE, "/")
        );

        assertThat(provider.request.redirectUri()).isEqualTo(URI.create("carelog://oauth/callback"));
        assertThat(store.record.redirectUri()).isEqualTo(URI.create("carelog://oauth/callback"));
    }

    private OAuthAuthorizationService service(
            CapturingProvider provider, CapturingStateStore store, MockEnvironment environment
    ) {
        return new OAuthAuthorizationService(
                new OAuthProviderRegistry(List.of(provider)),
                new OAuthRedirectUriResolver(environment),
                defaultClientResolver(),
                new ReturnToValidator(List.of()),
                store,
                environment,
                Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void 알수없는_Product_Client는_Provider를_선택하기_전에_거부한다() {
        CapturingProvider provider = new CapturingProvider(false);
        ProductClientReader unknownClient = clientId -> {
            throw new CustomException(ExceptionStatus.UNKNOWN_PRODUCT_CLIENT);
        };
        OAuthAuthorizationService service = new OAuthAuthorizationService(
                new OAuthProviderRegistry(List.of(provider)),
                new OAuthRedirectUriResolver(new MockEnvironment().withProperty(
                        "oauth.redirect-uris.neutral.WEB", "https://app.example.com/callback")),
                new OAuthProductClientCompatibilityResolver(unknownClient),
                new ReturnToValidator(List.of()),
                new CapturingStateStore(),
                new MockEnvironment(),
                Clock.systemUTC()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.startAuthorization(
                new OAuthAuthorizationCommand("neutral", ClientChannel.WEB, "/", "unknown-client")
        )).isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.UNKNOWN_PRODUCT_CLIENT);
        assertThat(provider.request).isNull();
    }

    @Test
    void 비활성_Product_Client는_Provider를_선택하기_전에_거부한다() {
        CapturingProvider provider = new CapturingProvider(false);
        ProductClientReader disabledClient = clientId -> {
            throw new CustomException(ExceptionStatus.DISABLED_PRODUCT_CLIENT);
        };
        OAuthAuthorizationService service = new OAuthAuthorizationService(
                new OAuthProviderRegistry(List.of(provider)),
                new OAuthRedirectUriResolver(new MockEnvironment().withProperty(
                        "oauth.redirect-uris.neutral.WEB", "https://app.example.com/callback")),
                new OAuthProductClientCompatibilityResolver(disabledClient),
                new ReturnToValidator(List.of()),
                new CapturingStateStore(),
                new MockEnvironment(),
                Clock.systemUTC()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.startAuthorization(
                new OAuthAuthorizationCommand("neutral", ClientChannel.WEB, "/", "disabled-client")
        )).isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DISABLED_PRODUCT_CLIENT);
        assertThat(provider.request).isNull();
    }

    @Test
    void 비활성_Carelog_MOBILE_Client는_Provider를_선택하기_전에_거부한다() {
        CapturingProvider provider = new CapturingProvider(false);
        ProductClientReader disabledMobileClient = clientId -> {
            assertThat(clientId).isEqualTo(OAuthProductClientCompatibilityResolver.DEFAULT_CARELOG_MOBILE_CLIENT_ID);
            throw new CustomException(ExceptionStatus.DISABLED_PRODUCT_CLIENT);
        };
        OAuthAuthorizationService service = new OAuthAuthorizationService(
                new OAuthProviderRegistry(List.of(provider)),
                new OAuthRedirectUriResolver(new MockEnvironment().withProperty(
                        "oauth.redirect-uris.neutral.MOBILE", "carelog://oauth/callback")),
                new OAuthProductClientCompatibilityResolver(disabledMobileClient),
                new ReturnToValidator(List.of()),
                new CapturingStateStore(),
                new MockEnvironment(),
                Clock.systemUTC()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.startAuthorization(
                new OAuthAuthorizationCommand("neutral", ClientChannel.MOBILE, "/")
        )).isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DISABLED_PRODUCT_CLIENT);
        assertThat(provider.request).isNull();
    }

    @Test
    void callback_mapping이_없으면_Provider_authorization_URL을_만들기_전에_거부한다() {
        CapturingProvider provider = new CapturingProvider(false);
        OAuthAuthorizationService service = new OAuthAuthorizationService(
                new OAuthProviderRegistry(List.of(provider)),
                new OAuthRedirectUriResolver(new MockEnvironment()),
                defaultClientResolver(),
                new ReturnToValidator(List.of()),
                new CapturingStateStore(),
                new MockEnvironment(),
                Clock.systemUTC()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.startAuthorization(
                new OAuthAuthorizationCommand("neutral", ClientChannel.WEB, "/")
        )).isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.UNSUPPORTED_CLIENT_CHANNEL);
        assertThat(provider.request).isNull();
    }

    private OAuthProductClientCompatibilityResolver defaultClientResolver() {
        return new OAuthProductClientCompatibilityResolver(clientId -> switch (clientId) {
            case OAuthProductClientCompatibilityResolver.DEFAULT_CARELOG_WEB_CLIENT_ID ->
                    new RegisteredProductClient(clientId, Product.CARELOG, ProductClientChannel.WEB);
            case OAuthProductClientCompatibilityResolver.DEFAULT_CARELOG_MOBILE_CLIENT_ID ->
                    new RegisteredProductClient(clientId, Product.CARELOG, ProductClientChannel.MOBILE);
            default -> throw new CustomException(ExceptionStatus.UNKNOWN_PRODUCT_CLIENT);
        });
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
        private final boolean supportsPkce;
        private OAuthAuthorizationRequest request;

        private CapturingProvider(boolean requiresNonce) {
            this(requiresNonce, true);
        }
        private CapturingProvider(boolean requiresNonce, boolean supportsPkce) {
            this.requiresNonce = requiresNonce;
            this.supportsPkce = supportsPkce;
        }

        @Override public String providerCode() { return "neutral"; }
        @Override public boolean requiresNonce() { return requiresNonce; }
        @Override public boolean supportsPkce() { return supportsPkce; }
        @Override public URI buildAuthorizationUrl(OAuthAuthorizationRequest request) {
            this.request = request;
            return URI.create("https://provider.example/authorize");
        }
        @Override public OAuthTokenGrant exchangeCode(String authorizationCode, URI redirectUri, String codeVerifier) { return null; }
        @Override public OAuthPrincipal fetchPrincipal(OAuthTokenGrant grant, OAuthStateRecord state) { return null; }
    }
}
