package carelog.gateway.filter

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * JwtVerifier의 현재 JWT 파싱/예외 계약을 고정하는 Characterization Test.
 * Phase 1B: 실제 JJWT 0.12.3 구현을 그대로 사용하며 Mock을 쓰지 않는다.
 *
 * 테스트 secret은 characterization 전용이며 실제 운영 값과 무관하다.
 */
class JwtVerifierCharacterizationTest {

    private val secret = "carelog-gateway-characterization-test-secret-key-0123456789-abcdef"
    private val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    private val verifier = JwtVerifier(secret)

    @DisplayName("유효한 서명 토큰은 subject와 claim을 그대로 반환한다")
    @Test
    fun verifyAndGetClaims_validToken_returnsClaims() {
        val token = Jwts.builder()
            .subject("manager@example.com")
            .claim("organizationId", "11111111-1111-1111-1111-111111111111")
            .claim("role", "MANAGER")
            .claim("publicId", "22222222-2222-2222-2222-222222222222")
            .signWith(key)
            .compact()

        val claims = verifier.verifyAndGetClaims(token)

        assertThat(claims.subject).isEqualTo("manager@example.com")
        assertThat(claims["organizationId"]).isEqualTo("11111111-1111-1111-1111-111111111111")
        assertThat(claims["role"]).isEqualTo("MANAGER")
        assertThat(claims["publicId"]).isEqualTo("22222222-2222-2222-2222-222222222222")
    }

    @DisplayName("형식이 깨진 토큰은 MalformedJwtException(JwtException 하위)을 던진다")
    @ParameterizedTest
    @ValueSource(strings = ["not-a-jwt", "a.b.c"])
    fun verifyAndGetClaims_malformedToken_throwsMalformedJwtException(token: String) {
        val thrown = catchThrowable { verifier.verifyAndGetClaims(token) }

        assertThat(thrown).isInstanceOf(MalformedJwtException::class.java)
        assertThat(thrown).isInstanceOf(JwtException::class.java)
    }

    @DisplayName("만료된 토큰은 ExpiredJwtException(JwtException 하위)을 던진다")
    @Test
    fun verifyAndGetClaims_expiredToken_throwsExpiredJwtException() {
        val expiredToken = Jwts.builder()
            .subject("manager@example.com")
            .issuedAt(Date(0))
            .expiration(Date(1000)) // epoch + 1s, 실시간 기준 확실히 만료
            .signWith(key)
            .compact()

        val thrown = catchThrowable { verifier.verifyAndGetClaims(expiredToken) }

        assertThat(thrown).isInstanceOf(ExpiredJwtException::class.java)
        assertThat(thrown).isInstanceOf(JwtException::class.java)
    }

    @DisplayName("서명이 변조된 토큰은 SignatureException(JwtException 하위)을 던진다")
    @Test
    fun verifyAndGetClaims_tamperedSignature_throwsSignatureException() {
        val valid = Jwts.builder().subject("manager@example.com").signWith(key).compact()
        val tampered = valid.substring(0, valid.length - 4) + "AAAA"

        val thrown = catchThrowable { verifier.verifyAndGetClaims(tampered) }

        assertThat(thrown).isInstanceOf(SignatureException::class.java)
        assertThat(thrown).isInstanceOf(JwtException::class.java)
    }

    @DisplayName("빈 문자열/공백 토큰은 JwtException이 아니라 IllegalArgumentException을 던진다 (필터 catch 경계 밖)")
    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun verifyAndGetClaims_emptyOrBlankToken_throwsIllegalArgumentExceptionNotJwtException(token: String) {
        val thrown = catchThrowable { verifier.verifyAndGetClaims(token) }

        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
        // 현재 JwtGlobalFilter는 catch(JwtException)만 하므로 이 예외는 잡히지 않고 전파된다 — Risk 고정
        assertThat(thrown).isNotInstanceOf(JwtException::class.java)
    }
}
