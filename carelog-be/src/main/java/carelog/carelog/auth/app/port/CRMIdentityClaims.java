package carelog.carelog.auth.app.port;

import java.util.UUID;

/**
 * CRM이 소유하는 Identity claim의 최소 projection.
 *
 * <p>오늘 실제로 access token 발급에 쓰이는 3필드만 담는다. {@code User} Entity를 복제한 거대 DTO가 아니다.
 * {@code role}은 CRM 역할({@code MANAGER}/{@code CUSTOMER})의 문자열 표현이며,
 * {@code publicId}는 후보 Stable ID일 뿐 아직 확정된 Identity 식별자가 아니다.
 */
public record CRMIdentityClaims(
        UUID organizationId,
        String role,
        UUID publicId
) {
}
