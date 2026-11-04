package carelog.gateway.filter

import carelog.gateway.blacklist.RedisBlacklistService
import carelog.gateway.config.GatewayConfig
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * JwtGlobalFilter의 현재 인증 경계 동작을 고정하는 Characterization Test.
 * Phase 1B: 실제 JwtVerifier(실제 JJWT) + Mock ReactiveStringRedisTemplate을 감싼 실제
 * RedisBlacklistService + MockServerWebExchange + SAM 구현 GatewayFilterChain으로 구성.
 * 실제 Redis/네트워크/전체 Spring Context를 사용하지 않는다.
 *
 * 테스트 secret은 characterization 전용이며 실제 운영 값과 무관하다.
 */
class JwtGlobalFilterCharacterizationTest {

    private val secret = "carelog-gateway-characterization-test-secret-key-0123456789-abcdef"
    private val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    private val internalSecret = "test-internal-secret"

    private val orgId = "11111111-1111-1111-1111-111111111111"
    private val publicId = "22222222-2222-2222-2222-222222222222"
    private val subject = "manager@example.com"

    private lateinit var redisTemplate: org.springframework.data.redis.core.ReactiveStringRedisTemplate
    private lateinit var blacklistService: RedisBlacklistService
    private lateinit var filter: JwtGlobalFilter

    // 현재 downstream으로 전달된 exchange를 캡처한다. null이면 chain이 호출되지 않은 것.
    private var capturedExchange: ServerWebExchange? = null
    private val chain = GatewayFilterChain { exchange ->
        capturedExchange = exchange
        Mono.empty()
    }

    @BeforeEach
    fun setUp() {
        redisTemplate = mock(org.springframework.data.redis.core.ReactiveStringRedisTemplate::class.java)
        blacklistService = RedisBlacklistService(redisTemplate)
        val gatewayConfig = GatewayConfig(
            publicPaths = listOf(
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/users/managers"
            ),
            internalSecret = internalSecret
        )
        filter = JwtGlobalFilter(JwtVerifier(secret), blacklistService, gatewayConfig)
        capturedExchange = null
    }

    // ---------- fixture helpers ----------

    private fun exchange(
        method: HttpMethod,
        path: String,
        headers: Map<String, String> = emptyMap()
    ): MockServerWebExchange {
        val builder = MockServerHttpRequest.method(method, path)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return MockServerWebExchange.from(builder.build())
    }

    private fun validToken(): String = Jwts.builder()
        .subject(subject)
        .claim("organizationId", orgId)
        .claim("role", "MANAGER")
        .claim("publicId", publicId)
        .expiration(Date(4102444800000L)) // 2100-01-01, 실시간 기준 미만료
        .signWith(key)
        .compact()

    private fun expiredToken(): String = Jwts.builder()
        .subject(subject)
        .issuedAt(Date(0))
        .expiration(Date(1000))
        .signWith(key)
        .compact()

    private fun headerOf(name: String): String? =
        capturedExchange!!.request.headers.getFirst(name)

    // ---------- Public Path ----------

    @DisplayName("공개 경로는 JWT 검증 없이 통과하고 X-Gateway-Secret만 주입하며 인입 identity 헤더는 제거한다")
    @Test
    fun publicPath_skipsAuthenticationAndInjectsGatewaySecretOnly() {
        val ex = exchange(HttpMethod.POST, "/api/v1/auth/login", mapOf("X-User-Id" to "spoofed"))

        filter.filter(ex, chain).block()

        assertThat(capturedExchange).isNotNull()
        assertThat(headerOf("X-Gateway-Secret")).isEqualTo(internalSecret)
        assertThat(headerOf("X-User-Id")).isNull()
        verifyNoInteractions(redisTemplate)
    }

