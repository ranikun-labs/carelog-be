package carelog.carelog.auth.app.adapter.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthStateStore;
import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Redis GETDEL로 OAuth state를 한 번만 소비하게 하는 저장소 구현이다. */
@Component
@RequiredArgsConstructor
public class RedisOAuthStateStore implements OAuthStateStore {

    private static final String KEY_PREFIX = "oauth:state:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String state, OAuthStateRecord record, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(state), objectMapper.writeValueAsString(record), ttl);
        } catch (JsonProcessingException | RedisConnectionFailureException | QueryTimeoutException e) {
            throw unavailable(e);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public Optional<OAuthStateRecord> consume(String state) {
        try {
            String value = redisTemplate.opsForValue().getAndDelete(key(state));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, OAuthStateRecord.class));
        } catch (JsonProcessingException | RedisConnectionFailureException | QueryTimeoutException e) {
            throw unavailable(e);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private OAuthStateStoreUnavailableException unavailable(Exception cause) {
        return new OAuthStateStoreUnavailableException("OAuth state store is unavailable", cause);
    }

    private String key(String state) {
        return KEY_PREFIX + state;
    }
}
