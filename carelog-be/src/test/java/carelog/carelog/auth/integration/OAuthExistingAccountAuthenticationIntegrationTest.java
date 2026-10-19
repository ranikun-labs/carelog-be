package carelog.carelog.auth.integration;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.auth.app.AuthTokenIssuanceService;
import carelog.carelog.auth.app.oauth.OAuthLoginService;
import carelog.carelog.auth.app.oauth.OAuthProviderRegistry;
import carelog.carelog.auth.app.oauth.OAuthStateBindingVerifier;
import carelog.carelog.auth.app.port.AuthTokenBundle;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.auth.app.port.oauth.ExternalIdentityLookupPort;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationRequest;
import carelog.carelog.auth.app.port.oauth.OAuthBoundProductClient;
import carelog.carelog.auth.app.port.oauth.OAuthCallbackCommand;
import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.auth.app.port.oauth.OAuthPrincipal;
import carelog.carelog.auth.app.port.oauth.OAuthProviderPort;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthStateStore;
import carelog.carelog.auth.app.port.oauth.OAuthTokenGrant;
import carelog.carelog.auth.domain.RefreshTokenRepository;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.identity.domain.ExternalIdentity;
import carelog.carelog.identity.domain.ExternalIdentityRepository;
import carelog.carelog.user.app.UserService;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** OAuth 기존 계정 성공 경로가 실제 JPA refresh session 교체를 단일 transaction으로 수행하는지 검증한다. */
@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class OAuthExistingAccountAuthenticationIntegrationTest {

    private static final String PROVIDER = "test-provider";
    private static final String STATE = "A".repeat(43);
    private static final URI REDIRECT_URI = URI.create("https://app.example.com/oauth/callback");

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private ExternalIdentityRepository externalIdentityRepository;
    @Autowired private ExternalIdentityLookupPort externalIdentityLookupPort;
    @Autowired private OAuthStateBindingVerifier stateBindingVerifier;
    @Autowired private Clock clock;
    @Autowired private CRMIdentityProjectionPort crmIdentityProjectionPort;
    @Autowired private AuthTokenIssuanceService authTokenIssuanceService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @Test
    void OAuth_기존계정_성공은_proxy_transaction안에서_refresh_session을_교체한다() {
        String userId = "oauth-it-" + UUID.randomUUID().toString().substring(0, 8);
        userService.createManager(new ManagerCreateRequest(
                userId, userId + "@example.com", "password123!", "OAuth 통합 테스트",
                ManagerType.PHYSICAL_THERAPIST, null, null
        ));
        User user = userRepository.findByUserId(userId).orElseThrow();
        externalIdentityRepository.saveAndFlush(ExternalIdentity.create(
                user.getAccountId(), PROVIDER, "provider-subject", null
        ));

        OAuthLoginService service = new OAuthLoginService(
                new OAuthProviderRegistry(List.of(new TestProvider())),
                new FixedStateStore(state()),
                stateBindingVerifier,
                externalIdentityLookupPort,
                crmIdentityProjectionPort,
                authTokenIssuanceService
        );

        OAuthLoginResult result = service.completeLogin(new OAuthCallbackCommand(PROVIDER, "authorization-code", STATE));

        assertThat(result).isInstanceOfSatisfying(OAuthLoginResult.ExistingAccountAuthenticated.class, authenticated -> {
            AuthTokenBundle tokens = authenticated.tokens();
            assertThat(authenticated.accountId()).isEqualTo(user.getAccountId());
            assertThat(tokens.accessToken()).isNotBlank();
            assertThat(refreshTokenRepository.findByRefreshToken(tokens.refreshToken()))
                    .hasValueSatisfying(session -> assertThat(session.getAccountId()).isEqualTo(user.getAccountId()));
        });
    }

    private OAuthStateRecord state() {
        Instant issuedAt = clock.instant().minus(Duration.ofMinutes(1));
        return new OAuthStateRecord(
                OAuthStateRecord.CURRENT_VERSION,
                PROVIDER,
                REDIRECT_URI,
                new OAuthBoundProductClient("carelog-web", Product.CARELOG, ProductClientChannel.WEB),
                "/journals/42",
                "server-only-verifier",
                null,
                issuedAt,
                issuedAt.plus(Duration.ofMinutes(5))
        );
    }

    private record FixedStateStore(OAuthStateRecord record) implements OAuthStateStore {
        @Override public void save(String state, OAuthStateRecord stateRecord, Duration ttl) { }
        @Override public Optional<OAuthStateRecord> consume(String state) { return Optional.of(record); }
    }

    private static final class TestProvider implements OAuthProviderPort {
        @Override public String providerCode() { return PROVIDER; }
        @Override public URI buildAuthorizationUrl(OAuthAuthorizationRequest request) { return URI.create("https://provider.example.test/authorize"); }
        @Override public OAuthTokenGrant exchangeCode(String authorizationCode, URI redirectUri, String codeVerifier) {
            return new OAuthTokenGrant("provider-token", null, Instant.now().plusSeconds(60));
        }
        @Override public OAuthPrincipal fetchPrincipal(OAuthTokenGrant grant, OAuthStateRecord state) {
            return new OAuthPrincipal(PROVIDER, "provider-subject", null, false, null);
        }
    }
}