    @DisplayName("공개 경로 prefix에 걸리는 확장 경로(login-extra)도 현재 startsWith 구현상 공개로 처리된다 (Risk)")
    @Test
    fun prefixMatchingPublicPath_followsCurrentBehavior() {
        val ex = exchange(HttpMethod.POST, "/api/v1/auth/login-extra")

        filter.filter(ex, chain).block()

        assertThat(capturedExchange).isNotNull()
        assertThat(headerOf("X-Gateway-Secret")).isEqualTo(internalSecret)
        verifyNoInteractions(redisTemplate)
    }

    // ---------- Authorization ----------

    @DisplayName("보호 경로에서 Authorization 헤더가 없으면 401을 반환하고 downstream을 호출하지 않는다")
    @Test
    fun protectedPath_missingAuthorization_returnsUnauthorizedWithoutDownstream() {
        val ex = exchange(HttpMethod.GET, "/api/v1/patients")

        filter.filter(ex, chain).block()

        assertThat(ex.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(capturedExchange).isNull()
        verifyNoInteractions(redisTemplate)
    }

    @DisplayName("보호 경로에서 Bearer 형식이 잘못되면 401을 반환하고 JWT 검증/ downstream을 호출하지 않는다")
    @ParameterizedTest
    @ValueSource(strings = ["Bearer", "bearer valid-token", "NotBearer x", " Bearer x"])
    fun protectedPath_malformedBearerHeader_returnsUnauthorizedWithoutDownstream(authHeader: String) {
        val ex = exchange(HttpMethod.GET, "/api/v1/patients", mapOf("Authorization" to authHeader))

        filter.filter(ex, chain).block()

        assertThat(ex.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(capturedExchange).isNull()
        verifyNoInteractions(redisTemplate)
    }

    @DisplayName("'Bearer '(빈 토큰)은 현재 IllegalArgumentException이 catch되지 않고 전파된다 (Risk)")
    @Test
    fun protectedPath_emptyBearerToken_propagatesIllegalArgumentException() {
        val ex = exchange(HttpMethod.GET, "/api/v1/patients", mapOf("Authorization" to "Bearer "))

        // filter 내부 catch(JwtException)가 IllegalArgumentException을 잡지 못해 동기적으로 전파된다.
        assertThatThrownBy { filter.filter(ex, chain) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(capturedExchange).isNull()
    }

    // ---------- JWT validation 실패 ----------

    @DisplayName("서명/형식이 잘못된 JWT는 401을 반환하고 downstream을 호출하지 않는다")
    @Test
    fun invalidToken_returnsUnauthorizedWithoutDownstream() {
        val ex = exchange(HttpMethod.GET, "/api/v1/patients", mapOf("Authorization" to "Bearer not-a-jwt"))

        filter.filter(ex, chain).block()

        assertThat(ex.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(capturedExchange).isNull()
        verifyNoInteractions(redisTemplate)
    }

    @DisplayName("만료된 JWT는 401을 반환하고 downstream을 호출하지 않는다")
    @Test
    fun expiredToken_returnsUnauthorizedWithoutDownstream() {
        val ex = exchange(HttpMethod.GET, "/api/v1/patients", mapOf("Authorization" to "Bearer ${expiredToken()}"))

        filter.filter(ex, chain).block()

        assertThat(ex.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(capturedExchange).isNull()
        verifyNoInteractions(redisTemplate)
    }

    // ---------- Blacklist ----------

    @DisplayName("blacklist에 등록된 JWT는 401을 반환하고 downstream을 호출하지 않는다")
    @Test
    fun blacklistedToken_returnsUnauthorizedWithoutDownstream() {
        val token = validToken()
        `when`(redisTemplate.hasKey("blacklist:$token")).thenReturn(Mono.just(true))
        val ex = exchange(HttpMethod.GET, "/api/v1/patients", mapOf("Authorization" to "Bearer $token"))

        filter.filter(ex, chain).block()

        assertThat(ex.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(capturedExchange).isNull()
    }

    @DisplayName("blacklist 조회가 실패하면 현재 구현상 에러가 그대로 전파되고 downstream을 호출하지 않는다 (Risk)")
    @Test
    fun blacklistLookupFailure_propagatesErrorWithoutDownstream() {
        val token = validToken()
        `when`(redisTemplate.hasKey("blacklist:$token"))
            .thenReturn(Mono.error(RuntimeException("redis down")))
        val ex = exchange(HttpMethod.GET, "/api/v1/patients", mapOf("Authorization" to "Bearer $token"))

        assertThatThrownBy { filter.filter(ex, chain).block() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("redis down")
        assertThat(capturedExchange).isNull()
    }

    // ---------- 정상 JWT + Identity Header 주입 ----------

    @DisplayName("정상/미등록 JWT는 현재 identity 헤더를 주입하고 downstream을 호출한다")
    @Test
    fun validToken_notBlacklisted_injectsCurrentIdentityHeadersAndContinues() {
        val token = validToken()
        `when`(redisTemplate.hasKey("blacklist:$token")).thenReturn(Mono.just(false))
        val ex = exchange(HttpMethod.GET, "/api/v1/patients", mapOf("Authorization" to "Bearer $token"))

        filter.filter(ex, chain).block()

        assertThat(capturedExchange).isNotNull()
        assertThat(headerOf("X-User-Id")).isEqualTo(subject)
        assertThat(headerOf("X-Organization-Id")).isEqualTo(orgId)
        assertThat(headerOf("X-Role")).isEqualTo("MANAGER")
        assertThat(headerOf("X-Public-Id")).isEqualTo(publicId)
        assertThat(headerOf("X-Gateway-Secret")).isEqualTo(internalSecret)
    }

    @DisplayName("클라이언트가 스푸핑한 identity 헤더는 제거되고 Gateway가 계산한 값으로 대체된다")
    @Test
    fun validToken_withClientSuppliedIdentityHeaders_replacesThemWithGatewayValues() {
        val token = validToken()
        `when`(redisTemplate.hasKey("blacklist:$token")).thenReturn(Mono.just(false))
        val ex = exchange(
            HttpMethod.GET, "/api/v1/patients",
            mapOf(
                "Authorization" to "Bearer $token",
                "X-User-Id" to "attacker",
                "X-Organization-Id" to "evil-org",
                "X-Role" to "ADMIN",
                "X-Public-Id" to "evil-public",
                "X-Gateway-Secret" to "leaked-secret"
            )
        )

        filter.filter(ex, chain).block()

        assertThat(capturedExchange).isNotNull()
        assertThat(headerOf("X-User-Id")).isEqualTo(subject)
        assertThat(headerOf("X-Organization-Id")).isEqualTo(orgId)
        assertThat(headerOf("X-Role")).isEqualTo("MANAGER")
        assertThat(headerOf("X-Public-Id")).isEqualTo(publicId)
        assertThat(headerOf("X-Gateway-Secret")).isEqualTo(internalSecret)
    }

    @DisplayName("선택 claim이 없으면 organizationId/publicId 헤더는 생략되고 role은 빈 문자열로 주입된다 (현재 비대칭 동작)")
    @Test
    fun validToken_missingOptionalClaims_omitsOrgAndPublicIdButSetsEmptyRole() {
        val token = Jwts.builder()
            .subject(subject)
            .expiration(Date(4102444800000L))
            .signWith(key)
            .compact()
        `when`(redisTemplate.hasKey("blacklist:$token")).thenReturn(Mono.just(false))
        val ex = exchange(HttpMethod.GET, "/api/v1/patients", mapOf("Authorization" to "Bearer $token"))

        filter.filter(ex, chain).block()

        assertThat(capturedExchange).isNotNull()
        assertThat(headerOf("X-User-Id")).isEqualTo(subject)
        assertThat(headerOf("X-Organization-Id")).isNull()
        assertThat(headerOf("X-Role")).isEqualTo("")
        assertThat(headerOf("X-Public-Id")).isNull()
        assertThat(headerOf("X-Gateway-Secret")).isEqualTo(internalSecret)
    }
}
