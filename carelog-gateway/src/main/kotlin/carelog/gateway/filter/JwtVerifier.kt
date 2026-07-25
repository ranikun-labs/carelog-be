package carelog.gateway.filter

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets


@Component
class JwtVerifier (
    @Value("\${jwt.secret-key}") secretKey: String
){
    // HS256 서명 검증용 키 - 문자열을 바이트로 변환해 SecretKey 객체 생성
    private val key = Keys.hmacShaKeyFor(secretKey.toByteArray(StandardCharsets.UTF_8))

    // 토큰 검증 + Claims(페이로드) 변환
    // 서명 불일치, 만료 등이면 JwtException - 호출부에서 catch해서 401처리
    fun verifyAndGetClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}