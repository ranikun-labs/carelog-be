package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.OAuthProviderPort;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 등록된 provider port를 소문자 provider code로 해석한다. */
@Component
public class OAuthProviderRegistry {

    private final Map<String, OAuthProviderPort> providers;

    public OAuthProviderRegistry(List<OAuthProviderPort> providerPorts) {
        Map<String, OAuthProviderPort> resolved = new HashMap<>();
        for (OAuthProviderPort providerPort : providerPorts) {
            String code = normalize(providerPort.providerCode());
            if (resolved.putIfAbsent(code, providerPort) != null) {
                throw new IllegalStateException("Duplicate OAuth provider registration");
            }
        }
        this.providers = Map.copyOf(resolved);
    }

    public OAuthProviderPort resolve(String providerCode) {
        OAuthProviderPort provider = providers.get(normalize(providerCode));
        if (provider == null) {
            throw new CustomException(ExceptionStatus.UNSUPPORTED_OAUTH_PROVIDER);
        }
        return provider;
    }

    public static String normalize(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return "";
        }
        return providerCode.trim().toLowerCase(Locale.ROOT);
    }
}
