package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;

/** 서버 설정에서 provider와 Product Client에 맞는 callback URI만 선택한다. */
@Component
public class OAuthRedirectUriResolver {

    private static final String CARELOG_WEB_CLIENT_ID = "carelog-web";
    private static final String CARELOG_MOBILE_CLIENT_ID = "carelog-mobile";

    private final OAuthProviderCallbackProperties callbackProperties;
    private final Environment environment;

    @Autowired
    public OAuthRedirectUriResolver(OAuthProviderCallbackProperties callbackProperties, Environment environment) {
        this.callbackProperties = callbackProperties;
        this.environment = environment;
    }

    OAuthRedirectUriResolver(Environment environment) {
        this(new OAuthProviderCallbackProperties(), environment);
    }

    public URI resolve(String provider, RegisteredProductClient client) {
        if (client == null || client.clientId() == null || client.clientId().isBlank()) {
            throw new CustomException(ExceptionStatus.UNSUPPORTED_CLIENT_CHANNEL);
        }

        String providerCode = OAuthProviderRegistry.normalize(provider);
        String configuredUri = callbackProperties.find(providerCode, client.clientId());
        if (configuredUri == null) {
            configuredUri = legacyConfiguredUri(providerCode, client);
        }
        if (configuredUri == null || configuredUri.isBlank()) {
            throw new CustomException(ExceptionStatus.UNSUPPORTED_CLIENT_CHANNEL);
        }

        try {
            URI uri = URI.create(configuredUri);
            if (!uri.isAbsolute() || uri.getScheme() == null || uri.getScheme().isBlank()) {
                throw new IllegalArgumentException("OAuth callback URI must be absolute");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ExceptionStatus.UNSUPPORTED_CLIENT_CHANNEL);
        }
    }

    private String legacyConfiguredUri(String providerCode, RegisteredProductClient client) {
        String legacyChannel = legacyChannel(client);
        if (legacyChannel == null) {
            return null;
        }
        return environment.getProperty("oauth.redirect-uris." + providerCode + "." + legacyChannel);
    }

    private String legacyChannel(RegisteredProductClient client) {
        if (CARELOG_WEB_CLIENT_ID.equals(client.clientId()) && client.channel() == ProductClientChannel.WEB) {
            return ProductClientChannel.WEB.name();
        }
        if (CARELOG_MOBILE_CLIENT_ID.equals(client.clientId()) && client.channel() == ProductClientChannel.MOBILE) {
            return ProductClientChannel.MOBILE.name();
        }
        return null;
    }
}
