package carelog.gateway.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import reactor.core.publisher.Mono

class OAuthFailClosedRateLimiterTest {

    @Test
    fun `maps Redis limiter failures to the Gateway fail-closed signal`() {
        val delegate = mock(RedisRateLimiter::class.java)
        val redisFailure = IllegalStateException("redis unavailable")
        `when`(delegate.isAllowed("kakao-oauth-authorization", "203.0.113.10"))
            .thenReturn(Mono.error(redisFailure))

        val failure = catchThrowable {
            OAuthFailClosedRateLimiter(delegate)
                .isAllowed("kakao-oauth-authorization", "203.0.113.10")
                .block()
        }

        assertThat(failure)
            .isInstanceOf(OAuthRateLimitInfrastructureException::class.java)
            .hasCause(redisFailure)
    }
}
