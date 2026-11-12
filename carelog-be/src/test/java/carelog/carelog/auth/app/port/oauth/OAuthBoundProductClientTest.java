package carelog.carelog.auth.app.port.oauth;

import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OAuthBoundProductClientTest {

    @Test
    void 검증된_Product_Client_snapshot의_세_필드를_그대로_보존한다() {
        OAuthBoundProductClient snapshot = new OAuthBoundProductClient(
                "carelog-web", Product.CARELOG, ProductClientChannel.WEB
        );

        assertThat(snapshot.clientId()).isEqualTo("carelog-web");
        assertThat(snapshot.product()).isEqualTo(Product.CARELOG);
        assertThat(snapshot.channel()).isEqualTo(ProductClientChannel.WEB);
    }

    @Test
    void clientId가_blank이면_생성하지_않는다() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OAuthBoundProductClient(
                " ", Product.CARELOG, ProductClientChannel.WEB
        ));
    }

    @Test
    void 필수_snapshot_필드가_null이면_생성하지_않는다() {
        assertThatNullPointerException().isThrownBy(() -> new OAuthBoundProductClient(
                null, Product.CARELOG, ProductClientChannel.WEB
        ));
        assertThatNullPointerException().isThrownBy(() -> new OAuthBoundProductClient(
                "carelog-web", null, ProductClientChannel.WEB
        ));
        assertThatNullPointerException().isThrownBy(() -> new OAuthBoundProductClient(
                "carelog-web", Product.CARELOG, null
        ));
    }
}
