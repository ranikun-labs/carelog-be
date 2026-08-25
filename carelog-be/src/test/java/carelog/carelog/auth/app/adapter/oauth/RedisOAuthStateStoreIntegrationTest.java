package carelog.carelog.auth.app.adapter.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import carelog.carelog.auth.app.port.oauth.OAuthBoundProductClient;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisOAuthStateStoreIntegrationTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisOAuthStateStore store;

    @BeforeAll
    static void setUp() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisOAuthStateStore(redisTemplate, new ObjectMapper().findAndRegisterModules());
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    void state는_GETDEL로_한번만_소비되고_verifier를_보존한다() {
        OAuthStateRecord record = record();

        store.save("one-time-state", record, Duration.ofMinutes(1));

        assertThat(store.consume("one-time-state")).contains(record);
        assertThat(store.consume("one-time-state")).isEmpty();
    }

    @Test
    void TTL이_만료된_state는_없음으로_반환한다() throws InterruptedException {
        store.save("expiring-state", record(), Duration.ofMillis(100));

        Thread.sleep(250);

        assertThat(store.consume("expiring-state")).isEmpty();
    }

    @Test
    void 역직렬화_실패는_인증실패가_아닌_저장소장애로_전파한다() {
        redisTemplate.opsForValue().set("oauth:state:invalid-json", "not-json", Duration.ofMinutes(1));

        assertThatThrownBy(() -> store.consume("invalid-json"))
                .isInstanceOf(OAuthStateStoreUnavailableException.class);
    }

    @Test
    void Redis_연결실패는_인증실패가_아닌_저장소장애로_전파한다() {
        LettuceConnectionFactory unavailableConnectionFactory = new LettuceConnectionFactory("127.0.0.1", 1);
        unavailableConnectionFactory.afterPropertiesSet();
        StringRedisTemplate unavailableTemplate = new StringRedisTemplate(unavailableConnectionFactory);
        unavailableTemplate.afterPropertiesSet();
        RedisOAuthStateStore unavailableStore = new RedisOAuthStateStore(
                unavailableTemplate, new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> unavailableStore.save("state", record(), Duration.ofMinutes(1)))
                .isInstanceOf(OAuthStateStoreUnavailableException.class);
        assertThatThrownBy(() -> unavailableStore.consume("state"))
                .isInstanceOf(OAuthStateStoreUnavailableException.class);

        unavailableConnectionFactory.destroy();
    }

    private static OAuthStateRecord record() {
        return new OAuthStateRecord(
                OAuthStateRecord.CURRENT_VERSION,
                "provider",
                URI.create("https://app.example.com/oauth/callback"),
                new OAuthBoundProductClient("carelog-web", Product.CARELOG, ProductClientChannel.WEB),
                "/journals/42",
                "code-verifier",
                "nonce",
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-27T00:05:00Z")
        );
    }
}
