package carelog.carelog.auth.app.port;

/**
 * 로그인 식별자(userId)로 현재 CRM claim(organizationId, role, publicId)을 재조회하는 경계.
 *
 * <p>{@code refreshToken()} 재발급 시 최신 claim을 보장하기 위해 사용하는 유일한 조회 지점이다.
 * 반환 타입에 CRM {@code User} Entity를 노출하지 않는다.
 *
 * <p>Phase 3A: 계약 정의만 도입한다. Legacy Adapter(UserRepository 위임)는 3A-2에서 구현한다.
 */
public interface CRMIdentityProjectionPort {

    /**
     * userId에 해당하는 CRM claim projection을 반환한다.
     *
     * <p>대상 없음 시 현재 계약({@code USER_NOT_FOUND})과 동일하게 매핑되어야 한다.
     */
    CRMIdentityClaims getIdentityClaims(String userId);
}
