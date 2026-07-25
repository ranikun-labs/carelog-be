package carelog.carelog.auth.app.port;

import carelog.carelog.auth.app.UserPrincipal;

/**
 * 로그인 자격증명 검증 경계.
 *
 * <p>Auth Application이 CRM 결합 concrete class(CustomUserDetails)를 직접 다운캐스트하지 않도록,
 * 검증 결과를 내부 계약 타입({@link UserPrincipal})으로 반환한다.
 *
 * <p>Phase 3A: 계약 정의만 도입한다. 실제 Adapter 배선은 3A-2/3A-3에서 수행한다.
 */
public interface CredentialPort {

    /**
     * userId/password를 검증하고 인증 주체를 반환한다.
     *
     * <p>검증 실패 시 현재 계약(HTTP 401, {@code INVALID_CREDENTIALS})과 동일한 결과로 매핑되어야 한다.
     * 매핑을 Port 안에서 하든 밖에서 하든 관계없이 최종 상태 코드는 보존한다.
     */
    UserPrincipal authenticate(String userId, String password);
}
