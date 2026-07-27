package carelog.carelog.auth.app.adapter.oauth.kakao;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

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
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);

    public void setClientId(String clientId) { this.clientId = clientId; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public void setAuthorizationUri(String authorizationUri) { this.authorizationUri = validUri(authorizationUri); }
    public void setTokenUri(String tokenUri) { this.tokenUri = validUri(tokenUri); }
    public void setUserInfoUri(String userInfoUri) { this.userInfoUri = validUri(userInfoUri); }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = validTimeout(connectTimeout); }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = validTimeout(readTimeout); }

    private String validUri(String value) {
        URI uri = URI.create(value);
        if (!uri.isAbsolute() || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Kakao OAuth URI must be absolute");
        }
        return value;
    }
    private Duration validTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException("Kakao OAuth timeout must be positive");
        return value;
    }

    @Override
    public String toString() {
        return "KakaoOAuthProperties[clientId=***, clientSecret=***, authorizationUri=" + authorizationUri
                + ", tokenUri=" + tokenUri + ", userInfoUri=" + userInfoUri + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
