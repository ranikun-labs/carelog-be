package carelog.gateway.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class OAuthRateLimitConfigValidationTest {

    private val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }

    @Test
    fun `rejects non-positive trusted proxy hop configuration`() {
        val config = GatewayConfig(oauthRateLimit = OAuthRateLimitConfig(trustedProxyHops = 0))

        assertThat(validator.validate(config))
            .anyMatch { violation -> violation.propertyPath.toString() == "oauthRateLimit.trustedProxyHops" }
    }

    @Test
    fun `rejects non-positive OAuth bucket values`() {
        val config = GatewayConfig(
            oauthRateLimit = OAuthRateLimitConfig(
                authorization = RateLimitBucketConfig(replenishRate = 0, burstCapacity = 10),
                exchange = RateLimitBucketConfig(replenishRate = 3, burstCapacity = 0)
            )
        )

        assertThat(validator.validate(config).map { it.propertyPath.toString() })
            .contains("oauthRateLimit.authorization.replenishRate", "oauthRateLimit.exchange.burstCapacity")
    }
}
