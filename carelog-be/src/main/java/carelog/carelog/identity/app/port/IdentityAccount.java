package carelog.carelog.identity.app.port;

import java.util.UUID;

/**
 * Identity Account Registration 결과. Port 계약이므로 Entity를 노출하지 않는다.
 *
 * <p>{@code encodedPasswordHash}는 Carelog Enrollment Projection이 기존 {@code users.password}
 * 컬럼(호환 기간 동안 유지, 인증에는 더 이상 사용하지 않음)을 같은 해시로 채우기 위해 재사용한다.
 * password_credentials가 유일한 인증 조회 대상이며, 이 값을 다시 인증에 사용하지 않는다.
 */
public record IdentityAccount(
        UUID accountId,
        String loginId,
        String encodedPasswordHash
) {
}
