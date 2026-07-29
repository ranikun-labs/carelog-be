package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.ClientChannel;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationCommand;
import carelog.carelog.auth.app.port.productclient.ProductClientReader;
import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기존 Kakao 요청의 clientChannel을 각 Carelog 기본 Client로 해석하는 호환 계층이다.
 * Public DTO는 유지하며, 새 clientId API 계약은 별도 작업에서 확정한다.
 */
@Component
@RequiredArgsConstructor
public class OAuthProductClientCompatibilityResolver {

    public static final String DEFAULT_CARELOG_WEB_CLIENT_ID = "carelog-web";
    public static final String DEFAULT_CARELOG_MOBILE_CLIENT_ID = "carelog-mobile";

    private final ProductClientReader productClientReader;

    public RegisteredProductClient resolve(OAuthAuthorizationCommand command) {
        String clientId = command.clientId() == null
                ? resolveDefaultClientId(command.clientChannel())
                : command.clientId();
        RegisteredProductClient client = productClientReader.requireEnabled(clientId);

        if (client.product() != Product.CARELOG
                || client.channel() != resolveExpectedChannel(command.clientChannel())) {
            throw new CustomException(ExceptionStatus.INVALID_PRODUCT_CLIENT_CHANNEL_MAPPING);
        }
        return client;
    }

    private String resolveDefaultClientId(ClientChannel channel) {
        return switch (channel) {
            case WEB -> DEFAULT_CARELOG_WEB_CLIENT_ID;
            case MOBILE -> DEFAULT_CARELOG_MOBILE_CLIENT_ID;
        };
    }

    private ProductClientChannel resolveExpectedChannel(ClientChannel channel) {
        return switch (channel) {
            case WEB -> ProductClientChannel.WEB;
            case MOBILE -> ProductClientChannel.MOBILE;
        };
    }
}
