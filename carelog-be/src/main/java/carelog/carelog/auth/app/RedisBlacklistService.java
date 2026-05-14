package carelog.carelog.auth.app;

import lombok.*;
import lombok.extern.slf4j.*;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.*;

import java.time.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisBlacklistService {

    private final StringRedisTemplate redisTemplate;

    public void addToBlacklist(String token, Duration ttl) {
        String key = "blacklist:" + token;
        redisTemplate.opsForValue().set(key, "1", ttl);
        log.info("Blacklist 등록 완료 - key length: {}, ttl: {}", key.length(), ttl);
    }
}
