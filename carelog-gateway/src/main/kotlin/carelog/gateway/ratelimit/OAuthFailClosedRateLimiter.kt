package carelog.gateway.ratelimit

import java.util.ArrayList
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import reactor.core.publisher.Mono

class OAuthFailClosedRateLimiter(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val redisScript: RedisScript<List<Long>>,
    private val configurationSource: RedisRateLimiter
) : RateLimiter<RedisRateLimiter.Config> {

    override fun isAllowed(routeId: String, id: String): Mono<RateLimiter.Response> =
        Mono.defer {
            val routeConfig = configurationSource.config[routeId]
                ?: configurationSource.config[RouteDefinitionRouteLocator.DEFAULT_FILTERS]
                ?: throw IllegalStateException("No rate limit configuration found for route $routeId")

            val prefix = "request_rate_limiter.{$routeId.$id}."
            val keys = listOf("${prefix}tokens", "${prefix}timestamp")
            val scriptArgs = listOf(
                routeConfig.replenishRate.toString(),
                routeConfig.burstCapacity.toString(),
                "",
                routeConfig.requestedTokens.toString()
            )

            redisTemplate.execute(redisScript, keys, scriptArgs)
                .reduce(ArrayList<Long>()) { results, values -> results.apply { addAll(values) } }
                .map { results ->
                    if (results.size != 2) {
                        throw IllegalStateException("Unexpected Redis rate limiter response")
                    }

                    val allowed = results[0]
                    val remainingTokens = results[1]
                    if (
                        allowed !in 0L..1L ||
                        remainingTokens < 0L ||
                        remainingTokens > routeConfig.burstCapacity.toLong()
                    ) {
                        throw IllegalStateException("Invalid Redis rate limiter response")
                    }

                    RateLimiter.Response(
                        allowed == 1L,
                        configurationSource.getHeaders(routeConfig, remainingTokens)
                    )
                }
        }.onErrorMap { cause ->
            if (cause is OAuthRateLimitInfrastructureException) cause
            else OAuthRateLimitInfrastructureException(cause)
        }

    override fun getConfigClass(): Class<RedisRateLimiter.Config> = configurationSource.configClass

    override fun newConfig(): RedisRateLimiter.Config = configurationSource.newConfig()

    override fun getConfig(): Map<String, RedisRateLimiter.Config> = configurationSource.config
}

class OAuthRateLimitInfrastructureException(cause: Throwable) : RuntimeException(cause)
