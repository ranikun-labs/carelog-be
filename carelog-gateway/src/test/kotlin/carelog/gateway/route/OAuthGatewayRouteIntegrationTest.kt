package carelog.gateway.route

import carelog.gateway.CarelogGatewayApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono

@SpringBootTest(
    classes = [CarelogGatewayApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "CARELOG_BE_URI=http://localhost:8080",
        "CARELOG_RAG_URI=http://localhost:8081",
        "GATEWAY_INTERNAL_SECRET=test-gateway-secret",
        "JWT_SECRET_KEY=test-jwt-secret-key-that-is-long-enough-for-hmac-sha"
    ]
)
class OAuthGatewayRouteIntegrationTest(
    @Autowired private val routeLocator: RouteLocator
) {

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
    }
}
