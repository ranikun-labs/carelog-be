package carelog.gateway.filter

import carelog.gateway.ratelimit.OAuthRateLimitInfrastructureException
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class OAuthRateLimitFailureClosedFilter(
    private val oauthPublicRequestMatcher: OAuthPublicRequestMatcher
) : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> =
        chain.filter(exchange).onErrorResume(OAuthRateLimitInfrastructureException::class.java) {
            if (!oauthPublicRequestMatcher.matches(exchange.request)) {
                Mono.error(it)
            } else {
                exchange.response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
                exchange.response.setComplete()
            }
        }

    override fun getOrder(): Int = -2
}
