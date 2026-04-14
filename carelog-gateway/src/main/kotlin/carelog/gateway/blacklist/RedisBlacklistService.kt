package carelog.gateway.blacklist

import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class RedisBlacklistService(
    private val redisTemplate: ReactiveStringRedisTemplate
) {
    // carelog-be에서 로그아웃 시 "blacklist:{token}" 키로 Redis에 저장함
    // 여기서는 해당 키 존재 여부만 확인 — true면 블랙리스트된 토큰
    fun isBlacklisted(token: String): Mono<Boolean> =
        redisTemplate.hasKey("blacklist:$token")
}