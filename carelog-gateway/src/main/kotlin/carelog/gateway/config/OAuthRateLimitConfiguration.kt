package carelog.gateway.config

import carelog.gateway.ratelimit.OAuthClientIpKeyResolver
import carelog.gateway.ratelimit.OAuthFailClosedRateLimiter
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class OAuthRateLimitConfiguration {

    @Bean("oauthClientIpKeyResolver")
    fun oauthClientIpKeyResolver(gatewayConfig: GatewayConfig): KeyResolver =
        OAuthClientIpKeyResolver(gatewayConfig.oauthRateLimit)

    @Bean("oauthFailClosedRateLimiter")
    @Primary
    fun oauthFailClosedRateLimiter(redisRateLimiter: RedisRateLimiter) =
        OAuthFailClosedRateLimiter(redisRateLimiter)
}
