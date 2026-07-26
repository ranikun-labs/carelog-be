package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.UserPrincipal;

import java.util.UUID;

/**
 * {@link IdentityCredentialAdapter}가 반환하는 {@link UserPrincipal} 구현.
 * CRM Entity를 감싸지 않고 claim 4개만 보유한다.
 */
record IdentityAuthenticatedPrincipal(
        String userId,
        UUID organizationId,
        String role,
        UUID publicId
) implements UserPrincipal {

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
