package carelog.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway")
data class GatewayConfig(
    val publicPaths: List<String> = emptyList(),
    val internalSecret: String = ""
)