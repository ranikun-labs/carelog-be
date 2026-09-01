package carelog.carelog.auth.web.internal;

import java.util.UUID;

/**
 * platform-identity가 소비하는 내부 전용 CRM claim projection 응답.
 *
 * <p>ADR-0019가 승인한 정확히 세 필드만 담는다. 그 외 어떤 User 필드도 이 경계를 벗어나지 않는다.
 */
public record IdentityClaimsResponse(
        UUID organizationId,
        String role,
        UUID publicId
) {
}
