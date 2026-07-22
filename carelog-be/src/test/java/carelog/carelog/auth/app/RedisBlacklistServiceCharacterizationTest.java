package carelog.carelog.auth.app;

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
 * RedisBlacklistService의 현재 key/value/TTL 계약을 고정하는 Characterization Test.
 * Phase 1A: docs/context/identity/auth-extraction-audit.md 기준.
 */
@ExtendWith(MockitoExtension.class)
class RedisBlacklistServiceCharacterizationTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisBlacklistService redisBlacklistService;

    @BeforeEach
    void setUp() {
        redisBlacklistService = new RedisBlacklistService(redisTemplate);
    }

    // 6.1 Raw Token blacklist 저장
    @DisplayName("raw Access Token을 blacklist: prefix key로 value=\"1\", 지정 TTL로 저장한다")
    @Test
    void blacklist_writesRawAccessTokenUnderBlacklistPrefixWithValueOneAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String rawToken = "raw-access-token";
        Duration ttl = Duration.ofMinutes(10);

        redisBlacklistService.addToBlacklist(rawToken, ttl);

        verify(valueOperations).set("blacklist:raw-access-token", "1", ttl);
    }
}
