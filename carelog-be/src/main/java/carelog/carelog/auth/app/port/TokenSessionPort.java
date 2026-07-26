package carelog.carelog.auth.app.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh Token 저장/조회/삭제/갱신 경계.
 *
 * <p>현재 {@code AuthServiceImpl}이 {@code RefreshTokenRepository}와 {@code RefreshToken} Entity를
 * 직접 다루는 부분을 대체한다. 만료 검사·subject 일치 검사 같은 Auth 도메인 규칙은 CRM 결합이 아니므로
 * Port로 옮기지 않고 {@code AuthServiceImpl}에 그대로 둔다.
 *
 * <p>Identity Foundation B0: 세션의 공식 키를 loginId에서 accountId로 전환했다(과거 loginId는
 * Legacy Mirror 컬럼으로만 남고 조회 키로 쓰이지 않는다).
 */
public interface TokenSessionPort {

    /**
     * 로그인 경로: 해당 Account의 기존 토큰을 삭제한 뒤 신규 토큰을 저장한다.
     * 현재 {@code deleteByAccountId → save} 호출 순서를 그대로 보존해야 한다.
     */
    void replaceForAccount(UUID accountId, String newToken, OffsetDateTime expiresAt);

    /**
     * 원문 refresh token으로 저장된 세션을 조회한다.
     */
    Optional<RefreshSession> findByToken(String rawToken);

    /**
     * refresh 경로: 기존 세션을 신규 토큰/만료 시각으로 갱신한다.
     * 현재 {@code RefreshToken.updateToken(...)} dirty-checking 동작을 그대로 보존해야 한다.
     */
    void rotate(RefreshSession session, String newToken, OffsetDateTime newExpiresAt);

    /**
     * 로그아웃 경로: 해당 Account의 refresh 세션을 삭제한다.
     */
    void deleteForAccount(UUID accountId);

    /**
     * 만료 삭제 경로: 특정 세션 하나를 삭제한다.
     */
    void delete(RefreshSession session);
}
