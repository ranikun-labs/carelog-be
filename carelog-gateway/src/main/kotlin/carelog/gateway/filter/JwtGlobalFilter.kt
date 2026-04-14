package carelog.gateway.filter

import carelog.gateway.blacklist.RedisBlacklistService
import io.jsonwebtoken.JwtException
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class JwtGlobalFilter(
    private val jwtVerifier: JwtVerifier,
    private val blacklistService: RedisBlacklistService,
    @param:Value("\${gateway.public-paths}")
    private val publicPaths: List<String>,
    @param:Value("\${gateway.internal-secret}")
    private val internalSecret: String
) : GlobalFilter, Ordered {

    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain
    ): Mono<Void> {
        val request = exchange.request
        val path = request.uri.path

        // 헤더 스푸핑 방어 - 인입 요청의 X-User-* 헤더 제거
        val sanitizedExchange = exchange.mutate()
            .request {
                it.headers { headers ->
                    headers.remove("X-User-Id")
                    headers.remove("X-Organization-Id")
                    headers.remove("X-Role")
                    headers.remove("X-Public-Id")
                    headers.remove("X-Gateway-Secret")
                }
            }
            .build()

        // 공개 경로는 필터 통과
        if (publicPaths.any { path.startsWith(it)}) {
            return chain.filter(sanitizedExchange)
        }

        // Authorization 헤더에서 Bearer 토큰 추출
        val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        val token = authHeader.removePrefix("Bearer ")

        val claims = try {
            jwtVerifier.verifyAndGetClaims(token)
        } catch (e: JwtException) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        // Redis Blacklist 조회
        return blacklistService.isBlacklisted(token).flatMap { isBlacklisted ->
            if (isBlacklisted) {
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                exchange.response.setComplete()
            } else {
                val mutatedExchange = sanitizedExchange.mutate()
                    .request { it.headers { headers ->
                        headers.set("X-User-Id", claims.subject)
                        headers.set("X-Organization-Id", claims["organizationId"]?.toString() ?: "")
                        headers.set("X-Role", claims["role"]?.toString() ?: "")
                        headers.set("X-Public-Id", claims["publicId"]?.toString() ?: "")
                        headers.set("X-Gateway-Secret", internalSecret)
                    }}
                    .build()
                chain.filter(mutatedExchange)
            }
        }
    }

    override fun getOrder(): Int = -1
}