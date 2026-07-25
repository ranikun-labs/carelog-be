package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.port.TokenBlacklistPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link TokenBlacklistPort}의 Redis 구현.
 *
 * <p>구체 Redis 타입({@code StringRedisTemplate})에 대한 의존을 이 Adapter 안에만 가둔다.
 * Auth Application 코어({@code AuthServiceImpl})는 Port에만 의존한다.
 * key 형식({@code blacklist:<token>}), value({@code "1"}), TTL, 로그 동작은 리팩터링 이전
 * {@code RedisBlacklistService}와 동일하게 보존한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addToBlacklist(String token, Duration ttl) {
        String key = "blacklist:" + token;
        redisTemplate.opsForValue().set(key, "1", ttl);
        log.info("Blacklist 등록 완료 - key length: {}, ttl: {}", key.length(), ttl);
    }
}
