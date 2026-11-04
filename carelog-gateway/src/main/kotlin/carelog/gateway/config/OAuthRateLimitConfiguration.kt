package carelog.gateway.config

import carelog.gateway.ratelimit.OAuthClientIpKeyResolver
import carelog.gateway.ratelimit.OAuthFailClosedRateLimiter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript

@Configuration
class OAuthRateLimitConfiguration {

    @Bean("oauthClientIpKeyResolver")
    fun oauthClientIpKeyResolver(gatewayConfig: GatewayConfig): KeyResolver =
        OAuthClientIpKeyResolver(gatewayConfig.oauthRateLimit)

    @Bean("oauthFailClosedRateLimiter")
    @Primary
    fun oauthFailClosedRateLimiter(
        redisTemplate: ReactiveStringRedisTemplate,
        @Qualifier(RedisRateLimiter.REDIS_SCRIPT_NAME) redisScript: RedisScript<List<Long>>,
        redisRateLimiter: RedisRateLimiter
    ) = OAuthFailClosedRateLimiter(redisTemplate, redisScript, redisRateLimiter)
}
