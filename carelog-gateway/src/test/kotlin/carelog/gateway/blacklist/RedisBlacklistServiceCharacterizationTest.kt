package carelog.gateway.blacklist

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono

/**
 * RedisBlacklistService의 현재 key 포맷과 조회/장애 계약을 고정하는 Characterization Test.
 * Phase 1B: 실제 Redis 없이 ReactiveStringRedisTemplate을 Mock으로 대체한다.
 */
class RedisBlacklistServiceCharacterizationTest {

    private lateinit var redisTemplate: ReactiveStringRedisTemplate
    private lateinit var service: RedisBlacklistService

    @BeforeEach
    fun setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate::class.java)
        service = RedisBlacklistService(redisTemplate)
    }

    @DisplayName("정확히 blacklist:{token} key로 hasKey를 조회하고 등록되어 있으면 true를 반환한다")
    @Test
    fun isBlacklisted_queriesExactBlacklistPrefixKey_returnsTrueWhenPresent() {
        `when`(redisTemplate.hasKey("blacklist:raw-access-token")).thenReturn(Mono.just(true))

        val result = service.isBlacklisted("raw-access-token").blockOptional()

        assertThat(result).contains(true)
        verify(redisTemplate).hasKey("blacklist:raw-access-token")
    }

    @DisplayName("등록되지 않은 토큰이면 false를 반환한다")
    @Test
    fun isBlacklisted_returnsFalseWhenAbsent() {
        `when`(redisTemplate.hasKey("blacklist:raw-access-token")).thenReturn(Mono.just(false))

        val result = service.isBlacklisted("raw-access-token").blockOptional()

        assertThat(result).contains(false)
    }

    @DisplayName("Redis 조회가 실패하면 현재 구현은 에러 시그널을 그대로 전파한다 (fail-open/closed 아님)")
    @Test
    fun isBlacklisted_redisFailure_propagatesErrorSignal() {
        `when`(redisTemplate.hasKey("blacklist:raw-access-token"))
            .thenReturn(Mono.error(RuntimeException("redis down")))

        assertThatThrownBy { service.isBlacklisted("raw-access-token").block() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("redis down")
    }
}
