package carelog.carelog.auth.app.oauth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Provider와 Product Client 조합별 OAuth callback URI 환경 설정이다. */
@Component
@ConfigurationProperties("oauth")
@Getter
@Setter
public class OAuthProviderCallbackProperties {

    private Map<String, Map<String, String>> providerCallbacks = new LinkedHashMap<>();

    public String find(String provider, String clientId) {
        return providerCallbacks.getOrDefault(provider, Map.of()).get(clientId);
    }
}
