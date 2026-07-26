package carelog.carelog.auth.app.port;

import java.util.UUID;

/**
 * accountId(Stable Account ID)로 현재 CRM claim(organizationId, role, publicId)을 재조회하는 경계.
 *
 * <p>로그인(Credential 검증 직후)과 {@code refreshToken()} 재발급 양쪽에서 최신 claim을 보장하기 위해
 * 사용하는 유일한 조회 지점이다. 반환 타입에 CRM {@code User} Entity를 노출하지 않는다.
 *
 * <p>Identity Foundation B0: 조회 키를 loginId에서 accountId로 전환했다(OAuth 계정은 loginId가 없을
 * 수 있어 accountId만이 유일하게 보장되는 식별자다).
 */
public interface CRMIdentityProjectionPort {

    /**
     * accountId에 해당하는 CRM claim projection을 반환한다.
     *
     * <p>대상 없음 시 현재 계약({@code USER_NOT_FOUND})과 동일하게 매핑되어야 한다.
     */
    CRMIdentityClaims getIdentityClaims(UUID accountId);
}
