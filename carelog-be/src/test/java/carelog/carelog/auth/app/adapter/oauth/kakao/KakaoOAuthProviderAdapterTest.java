package carelog.carelog.auth.app.adapter.oauth.kakao;

import carelog.carelog.auth.app.adapter.oauth.kakao.dto.KakaoUserResponse;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationRequest;
import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.auth.app.port.oauth.OAuthPrincipal;
import carelog.carelog.auth.app.port.oauth.OAuthProviderException;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthTokenGrant;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoOAuthProviderAdapterTest {

    @Test
    void authorization_URL에는_필수값과_PKCE만_포함하고_이메일_scope는_요청하지_않는다() {
        KakaoOAuthProviderAdapter adapter = adapter();

        URI url = adapter.buildAuthorizationUrl(new OAuthAuthorizationRequest(
                "kakao", URI.create("https://app.example.com/callback"), "state-value", "challenge", "S256", null
        ));

        assertThat(UriComponentsBuilder.fromUri(url).build().getQueryParams())
                .containsEntry("client_id", java.util.List.of("test-client"))
                .containsEntry("redirect_uri", java.util.List.of("https://app.example.com/callback"))
                .containsEntry("response_type", java.util.List.of("code"))
                .containsEntry("state", java.util.List.of("state-value"))
                .containsEntry("code_challenge", java.util.List.of("challenge"))
                .containsEntry("code_challenge_method", java.util.List.of("S256"));
        assertThat(url.toString()).doesNotContain("scope=", "nonce=");
    }

    @Test
    void Kakao_ID는_정규_10진수_providerSubject로_변환한다() {
        KakaoOAuthProviderAdapter adapter = adapterWithUserId(123456789L);

        OAuthPrincipal principal = adapter.fetchPrincipal(new OAuthTokenGrant("provider-token", null, Instant.EPOCH), state());

        assertThat(principal).isEqualTo(new OAuthPrincipal("kakao", "123456789", null, false, null));
    }

    @Test
    void Kakao_ID가_없으면_인증에_실패한다() {
        KakaoOAuthProviderAdapter adapter = adapterWithUserId(null);

        assertThatThrownBy(() -> adapter.fetchPrincipal(new OAuthTokenGrant("provider-token", null, Instant.EPOCH), state()))
                .isInstanceOf(OAuthProviderException.class)
                .extracting("reason").isEqualTo(OAuthLoginResult.FailureReason.PRINCIPAL_UNVERIFIED);
    }

    @Test
    void Kakao_DTO에는_개인정보와_refresh_token_필드가_없다() {
        assertThat(KakaoUserResponse.class.getRecordComponents()).extracting(component -> component.getName()).containsExactly("id");
        assertThat(carelog.carelog.auth.app.adapter.oauth.kakao.dto.KakaoTokenResponse.class.getRecordComponents())
                .extracting(component -> component.getName()).containsExactly("accessToken", "expiresIn");
    }

    private KakaoOAuthProviderAdapter adapter() { return adapterWithUserId(1L); }
    private KakaoOAuthProviderAdapter adapterWithUserId(Long id) {
        KakaoOAuthProperties properties = new KakaoOAuthProperties();
        properties.setClientId("test-client");
        properties.setAuthorizationUri("https://kauth.example/authorize");
        properties.setTokenUri("https://kauth.example/token");
        properties.setUserInfoUri("https://kapi.example/user/me");
        KakaoOAuthApiClient client = new KakaoOAuthApiClient(RestClient.create(), properties, new KakaoOAuthErrorMapper()) {
            @Override public KakaoUserResponse fetchUser(String accessToken) { return new KakaoUserResponse(id); }
        };
        return new KakaoOAuthProviderAdapter(client, properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }
    private OAuthStateRecord state() {
        return new OAuthStateRecord("kakao", URI.create("https://app.example.com/callback"), "/", "verifier", null, Instant.EPOCH);
    }
}
