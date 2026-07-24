package carelog.carelog.auth.app.port;

import java.time.Duration;

/**
 * 폐기된 Access Token 차단 등록 경계.
 *
 * <p>로그아웃 등으로 무효화된 Access Token을 blacklist에 등록하는 책임만 담는다.
 * {@code AuthServiceImpl}이 구체 Redis 구현({@code StringRedisTemplate})에 직접 결합하던 부분을 대체한다.
 * 만료 시각 계산({@code JwtTokenProvider#getRemainingValidity})은 Auth 도메인 규칙이므로 Port로 옮기지 않고
 * {@code AuthServiceImpl}에 그대로 둔다.
 *
 * <p>책임 경계: Refresh Token 세션(저장·조회·회전·폐기)은 {@link TokenSessionPort}가 담당한다.
 * 두 Port를 합치지 않는다.
 *
 * <p>차단 여부 <b>조회</b>는 현재 be가 아니라 Gateway가 수행하므로 be 측 계약에는 등록만 둔다.
 * 기존 {@code RedisBlacklistService#addToBlacklist} 시그니처·의미를 그대로 승계한다.
 */
public interface TokenBlacklistPort {

    /**
     * 주어진 Access Token을 지정 TTL 동안 blacklist에 등록한다.
     * 현재 key 형식({@code blacklist:<token>}), value, TTL 동작을 그대로 보존해야 한다.
     */
    void addToBlacklist(String token, Duration ttl);
}
