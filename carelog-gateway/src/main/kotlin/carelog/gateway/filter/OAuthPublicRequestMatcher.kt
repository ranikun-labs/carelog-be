package carelog.gateway.filter

import org.springframework.http.HttpMethod
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component

@Component
class OAuthPublicRequestMatcher {

    fun matches(request: ServerHttpRequest): Boolean =
        request.method == HttpMethod.POST && request.uri.path in kakaoOAuthPaths

    companion object {
        private val kakaoOAuthPaths = setOf(
            "/api/v1/auth/oauth/kakao/authorization",
            "/api/v1/auth/oauth/kakao/exchange"
        )
    }
}
