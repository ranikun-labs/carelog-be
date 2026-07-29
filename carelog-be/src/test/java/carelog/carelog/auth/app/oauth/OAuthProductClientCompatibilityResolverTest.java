package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.ClientChannel;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationCommand;
import carelog.carelog.auth.app.port.productclient.ProductClientReader;
import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthProductClientCompatibilityResolverTest {

    @Test
    void 기존_WEB_요청은_Carelog_WEB_기본_Client로_해석한다() {
        ProductClientReader reader = clientId -> new RegisteredProductClient(
                clientId, Product.CARELOG, ProductClientChannel.WEB
        );

        RegisteredProductClient result = new OAuthProductClientCompatibilityResolver(reader).resolve(
                new OAuthAuthorizationCommand("kakao", ClientChannel.WEB, "/")
        );

        assertThat(result.clientId()).isEqualTo(OAuthProductClientCompatibilityResolver.DEFAULT_CARELOG_WEB_CLIENT_ID);
    }

    @Test
    void 기존_MOBILE_요청은_Carelog_MOBILE_기본_Client로_해석한다() {
        ProductClientReader reader = clientId -> new RegisteredProductClient(
                clientId, Product.CARELOG, ProductClientChannel.MOBILE
        );

        RegisteredProductClient result = new OAuthProductClientCompatibilityResolver(reader).resolve(
                new OAuthAuthorizationCommand("kakao", ClientChannel.MOBILE, "/")
        );

        assertThat(result.clientId()).isEqualTo(OAuthProductClientCompatibilityResolver.DEFAULT_CARELOG_MOBILE_CLIENT_ID);
        assertThat(result.channel()).isEqualTo(ProductClientChannel.MOBILE);
    }

    @Test
    void 등록_Client와_요청_Channel이_일치하지_않으면_거부한다() {
        ProductClientReader reader = clientId -> new RegisteredProductClient(
                clientId, Product.CARELOG, ProductClientChannel.WEB
        );

        assertThatThrownBy(() -> new OAuthProductClientCompatibilityResolver(reader).resolve(
                new OAuthAuthorizationCommand("kakao", ClientChannel.MOBILE, "/")
        )).isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_PRODUCT_CLIENT_CHANNEL_MAPPING);
    }
}
