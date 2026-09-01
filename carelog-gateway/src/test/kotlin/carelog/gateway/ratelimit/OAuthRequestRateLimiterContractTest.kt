package carelog.gateway.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono

class OAuthRequestRateLimiterContractTest {

    @Test
    fun `denied authorization request receives 429`() {
        val rateLimiter = CapturingRateLimiter(allowed = false)
        val factory = RequestRateLimiterGatewayFilterFactory(rateLimiter, clientIpResolver())
        val filter = factory.apply(factory.configFor("kakao-oauth-authorization"))
        val exchange = exchange()

        filter.filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(rateLimiter.routeIds).containsExactly("kakao-oauth-authorization")
    }

    @Test
    fun `authorization and exchange consume separate route buckets`() {
        val rateLimiter = CapturingRateLimiter(allowed = true)
        val factory = RequestRateLimiterGatewayFilterFactory(rateLimiter, clientIpResolver())

        factory.apply(factory.configFor("kakao-oauth-authorization"))
            .filter(exchange(), GatewayFilterChain { Mono.empty() }).block()
        factory.apply(factory.configFor("kakao-oauth-exchange"))
            .filter(exchange(), GatewayFilterChain { Mono.empty() }).block()

        assertThat(rateLimiter.routeIds).containsExactly(
            "kakao-oauth-authorization",
            "kakao-oauth-exchange"
        )
    }

    @Test
    fun `empty client key is rejected without calling the rate limiter`() {
        val rateLimiter = CapturingRateLimiter(allowed = true)
        val factory = RequestRateLimiterGatewayFilterFactory(rateLimiter, KeyResolver { Mono.empty() })
        val filter = factory.apply(factory.configFor("kakao-oauth-authorization"))
        val exchange = exchange()

        filter.filter(exchange, GatewayFilterChain { Mono.empty() }).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(rateLimiter.routeIds).isEmpty()
    }

    private fun clientIpResolver() = KeyResolver { Mono.just("203.0.113.10") }

    private fun RequestRateLimiterGatewayFilterFactory.configFor(routeId: String) =
        newConfig().also { it.setRouteId(routeId) }

    private fun exchange() = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/v1/auth/oauth/kakao/authorization").build()
    )

    private class CapturingRateLimiter(
        private val allowed: Boolean
    ) : RateLimiter<Any> {
        val routeIds = mutableListOf<String>()

        override fun isAllowed(routeId: String, id: String): Mono<RateLimiter.Response> {
            routeIds += routeId
            return Mono.just(RateLimiter.Response(allowed, emptyMap()))
        }

        override fun getConfigClass(): Class<Any> = Any::class.java

        override fun newConfig(): Any = Any()

        override fun getConfig(): Map<String, Any> = emptyMap()
    }
}
