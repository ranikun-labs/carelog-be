package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.ClientChannel;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;

/** 서버 설정에서 provider와 client channel에 맞는 redirect URI만 선택한다. */
@Component
@RequiredArgsConstructor
public class OAuthRedirectUriResolver {

    private final Environment environment;

    public URI resolve(String provider, ClientChannel clientChannel) {
        if (clientChannel == null) {
            throw new CustomException(ExceptionStatus.UNSUPPORTED_CLIENT_CHANNEL);
        }

        String property = "oauth.redirect-uris." + OAuthProviderRegistry.normalize(provider) + "." + clientChannel.name();
        String configuredUri = environment.getProperty(property);
        if (configuredUri == null || configuredUri.isBlank()) {
            throw new CustomException(ExceptionStatus.UNSUPPORTED_CLIENT_CHANNEL);
        }

        return URI.create(configuredUri);
    }
}
