package carelog.carelog.auth.app.adapter.oauth.kakao;

import carelog.carelog.auth.app.port.oauth.OAuthProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;

class KakaoOAuthApiClientRetryTest {
    @Test void connect_실패후_UserInfo는_한번만_재시도한다() {
        RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(2), method(HttpMethod.GET)).andRespond(request -> { throw new ResourceAccessException("connect", new ConnectException()); });
        KakaoOAuthApiClient client = client(builder.build());
        assertThatThrownBy(() -> client.fetchUser("token")).isInstanceOf(OAuthProviderException.class);
        server.verify();
    }
    @Test void timeout_Token은_재시도하지_않는다() {
        RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(1), method(HttpMethod.POST)).andRespond(request -> { throw new ResourceAccessException("timeout", new SocketTimeoutException()); });
        assertThatThrownBy(() -> client(builder.build()).exchangeCode("code", java.net.URI.create("https://app/callback"), null)).isInstanceOf(OAuthProviderException.class);
        server.verify();
    }
    private KakaoOAuthApiClient client(RestClient restClient) {
        KakaoOAuthProperties p = new KakaoOAuthProperties(); p.setClientId("id"); p.setAuthorizationUri("https://a.example"); p.setTokenUri("https://t.example"); p.setUserInfoUri("https://u.example");
        return new KakaoOAuthApiClient(restClient, p, new KakaoOAuthErrorMapper());
    }
}
