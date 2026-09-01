package carelog.carelog.user.web.dto;

import java.util.UUID;

/** Identity가 소비하는 Carelog Product claims transport DTO. */
public record IdentityClaimsResponse(
        UUID organizationId,
        String role,
        UUID publicId
) {
}
