package carelog.gateway.route

import carelog.gateway.CarelogGatewayApplication
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@AutoConfigureWebTestClient
@SpringBootTest(
    classes = [CarelogGatewayApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "CARELOG_RAG_URI=http://localhost:8081",
        "GATEWAY_INTERNAL_SECRET=test-gateway-secret",
        "JWT_SECRET_KEY=test-jwt-secret-key-that-is-long-enough-for-hmac-sha"
    ]
)
class OAuthGatewayRedisConnectionFailureRouteTest(
    @Autowired private val webTestClient: WebTestClient,
    @Autowired private val redisConnectionFactory: ReactiveRedisConnectionFactory
) {

    @Test
    fun `real Valkey outage on OAuth route returns 503 without calling Carelog BE`() {
        assertThat(redisConnectionFactory).isInstanceOf(LettuceConnectionFactory::class.java)

        val connection = redisConnectionFactory.reactiveConnection
        try {
            assertThat(connection.serverCommands().ping().block(Duration.ofSeconds(5))).isEqualTo("PONG")
        } finally {
            connection.close().block(Duration.ofSeconds(1))
        }

        valkey.stop()
        assertThat(valkey.isRunning).isFalse()

        upstreamRequestCount.set(0)
        webTestClient.post()
            .uri(authorizationPath)
            .header("X-Forwarded-For", "203.0.113.10")
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)

        assertThat(upstreamRequestCount.get()).isZero()
    }

    private companion object {
        const val authorizationPath = "/api/v1/auth/oauth/kakao/authorization"
        val valkey = GenericContainer<Nothing>(
            DockerImageName.parse(
                "valkey/valkey:8.1.0-alpine@sha256:93df46243b1d033a2d8f3ffc34122c6e431fbb315c5cff246ce6f77cf5a09f5b"
            )
        ).withExposedPorts(6379)
        val upstreamRequestCount = AtomicInteger()
        private val upstreamServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                upstreamRequestCount.incrementAndGet()
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerTestEndpoints(registry: DynamicPropertyRegistry) {
            if (!valkey.isRunning) valkey.start()
            registry.add("CARELOG_BE_URI") { "http://127.0.0.1:${upstreamServer.address.port}" }
            registry.add("REDIS_HOST") { valkey.host }
            registry.add("REDIS_PORT") { valkey.getMappedPort(6379) }
            registry.add("spring.data.redis.connect-timeout") { "500ms" }
        }

        @JvmStatic
        @AfterAll
        fun stopUpstreamServer() {
            upstreamServer.stop(0)
            if (valkey.isRunning) valkey.stop()
        }
    }
}
