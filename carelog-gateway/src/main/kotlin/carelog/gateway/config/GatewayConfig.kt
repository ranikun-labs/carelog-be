package carelog.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import jakarta.validation.Valid
import jakarta.validation.constraints.Min

@Validated
@ConfigurationProperties(prefix = "gateway")
data class GatewayConfig(
    val publicPaths: List<String> = emptyList(),
    val internalSecret: String = "",
    @field:Valid val oauthRateLimit: OAuthRateLimitConfig = OAuthRateLimitConfig()
)

data class OAuthRateLimitConfig(
    @field:Min(1) val trustedProxyHops: Int = 1,
    @field:Valid val authorization: RateLimitBucketConfig = RateLimitBucketConfig(5, 10),
    @field:Valid val exchange: RateLimitBucketConfig = RateLimitBucketConfig(3, 6)
)

data class RateLimitBucketConfig(
    @field:Min(1) val replenishRate: Int,
    @field:Min(1) val burstCapacity: Int
)
