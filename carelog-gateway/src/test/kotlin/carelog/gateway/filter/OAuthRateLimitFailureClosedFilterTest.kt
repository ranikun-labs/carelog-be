package carelog.gateway.filter

import org.assertj.core.api.Assertions.assertThat
import carelog.gateway.ratelimit.OAuthRateLimitInfrastructureException
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono

class OAuthRateLimitFailureClosedFilterTest {

    private val filter = OAuthRateLimitFailureClosedFilter(OAuthPublicRequestMatcher())

    @Test
    fun `returns 503 instead of allowing a public OAuth request when Redis rate limiting fails`() {
        val exchange = exchange(HttpMethod.POST, "/api/v1/auth/oauth/kakao/authorization")
        val failingChain = GatewayFilterChain {
            Mono.error(OAuthRateLimitInfrastructureException(IllegalStateException("redis unavailable")))
        }

        filter.filter(exchange, failingChain).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `does not convert unrelated downstream failures into an OAuth rate limit response`() {
        val exchange = exchange(HttpMethod.GET, "/api/v1/patients")
        val failure = OAuthRateLimitInfrastructureException(IllegalStateException("backend failure"))
        val failingChain = GatewayFilterChain { Mono.error(failure) }

        val signal = org.assertj.core.api.Assertions.catchThrowable {
            filter.filter(exchange, failingChain).block()
        }

        assertThat(signal).isSameAs(failure)
    }

    private fun exchange(method: HttpMethod, path: String) =
        MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build())
}
