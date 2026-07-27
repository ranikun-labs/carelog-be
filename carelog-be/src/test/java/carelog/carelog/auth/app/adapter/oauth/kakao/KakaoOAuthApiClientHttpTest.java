package carelog.carelog.auth.app.adapter.oauth.kakao;

import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.auth.app.port.oauth.OAuthProviderException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoOAuthApiClientHttpTest {
    private MockWebServer server;
    private KakaoOAuthApiClient client;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer(); server.start();
        KakaoOAuthProperties properties = new KakaoOAuthProperties();
        properties.setClientId("client"); properties.setClientSecret("secret");
        properties.setAuthorizationUri(server.url("/authorize").toString());
        properties.setTokenUri(server.url("/token").toString());
        properties.setUserInfoUri(server.url("/user").toString());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build());
        factory.setReadTimeout(Duration.ofMillis(200));
        client = new KakaoOAuthApiClient(RestClient.builder().requestFactory(factory).build(), properties, new KakaoOAuthErrorMapper());
    }
    @AfterEach void tearDown() throws Exception { server.shutdown(); }

    @Test void token_form과_PKCE를_한번만_전송한다() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"token\",\"expires_in\":60}").addHeader("Content-Type", "application/json"));
        assertThat(client.exchangeCode("code", URI.create("https://app.example/callback"), "verifier").accessToken()).isEqualTo("token");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Content-Type")).contains("application/x-www-form-urlencoded");
        assertThat(request.getBody().readUtf8()).contains("grant_type=authorization_code", "client_id=client", "client_secret=secret", "code_verifier=verifier");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
    @Test void token_오류는_안전하게_변환되고_재시도하지_않는다() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("sensitive-body"));
        assertThatThrownBy(() -> client.exchangeCode("code", URI.create("https://app.example/callback"), null))
                .isInstanceOf(OAuthProviderException.class).extracting("reason").isEqualTo(OAuthLoginResult.FailureReason.PROVIDER_UNAVAILABLE);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
    @Test void userInfo는_Bearer와_id만_처리한다() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"id\":123456789}").addHeader("Content-Type", "application/json"));
        assertThat(client.fetchUser("provider-token").id()).isEqualTo(123456789L);
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer provider-token");
    }
    @Test void userInfo_4xx는_재시도하지_않는다() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("sensitive-body"));
        assertThatThrownBy(() -> client.fetchUser("provider-token")).isInstanceOf(OAuthProviderException.class)
                .extracting("reason").isEqualTo(OAuthLoginResult.FailureReason.PRINCIPAL_UNVERIFIED);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
