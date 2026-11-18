package carelog.carelog.auth.app.adapter.oauth.kakao;

import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.auth.app.port.oauth.OAuthBoundProductClient;
import carelog.carelog.auth.app.port.oauth.OAuthProviderException;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.oauth.OAuthTokenGrant;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.URLDecoder;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoOAuthApiClientHttpTest {
    private static final String ACCESS_TOKEN_MARKER = "kakao-access-token-sensitive-marker";
    private static final String AUTHORIZATION_CODE_MARKER = "authorization-code-sensitive-marker";
    private static final String CLIENT_SECRET_MARKER = "client-secret-sensitive-marker";
    private static final String RAW_RESPONSE_MARKER = "raw-response-sensitive-marker";
    private static final List<String> SENSITIVE_MARKERS = List.of(
            ACCESS_TOKEN_MARKER, AUTHORIZATION_CODE_MARKER, CLIENT_SECRET_MARKER, RAW_RESPONSE_MARKER
    );

    private MockWebServer server;
    private KakaoOAuthApiClient client;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer(); server.start();
        client = client(CLIENT_SECRET_MARKER);
    }
    @AfterEach void tearDown() throws Exception { server.shutdown(); }

    @Test void token_form과_PKCE를_한번만_전송한다() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"token\",\"expires_in\":60}").addHeader("Content-Type", "application/json"));
        assertThat(client.exchangeCode(AUTHORIZATION_CODE_MARKER, URI.create("https://app.example/callback"), "verifier").accessToken()).isEqualTo("token");
        RecordedRequest request = server.takeRequest();
        Map<String, String> form = form(request);
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Content-Type")).contains("application/x-www-form-urlencoded");
        assertThat(form.keySet()).contains("grant_type", "client_id", "client_secret", "redirect_uri", "code", "code_verifier");
        assertThat(hasValue(form, "client_secret", CLIENT_SECRET_MARKER)).as("client secret form value").isTrue();
        assertThat(hasValue(form, "code", AUTHORIZATION_CODE_MARKER)).as("authorization code form value").isTrue();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test void token_선택_파라미터는_설정되지_않으면_전송하지_않는다() throws Exception {
        client = client(null);
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"token\"}").addHeader("Content-Type", "application/json"));

        client.exchangeCode(AUTHORIZATION_CODE_MARKER, URI.create("https://app.example/callback"), null);

        Map<String, String> form = form(server.takeRequest());
        assertThat(form.keySet()).contains("grant_type", "client_id", "redirect_uri", "code")
                .doesNotContain("client_secret", "code_verifier");
        assertThat(hasValue(form, "code", AUTHORIZATION_CODE_MARKER)).as("authorization code form value").isTrue();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500})
    void token_4xx와_5xx는_안전하게_변환되고_재시도하지_않는다(int status) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody(RAW_RESPONSE_MARKER));

        OAuthProviderException exception = catchThrowableOfType(
                () -> client.exchangeCode(AUTHORIZATION_CODE_MARKER, URI.create("https://app.example/callback"), null),
                OAuthProviderException.class
        );

        assertThat(exception.reason()).isEqualTo(status >= 500
                ? OAuthLoginResult.FailureReason.PROVIDER_UNAVAILABLE
                : OAuthLoginResult.FailureReason.CODE_EXCHANGE_FAILED);
        assertThat(exception).isNotInstanceOf(RestClientResponseException.class);
        assertThat(messagesExpose(exception, SENSITIVE_MARKERS)).as("provider failure messages must redact sensitive values").isFalse();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
    @Test void token_오류는_안전하게_변환되고_재시도하지_않는다() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody(RAW_RESPONSE_MARKER));
        assertThatThrownBy(() -> client.exchangeCode(AUTHORIZATION_CODE_MARKER, URI.create("https://app.example/callback"), null))
                .isInstanceOf(OAuthProviderException.class).extracting("reason").isEqualTo(OAuthLoginResult.FailureReason.PROVIDER_UNAVAILABLE);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
    @Test void token_누락과_malformed는_안전하게_실패한다() {
        server.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));
        assertThatThrownBy(() -> client.exchangeCode("code", URI.create("https://app.example/callback"), null)).isInstanceOf(OAuthProviderException.class);
        server.enqueue(new MockResponse().setBody("{").addHeader("Content-Type", "application/json"));
        assertThatThrownBy(() -> client.exchangeCode("code", URI.create("https://app.example/callback"), null)).isInstanceOf(OAuthProviderException.class);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }
    @Test void userInfo는_Bearer와_id만_처리한다() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"id\":123456789}").addHeader("Content-Type", "application/json"));
        assertThat(client.fetchUser("provider-token").id()).isEqualTo(123456789L);
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer provider-token");
    }
    @Test void userInfo_4xx는_재시도하지_않는다() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody(RAW_RESPONSE_MARKER));
        OAuthProviderException exception = catchThrowableOfType(() -> client.fetchUser(ACCESS_TOKEN_MARKER), OAuthProviderException.class);
        assertThat(exception.reason()).isEqualTo(OAuthLoginResult.FailureReason.PRINCIPAL_UNVERIFIED);
        assertThat(messagesExpose(exception, SENSITIVE_MARKERS)).as("provider failure messages must redact sensitive values").isFalse();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
    @Test void userInfo_ID_누락과_malformed는_안전하게_실패한다() {
        server.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));
        assertThatThrownBy(() -> adapter().fetchPrincipal(new OAuthTokenGrant("provider-token", null, Instant.EPOCH), state())).isInstanceOf(OAuthProviderException.class);
        server.enqueue(new MockResponse().setBody("{").addHeader("Content-Type", "application/json"));
        assertThatThrownBy(() -> adapter().fetchPrincipal(new OAuthTokenGrant("provider-token", null, Instant.EPOCH), state())).isInstanceOf(OAuthProviderException.class);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }
    private KakaoOAuthProviderAdapter adapter() {
        KakaoOAuthProperties properties = new KakaoOAuthProperties(); properties.setClientId("client");
        properties.setAuthorizationUri(server.url("/authorize").toString()); properties.setTokenUri(server.url("/token").toString()); properties.setUserInfoUri(server.url("/user").toString());
        return new KakaoOAuthProviderAdapter(client, properties, Clock.systemUTC());
    }
    private OAuthStateRecord state() {
        return new OAuthStateRecord(
                OAuthStateRecord.CURRENT_VERSION,
                "kakao",
                URI.create("https://app.example/callback"),
                new OAuthBoundProductClient("carelog-web", Product.CARELOG, ProductClientChannel.WEB),
                "/",
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300)
        );
    }
    private KakaoOAuthApiClient client(String clientSecret) {
        KakaoOAuthProperties properties = new KakaoOAuthProperties();
        properties.setClientId("client"); properties.setClientSecret(clientSecret);
        properties.setAuthorizationUri(server.url("/authorize").toString());
        properties.setTokenUri(server.url("/token").toString());
        properties.setUserInfoUri(server.url("/user").toString());
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build());
        factory.setReadTimeout(Duration.ofMillis(200));
        return new KakaoOAuthApiClient(RestClient.builder().requestFactory(factory).build(), properties, new KakaoOAuthErrorMapper());
    }
    private Map<String, String> form(RecordedRequest request) {
        return Arrays.stream(request.getBody().readUtf8().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8)
                ));
    }
    private boolean hasValue(Map<String, String> form, String key, String value) { return value.equals(form.get(key)); }
    private boolean messagesExpose(Throwable exception, List<String> markers) {
        Predicate<String> containsMarker = message -> markers.stream().anyMatch(message::contains);
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current.getMessage() != null && containsMarker.test(current.getMessage())) return true;
        }
        return false;
    }
}
