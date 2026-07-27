package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.AuthTokenIssuanceService;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.auth.app.port.oauth.ExternalIdentityLookupPort;
import carelog.carelog.auth.app.port.oauth.LinkedAccountStatus;
import carelog.carelog.auth.app.port.oauth.LinkedAccountView;
import carelog.carelog.auth.app.port.oauth.OAuthCallbackCommand;
import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.auth.app.port.oauth.OAuthPrincipal;
import carelog.carelog.auth.app.port.oauth.OAuthProviderException;
import carelog.carelog.auth.app.port.oauth.OAuthProviderPort;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthStateStore;
import carelog.carelog.auth.app.port.oauth.OnboardingCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** OAuth callback state를 소비하고 연결된 계정을 기존 토큰 발급 경로로 인증한다. */
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private final OAuthProviderRegistry providerRegistry;
    private final OAuthStateStore stateStore;
    private final ExternalIdentityLookupPort externalIdentityLookupPort;
    private final CRMIdentityProjectionPort crmIdentityProjectionPort;
    private final AuthTokenIssuanceService authTokenIssuanceService;

    public OAuthLoginResult completeLogin(OAuthCallbackCommand command) {
        OAuthProviderPort provider = providerRegistry.resolve(command.provider());
        String providerCode = OAuthProviderRegistry.normalize(provider.providerCode());
        Optional<OAuthStateRecord> state = stateStore.consume(command.state());
        if (state.isEmpty() || !providerCode.equals(OAuthProviderRegistry.normalize(state.get().provider()))) {
            return new OAuthLoginResult.InvalidOrExpiredState();
        }

        OAuthPrincipal principal;
        try {
            principal = provider.fetchPrincipal(
                    provider.exchangeCode(command.authorizationCode(), state.get().redirectUri(), state.get().codeVerifier()),
                    state.get()
            );
        } catch (OAuthProviderException e) {
            return new OAuthLoginResult.ProviderAuthenticationFailed(e.reason());
        }

        if (!providerCode.equals(OAuthProviderRegistry.normalize(principal.provider()))
                || principal.providerSubject() == null || principal.providerSubject().isBlank()) {
            return new OAuthLoginResult.ProviderAuthenticationFailed(
                    OAuthLoginResult.FailureReason.PRINCIPAL_UNVERIFIED);
        }

        Optional<LinkedAccountView> linkedAccount = externalIdentityLookupPort
                .findByProviderSubject(providerCode, principal.providerSubject());
        if (linkedAccount.isEmpty()) {
            return new OAuthLoginResult.NewAccountOnboardingRequired(new OnboardingCandidate(
                    providerCode,
                    principal.providerSubject(),
                    principal.email(),
                    principal.emailVerified(),
                    principal.displayName(),
                    state.get().returnTo()
            ));
        }

        if (linkedAccount.get().status() != LinkedAccountStatus.ACTIVE) {
            return new OAuthLoginResult.ExternalIdentityConflict(
                    OAuthLoginResult.ConflictReason.ACCOUNT_INACTIVE);
        }

        return crmIdentityProjectionPort.findIdentityClaims(linkedAccount.get().accountId())
                .<OAuthLoginResult>map(claims -> new OAuthLoginResult.ExistingAccountAuthenticated(
                        linkedAccount.get().accountId(),
                        authTokenIssuanceService.issue(linkedAccount.get().accountId(), claims)
                ))
                .orElseGet(() -> new OAuthLoginResult.ExternalIdentityConflict(
                        OAuthLoginResult.ConflictReason.ORPHANED_IDENTITY));
    }
}
