package carelog.gateway.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest

class OAuthPublicRequestMatcherTest {

    private val matcher = OAuthPublicRequestMatcher()

    @Test
    fun `allows only the exact Kakao authorization POST`() {
        assertThat(matcher.matches(request(HttpMethod.POST, "/api/v1/auth/oauth/kakao/authorization"))).isTrue()
        assertThat(matcher.matches(request(HttpMethod.GET, "/api/v1/auth/oauth/kakao/authorization"))).isFalse()
        assertThat(matcher.matches(request(HttpMethod.POST, "/api/v1/auth/oauth/kakao/authorization/extra"))).isFalse()
    }

    @Test
    fun `allows only the exact Kakao exchange POST`() {
        assertThat(matcher.matches(request(HttpMethod.POST, "/api/v1/auth/oauth/kakao/exchange"))).isTrue()
        assertThat(matcher.matches(request(HttpMethod.PUT, "/api/v1/auth/oauth/kakao/exchange"))).isFalse()
        assertThat(matcher.matches(request(HttpMethod.POST, "/api/v1/auth/oauth/kakao/link"))).isFalse()
    }

    private fun request(method: HttpMethod, path: String) =
        MockServerHttpRequest.method(method, path).build()
}
