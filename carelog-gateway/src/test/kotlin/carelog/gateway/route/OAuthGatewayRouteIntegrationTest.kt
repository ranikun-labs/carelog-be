package carelog.gateway.route

import carelog.gateway.CarelogGatewayApplication
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@AutoConfigureWebTestClient
@SpringBootTest(
    classes = [CarelogGatewayApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "CARELOG_RAG_URI=http://localhost:8081",
        "GATEWAY_INTERNAL_SECRET=test-gateway-secret",
        "JWT_SECRET_KEY=test-jwt-secret-key-that-is-long-enough-for-hmac-sha"
    ]
)
class OAuthGatewayRouteIntegrationTest(
    @Autowired private val routeLocator: RouteLocator,
    @Autowired private val webTestClient: WebTestClient,
    @Autowired @Qualifier(RedisRateLimiter.REDIS_SCRIPT_NAME)
    private val redisScript: RedisScript<List<Long>>
) {

    @MockBean
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Test
    fun `OAuth POST routes precede and match before the general API route`() {
        val routes = routeLocator.routes.collectList().block()!!

        assertThat(routes.map(Route::getId)).containsSubsequence(
            "kakao-oauth-authorization",
            "kakao-oauth-exchange",
            "carelog-be"
        )
        assertThat(route(routes, "kakao-oauth-authorization").matches(HttpMethod.POST, authorizationPath)).isTrue()
        assertThat(route(routes, "kakao-oauth-exchange").matches(HttpMethod.POST, exchangePath)).isTrue()
        assertThat(route(routes, "carelog-be").matches(HttpMethod.GET, "/api/v1/patients")).isTrue()
    }

    @Test
    fun `OAuth routes reject wrong methods and similar paths while general route still matches`() {
        val routes = routeLocator.routes.collectList().block()!!
        val authorizationRoute = route(routes, "kakao-oauth-authorization")
        val exchangeRoute = route(routes, "kakao-oauth-exchange")
        val generalRoute = route(routes, "carelog-be")

        assertThat(authorizationRoute.matches(HttpMethod.GET, authorizationPath)).isFalse()
        assertThat(exchangeRoute.matches(HttpMethod.PUT, exchangePath)).isFalse()
        assertThat(authorizationRoute.matches(HttpMethod.POST, "$authorizationPath/extra")).isFalse()
        assertThat(generalRoute.matches(HttpMethod.GET, authorizationPath)).isTrue()
    }


    @Test
    fun `Redis failure on the configured OAuth route returns 503 without calling Carelog BE`() {
        val clientIp = "203.0.113.10"
        val rateLimitKeys = listOf(
            "request_rate_limiter.{kakao-oauth-authorization.$clientIp}.tokens",
            "request_rate_limiter.{kakao-oauth-authorization.$clientIp}.timestamp"
        )
        val rateLimitArgs = listOf("5", "10", "", "1")
        `when`(redisTemplate.execute(redisScript, rateLimitKeys, rateLimitArgs))
            .thenReturn(Flux.error<List<Long>>(IllegalStateException("test Valkey failure")))
        upstreamRequestCount.set(0)

        webTestClient.post()
            .uri(authorizationPath)
            .header("X-Forwarded-For", clientIp)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)

        verify(redisTemplate).execute(redisScript, rateLimitKeys, rateLimitArgs)
        assertThat(upstreamRequestCount.get()).isZero()
    }

    private fun route(routes: List<Route>, id: String): Route =
        routes.single { it.id == id }

    private fun Route.matches(method: HttpMethod, path: String): Boolean =
        Mono.from(
            predicate.apply(
                MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build())
            )
        ).block()!!

    private companion object {
        const val authorizationPath = "/api/v1/auth/oauth/kakao/authorization"
        const val exchangePath = "/api/v1/auth/oauth/kakao/exchange"
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
        fun registerBackendUri(registry: DynamicPropertyRegistry) {
            registry.add("CARELOG_BE_URI") { "http://127.0.0.1:${upstreamServer.address.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stopUpstreamServer() {
            upstreamServer.stop(0)
        }
    }
}
