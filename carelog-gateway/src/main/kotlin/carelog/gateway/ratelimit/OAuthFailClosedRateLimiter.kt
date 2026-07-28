package carelog.gateway.ratelimit

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import reactor.core.publisher.Mono

class OAuthFailClosedRateLimiter(
    private val delegate: RedisRateLimiter
) : RateLimiter<RedisRateLimiter.Config> {

    override fun isAllowed(routeId: String, id: String): Mono<RateLimiter.Response> =
        delegate.isAllowed(routeId, id)
            .onErrorMap { cause -> OAuthRateLimitInfrastructureException(cause) }

    override fun getConfigClass(): Class<RedisRateLimiter.Config> = delegate.configClass

    override fun newConfig(): RedisRateLimiter.Config = delegate.newConfig()

    override fun getConfig(): Map<String, RedisRateLimiter.Config> = delegate.config
}

class OAuthRateLimitInfrastructureException(cause: Throwable) : RuntimeException(cause)
