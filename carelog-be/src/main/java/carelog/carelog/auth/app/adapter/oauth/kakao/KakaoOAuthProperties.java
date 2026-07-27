package carelog.carelog.auth.app.adapter.oauth.kakao;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/** Kakao OAuth 통신에 필요한 설정을 한 곳에서 검증한다. */
@Getter
@Validated
@ConfigurationProperties("carelog.auth.oauth.kakao")
public class KakaoOAuthProperties {

    @NotBlank
    private String clientId;
    private String clientSecret;
    @NotBlank
    private String authorizationUri;
    @NotBlank
    private String tokenUri;
    @NotBlank
    private String userInfoUri;
    private String scope;

    public void setClientId(String clientId) { this.clientId = clientId; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public void setAuthorizationUri(String authorizationUri) { this.authorizationUri = validUri(authorizationUri); }
    public void setTokenUri(String tokenUri) { this.tokenUri = validUri(tokenUri); }
    public void setUserInfoUri(String userInfoUri) { this.userInfoUri = validUri(userInfoUri); }
    public void setScope(String scope) { this.scope = scope; }

    private String validUri(String value) {
        URI uri = URI.create(value);
        if (!uri.isAbsolute() || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Kakao OAuth URI must be absolute");
        }
        return value;
    }

    @Override
    public String toString() {
        return "KakaoOAuthProperties[clientId=***, clientSecret=***, authorizationUri=" + authorizationUri
                + ", tokenUri=" + tokenUri + ", userInfoUri=" + userInfoUri + ", scope=" + scope + "]";
    }
}
