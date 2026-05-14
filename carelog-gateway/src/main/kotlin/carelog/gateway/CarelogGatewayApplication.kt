package carelog.gateway

import carelog.gateway.config.GatewayConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(GatewayConfig::class)
class CarelogGatewayApplication

fun main(args: Array<String>) {
    runApplication<CarelogGatewayApplication>(*args)
}
