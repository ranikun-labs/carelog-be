package carelog.carelog.auth.app;

import lombok.*;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.*;

import java.time.*;

@Service
@RequiredArgsConstructor
public class RedisBlacklistService {

    private final StringRedisTemplate redisTemplate;

    public void addToBlacklist(String token, Duration ttl) {
        redisTemplate.opsForValue().set("black:" + token, "1", ttl);
    }
}
