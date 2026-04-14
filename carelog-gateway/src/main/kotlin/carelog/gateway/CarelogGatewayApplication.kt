package carelog.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CarelogGatewayApplication

fun main(args: Array<String>) {
    runApplication<CarelogGatewayApplication>(*args)
}
