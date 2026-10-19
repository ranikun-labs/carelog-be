package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.AuthTokenIssuanceService;
import carelog.carelog.auth.app.port.AuthTokenBundle;
import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.auth.app.port.oauth.ExternalIdentityLookupPort;
import carelog.carelog.auth.app.port.oauth.LinkedAccountStatus;
import carelog.carelog.auth.app.port.oauth.LinkedAccountView;
import carelog.carelog.auth.app.port.oauth.OAuthBoundProductClient;
import carelog.carelog.auth.app.port.oauth.OAuthCallbackCommand;
import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.auth.app.port.oauth.OAuthPrincipal;
import carelog.carelog.auth.app.port.oauth.OAuthProviderException;
import carelog.carelog.auth.app.port.oauth.OAuthProviderPort;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthStateStore;
import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import carelog.carelog.auth.app.port.oauth.OAuthTokenGrant;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthLoginServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final URI REDIRECT_URI = URI.create("https://app.example.com/oauth/callback");
    private static final String STATE = "A".repeat(43);

    @Mock private OAuthStateStore stateStore;
    @Mock private OAuthStateBindingVerifier stateBindingVerifier;
    @Mock private ExternalIdentityLookupPort externalIdentityLookupPort;
    @Mock private CRMIdentityProjectionPort crmIdentityProjectionPort;
    @Mock private AuthTokenIssuanceService authTokenIssuanceService;
    @Mock private OAuthProviderPort provider;

    private OAuthLoginService service;

    @BeforeEach
    void setUp() {
        when(provider.providerCode()).thenReturn("neutral");
        service = new OAuthLoginService(
                new OAuthProviderRegistry(List.of(provider)),
                stateStore,
                stateBindingVerifier,
                externalIdentityLookupPort,
                crmIdentityProjectionPort,
                authTokenIssuanceService
        );
    }

    @Test
    void state가_없으면_인증실패_결과를_반환한다() {
        when(stateStore.consume(STATE)).thenReturn(Optional.empty());

        assertThat(service.completeLogin(command())).isInstanceOf(OAuthLoginResult.InvalidOrExpiredState.class);
        verifyNoInteractions(externalIdentityLookupPort, crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void malformed_public_state는_Redis와_provider_exchange_전에_거부한다() {
        List<String> malformedStates = List.of(
                "abc",
                "A".repeat(42),
                "A".repeat(44),
                "A".repeat(42) + "=",
                "A".repeat(21) + "!" + "A".repeat(21),
                "A".repeat(21) + " " + "A".repeat(21),
                "550e8400-e29b-41d4-a716-446655440000"
        );

        for (String malformedState : malformedStates) {
            assertThat(service.completeLogin(new OAuthCallbackCommand(
                    "neutral", "authorization-code", malformedState)))
                    .isInstanceOf(OAuthLoginResult.InvalidOrExpiredState.class);
        }

        verify(stateStore, never()).consume(anyString());
        verify(provider, never()).exchangeCode(anyString(), any(), anyString());
        verifyNoInteractions(stateBindingVerifier, externalIdentityLookupPort,
                crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void 요청_provider와_state_provider가_다르면_인증실패_결과를_반환한다() {
        when(stateStore.consume(STATE)).thenReturn(Optional.of(new OAuthStateRecord(
                OAuthStateRecord.CURRENT_VERSION,
                "different",
                REDIRECT_URI,
                productClient(),
                "/journals/42",
                "server-only-verifier",
                null,
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-27T00:05:00Z")
        )));
        when(stateBindingVerifier.verify(eq("neutral"), any(OAuthStateRecord.class))).thenReturn(false);

        assertThat(service.completeLogin(command())).isInstanceOf(OAuthLoginResult.InvalidOrExpiredState.class);
        verify(provider, never()).exchangeCode(anyString(), any(), anyString());
        verifyNoInteractions(externalIdentityLookupPort, crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void legacy_or_invalid_stored_state는_provider_exchange_전에_거부한다() {
        OAuthStateRecord legacyState = new OAuthStateRecord(
                0,
                "neutral",
                REDIRECT_URI,
                null,
                "/journals/42",
                "server-only-verifier",
                null,
                Instant.parse("2026-07-27T00:00:00Z"),
                null
        );
        when(stateStore.consume(STATE)).thenReturn(Optional.of(legacyState));
        when(stateBindingVerifier.verify("neutral", legacyState)).thenReturn(false);

        assertThat(service.completeLogin(command())).isInstanceOf(OAuthLoginResult.InvalidOrExpiredState.class);
        verify(provider, never()).exchangeCode(anyString(), any(), anyString());
        verifyNoInteractions(externalIdentityLookupPort, crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void state_store_장애는_인증실패로_흡수하지_않고_전파한다() {
        when(stateStore.consume(STATE))
                .thenThrow(new OAuthStateStoreUnavailableException("redis unavailable", new RuntimeException()));

        assertThatThrownBy(() -> service.completeLogin(command()))
                .isInstanceOf(OAuthStateStoreUnavailableException.class);
        verifyNoInteractions(externalIdentityLookupPort, crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void 연결된_identity가_없으면_어떤_write도_없이_onboarding_결과를_반환한다() {
        setVerifiedPrincipal();
        when(externalIdentityLookupPort.findByProviderSubject("neutral", "subject"))
                .thenReturn(Optional.empty());

        OAuthLoginResult result = service.completeLogin(command());

        assertThat(result).isInstanceOfSatisfying(OAuthLoginResult.NewAccountOnboardingRequired.class, onboarding -> {
            assertThat(onboarding.candidate().provider()).isEqualTo("neutral");
            assertThat(onboarding.candidate().providerSubject()).isEqualTo("subject");
            assertThat(onboarding.candidate().returnTo()).isEqualTo("/journals/42");
        });
        verifyNoInteractions(crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void inactive_identity는_토큰발급_없이_충돌결과를_반환한다() {
        setVerifiedPrincipal();
        when(externalIdentityLookupPort.findByProviderSubject("neutral", "subject"))
                .thenReturn(Optional.of(new LinkedAccountView(ACCOUNT_ID, LinkedAccountStatus.INACTIVE)));

        assertThat(service.completeLogin(command())).isEqualTo(
                new OAuthLoginResult.ExternalIdentityConflict(OAuthLoginResult.ConflictReason.ACCOUNT_INACTIVE));
        verifyNoInteractions(crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void CRM_claim이_없으면_orphaned_identity_충돌결과를_반환한다() {
        setVerifiedPrincipal();
        when(externalIdentityLookupPort.findByProviderSubject("neutral", "subject"))
                .thenReturn(Optional.of(new LinkedAccountView(ACCOUNT_ID, LinkedAccountStatus.ACTIVE)));
        when(crmIdentityProjectionPort.findIdentityClaims(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThat(service.completeLogin(command())).isEqualTo(
                new OAuthLoginResult.ExternalIdentityConflict(OAuthLoginResult.ConflictReason.ORPHANED_IDENTITY));
        verifyNoInteractions(authTokenIssuanceService);
    }

    @Test
    void 기존_활성_identity는_state의_redirectUri와_verifier로_교환하고_공통발급을_사용한다() {
        setVerifiedPrincipal();
        CRMIdentityClaims claims = new CRMIdentityClaims(UUID.randomUUID(), "MANAGER", UUID.randomUUID());
        when(externalIdentityLookupPort.findByProviderSubject("neutral", "subject"))
                .thenReturn(Optional.of(new LinkedAccountView(ACCOUNT_ID, LinkedAccountStatus.ACTIVE)));
        when(crmIdentityProjectionPort.findIdentityClaims(ACCOUNT_ID)).thenReturn(Optional.of(claims));
        when(authTokenIssuanceService.issue(ACCOUNT_ID, claims))
                .thenReturn(new AuthTokenBundle("access", "refresh"));

        OAuthLoginResult result = service.completeLogin(command());

        assertThat(result).isEqualTo(new OAuthLoginResult.ExistingAccountAuthenticated(
                ACCOUNT_ID, new AuthTokenBundle("access", "refresh")));
        verify(provider).exchangeCode("authorization-code", REDIRECT_URI, "server-only-verifier");
        verify(authTokenIssuanceService).issue(ACCOUNT_ID, claims);
    }

    @Test
    void provider_전용_인증실패만_명시적_결과로_변환한다() {
        when(stateBindingVerifier.verify(anyString(), any())).thenReturn(true);
        when(stateStore.consume(STATE)).thenReturn(Optional.of(state()));
        when(provider.exchangeCode(anyString(), any(), anyString()))
                .thenThrow(new OAuthProviderException(OAuthLoginResult.FailureReason.CODE_EXCHANGE_FAILED));

        assertThat(service.completeLogin(command())).isEqualTo(
                new OAuthLoginResult.ProviderAuthenticationFailed(OAuthLoginResult.FailureReason.CODE_EXCHANGE_FAILED));
        verifyNoInteractions(externalIdentityLookupPort, crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void providerSubject가_없거나_blank이면_검증실패_결과를_반환한다() {
        when(stateBindingVerifier.verify(anyString(), any())).thenReturn(true);
        when(stateStore.consume(STATE)).thenReturn(Optional.of(state()));
        OAuthTokenGrant grant = new OAuthTokenGrant("provider-token", null, Instant.parse("2026-07-27T01:00:00Z"));
        when(provider.exchangeCode("authorization-code", REDIRECT_URI, "server-only-verifier")).thenReturn(grant);
        when(provider.fetchPrincipal(grant, state())).thenReturn(new OAuthPrincipal(
                "neutral", " ", "snapshot@example.com", true, "Display name"));

        assertThat(service.completeLogin(command())).isEqualTo(
                new OAuthLoginResult.ProviderAuthenticationFailed(OAuthLoginResult.FailureReason.PRINCIPAL_UNVERIFIED));
        verifyNoInteractions(externalIdentityLookupPort, crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void external_identity_조회_DB장애는_conflict로_흡수하지_않고_전파한다() {
        setVerifiedPrincipal();
        when(externalIdentityLookupPort.findByProviderSubject("neutral", "subject"))
                .thenThrow(new IllegalStateException("external identity db failure"));

        assertThatThrownBy(() -> service.completeLogin(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("external identity db failure");
        verifyNoInteractions(crmIdentityProjectionPort, authTokenIssuanceService);
    }

    @Test
    void CRM_projection_조회_DB장애는_conflict로_흡수하지_않고_전파한다() {
        setVerifiedPrincipal();
        when(externalIdentityLookupPort.findByProviderSubject("neutral", "subject"))
                .thenReturn(Optional.of(new LinkedAccountView(ACCOUNT_ID, LinkedAccountStatus.ACTIVE)));
        when(crmIdentityProjectionPort.findIdentityClaims(ACCOUNT_ID))
                .thenThrow(new IllegalStateException("crm projection db failure"));

        assertThatThrownBy(() -> service.completeLogin(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("crm projection db failure");
        verifyNoInteractions(authTokenIssuanceService);
    }

    @Test
    void 토큰발급_장애는_결과로_흡수하지_않고_전파한다() {
        setVerifiedPrincipal();
        CRMIdentityClaims claims = new CRMIdentityClaims(UUID.randomUUID(), "MANAGER", UUID.randomUUID());
        when(externalIdentityLookupPort.findByProviderSubject("neutral", "subject"))
                .thenReturn(Optional.of(new LinkedAccountView(ACCOUNT_ID, LinkedAccountStatus.ACTIVE)));
        when(crmIdentityProjectionPort.findIdentityClaims(ACCOUNT_ID)).thenReturn(Optional.of(claims));
        when(authTokenIssuanceService.issue(ACCOUNT_ID, claims)).thenThrow(new IllegalStateException("session failure"));

        assertThatThrownBy(() -> service.completeLogin(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("session failure");
    }

    @Test
    void JWT_생성_장애는_conflict로_흡수하지_않고_전파한다() {
        setActiveLinkedAccount();
        when(authTokenIssuanceService.issue(any(), any()))
                .thenThrow(new IllegalStateException("jwt generation failed"));

        assertThatThrownBy(() -> service.completeLogin(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("jwt generation failed");
    }

    @Test
    void refresh_session_저장_장애는_conflict로_흡수하지_않고_전파한다() {
        setActiveLinkedAccount();
        when(authTokenIssuanceService.issue(any(), any()))
                .thenThrow(new IllegalStateException("refresh session persistence failed"));

        assertThatThrownBy(() -> service.completeLogin(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("refresh session persistence failed");
    }

    private void setActiveLinkedAccount() {
        setVerifiedPrincipal();
        when(externalIdentityLookupPort.findByProviderSubject("neutral", "subject"))
                .thenReturn(Optional.of(new LinkedAccountView(ACCOUNT_ID, LinkedAccountStatus.ACTIVE)));
        when(crmIdentityProjectionPort.findIdentityClaims(ACCOUNT_ID))
                .thenReturn(Optional.of(new CRMIdentityClaims(UUID.randomUUID(), "MANAGER", UUID.randomUUID())));
    }

    private void setVerifiedPrincipal() {
        when(stateBindingVerifier.verify(anyString(), any())).thenReturn(true);
        when(stateStore.consume(STATE)).thenReturn(Optional.of(state()));
        OAuthTokenGrant grant = new OAuthTokenGrant("provider-token", null, Instant.parse("2026-07-27T01:00:00Z"));
        when(provider.exchangeCode("authorization-code", REDIRECT_URI, "server-only-verifier")).thenReturn(grant);
        when(provider.fetchPrincipal(grant, state())).thenReturn(new OAuthPrincipal(
                "neutral", "subject", "snapshot@example.com", true, "Display name"));
    }

    private OAuthCallbackCommand command() {
        return new OAuthCallbackCommand("neutral", "authorization-code", STATE);
    }

    private OAuthStateRecord state() {
        return new OAuthStateRecord(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                REDIRECT_URI,
                productClient(),
                "/journals/42",
                "server-only-verifier",
                null,
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-27T00:05:00Z")
        );
    }

    private OAuthBoundProductClient productClient() {
        return new OAuthBoundProductClient("carelog-web", Product.CARELOG, ProductClientChannel.WEB);
    }
}
