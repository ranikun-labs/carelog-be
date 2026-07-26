package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.UserPrincipal;

import java.util.UUID;

/**
 * {@link IdentityCredentialAdapter}가 반환하는 {@link UserPrincipal} 구현.
 * CRM Entity를 감싸지 않고 claim 5개만 보유한다.
 *
 * <p>{@code accountId}가 Identity Foundation B0부터 JWT subject/Session의 공식 키다.
 * {@code userId}(loginId)는 Password Credential 로그인 식별자로만 남는다.
 */
record IdentityAuthenticatedPrincipal(
        UUID accountId,
        String userId,
        UUID organizationId,
        String role,
        UUID publicId
) implements UserPrincipal {

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public UUID getOrganizationId() {
        return organizationId;
    }

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public UUID getPublicId() {
        return publicId;
    }
}
