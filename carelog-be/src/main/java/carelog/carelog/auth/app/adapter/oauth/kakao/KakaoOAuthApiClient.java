package carelog.carelog.auth.app.adapter.oauth.kakao;

import carelog.carelog.auth.app.adapter.oauth.kakao.dto.KakaoTokenResponse;
import carelog.carelog.auth.app.adapter.oauth.kakao.dto.KakaoUserResponse;
import carelog.carelog.auth.app.port.oauth.OAuthProviderException;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.URI;
import java.util.Optional;

/** Kakao token 및 user-info endpoint의 HTTP 왕복을 캡슐화한다. */
@Component
@Conditional(KakaoOAuthConfiguredCondition.class)
public class KakaoOAuthApiClient {

    private final RestClient restClient;
    private final KakaoOAuthProperties properties;
    private final KakaoOAuthErrorMapper errorMapper;

    public KakaoOAuthApiClient(RestClient kakaoOAuthRestClient, KakaoOAuthProperties properties,
                               KakaoOAuthErrorMapper errorMapper) {
        this.restClient = kakaoOAuthRestClient;
        this.properties = properties;
        this.errorMapper = errorMapper;
    }

    public KakaoTokenResponse exchangeCode(String code, URI redirectUri, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.getClientId());
        form.add("redirect_uri", redirectUri.toString());
        form.add("code", code);
        optional(properties.getClientSecret()).ifPresent(value -> form.add("client_secret", value));
        if (codeVerifier != null) form.add("code_verifier", codeVerifier);
        try {
            KakaoTokenResponse response = restClient.post().uri(properties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw errorMapper.codeExchangeFailure(null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw errorMapper.tokenFailure(ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            throw errorMapper.unavailable(ex);
        } catch (OAuthProviderException ex) { throw ex; }
        catch (RuntimeException ex) { throw errorMapper.codeExchangeFailure(ex); }
    }

    public KakaoUserResponse fetchUser(String accessToken) {
        try {
            return userRequest(accessToken);
        } catch (ResourceAccessException firstFailure) {
            if (!hasConnectCause(firstFailure)) throw errorMapper.unavailable(firstFailure);
            return fetchUserRetry(accessToken);
        } catch (RestClientResponseException ex) {
            throw errorMapper.userInfoFailure(ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw errorMapper.principalUnverified(ex);
        }
    }

    private KakaoUserResponse fetchUserRetry(String accessToken) {
        try { return userRequest(accessToken); }
        catch (ResourceAccessException ex) { throw errorMapper.unavailable(ex); }
        catch (RestClientResponseException ex) { throw errorMapper.userInfoFailure(ex.getStatusCode().value()); }
        catch (RuntimeException ex) { throw errorMapper.principalUnverified(ex); }
    }

    private KakaoUserResponse userRequest(String accessToken) {
        return restClient.get().uri(properties.getUserInfoUri()).headers(h -> h.setBearerAuth(accessToken))
                .retrieve().body(KakaoUserResponse.class);
    }
    private boolean hasConnectCause(Throwable error) { for (Throwable t = error; t != null; t = t.getCause()) if (t instanceof ConnectException) return true; return false; }
    private Optional<String> optional(String value) { return value == null || value.isBlank() ? Optional.empty() : Optional.of(value); }
}
