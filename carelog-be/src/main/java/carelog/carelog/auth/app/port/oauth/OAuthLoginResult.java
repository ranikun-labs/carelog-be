package carelog.carelog.auth.app.port.oauth;

import carelog.carelog.auth.app.port.AuthTokenBundle;

import java.util.UUID;

/** OAuth 로그인 완료의 가능한 결과를 빠짐없이 표현한다. */
public sealed interface OAuthLoginResult {

    record ExistingAccountAuthenticated(UUID accountId, AuthTokenBundle tokens) implements OAuthLoginResult {
    }

    record NewAccountOnboardingRequired(OnboardingCandidate candidate) implements OAuthLoginResult {
    }

    record ExternalIdentityConflict(ConflictReason reason) implements OAuthLoginResult {
    }

    record ProviderAuthenticationFailed(FailureReason reason) implements OAuthLoginResult {
    }

    record InvalidOrExpiredState() implements OAuthLoginResult {
    }

    enum ConflictReason {
        ACCOUNT_INACTIVE,
        ORPHANED_IDENTITY
    }

    enum FailureReason {
        CODE_EXCHANGE_FAILED,
        PRINCIPAL_UNVERIFIED,
        PROVIDER_UNAVAILABLE
    }
}
