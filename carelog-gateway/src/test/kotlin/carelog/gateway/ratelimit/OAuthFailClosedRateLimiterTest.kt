package carelog.gateway.ratelimit

import carelog.gateway.filter.OAuthPublicRequestMatcher
import carelog.gateway.filter.OAuthRateLimitFailureClosedFilter
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class OAuthFailClosedRateLimiterTest {

    private val routeId = "kakao-oauth-authorization"
    private val key = "203.0.113.10"
    private val configurationSource = RedisRateLimiter(5, 10)
    private val routeConfig = RedisRateLimiter.Config()
        .setReplenishRate(5)
        .setBurstCapacity(10)
        .setRequestedTokens(1)

    @Suppress("UNCHECKED_CAST")
    private val redisTemplate = mock(ReactiveStringRedisTemplate::class.java) as ReactiveStringRedisTemplate

    @Suppress("UNCHECKED_CAST")
    private val redisScript = mock(RedisScript::class.java) as RedisScript<List<Long>>

    private val limiter = OAuthFailClosedRateLimiter(redisTemplate, redisScript, configurationSource)

    init {
        configurationSource.config[routeId] = routeConfig
    }

    @Test
    fun `maps missing route configuration to a fail-closed signal`() {
        val unconfiguredLimiter = OAuthFailClosedRateLimiter(
            redisTemplate,
            redisScript,
            RedisRateLimiter(5, 10)
        )

        val failure = catchThrowable { unconfiguredLimiter.isAllowed(routeId, key).block() }

        assertThat(failure).isInstanceOf(OAuthRateLimitInfrastructureException::class.java)
    }

    @Test
    fun `preserves the Spring Cloud Gateway allow response and headers`() {
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.just(listOf(1L, 9L)))

        val response = limiter.isAllowed(routeId, key).block()!!

        assertThat(response.isAllowed).isTrue()
        assertThat(response.headers)
            .containsEntry("X-RateLimit-Remaining", "9")
            .containsEntry("X-RateLimit-Replenish-Rate", "5")
            .containsEntry("X-RateLimit-Burst-Capacity", "10")
            .containsEntry("X-RateLimit-Requested-Tokens", "1")
    }

    @Test
    fun `preserves the Spring Cloud Gateway deny response`() {
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.just(listOf(0L, 0L)))

        val response = limiter.isAllowed(routeId, key).block()!!

        assertThat(response.isAllowed).isFalse()
        assertThat(response.headers).containsEntry("X-RateLimit-Remaining", "0")
    }

    @Test
    fun `maps Redis script execution failures to a fail-closed signal`() {
        val redisFailure = IllegalStateException("redis unavailable")
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.error<List<Long>>(redisFailure))

        val failure = catchThrowable { limiter.isAllowed(routeId, key).block() }

        assertThat(failure)
            .isInstanceOf(OAuthRateLimitInfrastructureException::class.java)
            .hasCause(redisFailure)
    }

    @Test
    fun `maps an empty script result to a fail-closed signal`() {
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.empty<List<Long>>())

        val failure = catchThrowable { limiter.isAllowed(routeId, key).block() }

        assertThat(failure).isInstanceOf(OAuthRateLimitInfrastructureException::class.java)
    }

    @Test
    fun `maps malformed script results to a fail-closed signal`() {
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.just(listOf(1L)))

        val failure = catchThrowable { limiter.isAllowed(routeId, key).block() }

        assertThat(failure).isInstanceOf(OAuthRateLimitInfrastructureException::class.java)
    }

    @Test
    fun `rejects the upstream RedisRateLimiter fail-open sentinel`() {
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.just(listOf(1L, -1L)))

        val failure = catchThrowable { limiter.isAllowed(routeId, key).block() }

        assertThat(failure).isInstanceOf(OAuthRateLimitInfrastructureException::class.java)
    }

    @Test
    fun `rejects an allow flag outside zero or one`() {
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.just(listOf(2L, 0L)))

        val failure = catchThrowable { limiter.isAllowed(routeId, key).block() }

        assertThat(failure).isInstanceOf(OAuthRateLimitInfrastructureException::class.java)
    }

    @Test
    fun `rejects remaining tokens above bucket capacity`() {
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.just(listOf(1L, 11L)))

        val failure = catchThrowable { limiter.isAllowed(routeId, key).block() }

        assertThat(failure).isInstanceOf(OAuthRateLimitInfrastructureException::class.java)
    }

    @Test
    fun `returns 503 through the RequestRateLimiter filter without invoking upstream`() {
        val redisFailure = IllegalStateException("redis unavailable")
        `when`(redisTemplate.execute(redisScript, keys(), args()))
            .thenReturn(Flux.error<List<Long>>(redisFailure))

        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.method(
                HttpMethod.POST,
                "/api/v1/auth/oauth/kakao/authorization"
            ).build()
        )
        val resolver = KeyResolver { Mono.just(key) }
        val rateLimiterConfig = RequestRateLimiterGatewayFilterFactory.Config()
            .setKeyResolver(resolver)
            .setRateLimiter(limiter)
        rateLimiterConfig.setRouteId(routeId)

        val rateLimiterFilter = RequestRateLimiterGatewayFilterFactory(limiter, resolver)
            .apply(rateLimiterConfig)
        val upstreamInvoked = AtomicBoolean(false)
        val chain = GatewayFilterChain { request ->
            rateLimiterFilter.filter(request, GatewayFilterChain {
                upstreamInvoked.set(true)
                Mono.empty<Void>()
            })
        }
        val failureFilter = OAuthRateLimitFailureClosedFilter(OAuthPublicRequestMatcher())

        failureFilter.filter(exchange, chain).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        assertThat(upstreamInvoked).isFalse()
    }

    private fun keys() = listOf(
        "request_rate_limiter.{$routeId.$key}.tokens",
        "request_rate_limiter.{$routeId.$key}.timestamp"
    )

    private fun args() = listOf("5", "10", "", "1")
}
