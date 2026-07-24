package carelog.carelog.auth.app.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisTokenBlacklistAdapter의 현재 key/value/TTL 계약을 고정하는 Characterization Test.
 *
 * <p>TokenBlacklistPort 분리 전 {@code RedisBlacklistService}가 보장하던 Redis 저장 계약을 그대로 승계한다.
 * Phase 1A: docs/context/identity/auth-extraction-audit.md 기준.
 */
@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistAdapterCharacterizationTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisTokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisTokenBlacklistAdapter(redisTemplate);
    }

    // 6.1 Raw Token blacklist 저장
    @DisplayName("raw Access Token을 blacklist: prefix key로 value=\"1\", 지정 TTL로 저장한다")
    @Test
    void blacklist_writesRawAccessTokenUnderBlacklistPrefixWithValueOneAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String rawToken = "raw-access-token";
        Duration ttl = Duration.ofMinutes(10);

        adapter.addToBlacklist(rawToken, ttl);

        verify(valueOperations).set("blacklist:raw-access-token", "1", ttl);
    }
}
