package carelog.carelog.auth.app.productclient;

import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClient;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.auth.domain.ProductClientRepository;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductClientRegistryServiceTest {

    @Mock private ProductClientRepository productClientRepository;

    @Test
    void 등록되고_활성화된_Client는_Product와_Channel을_반환한다() {
        when(productClientRepository.findByClientId("carelog-web")).thenReturn(Optional.of(
                ProductClient.create("carelog-web", Product.CARELOG, ProductClientChannel.WEB, true)
        ));

        RegisteredProductClient result = service().requireEnabled("carelog-web");

        assertThat(result).isEqualTo(new RegisteredProductClient(
                "carelog-web", Product.CARELOG, ProductClientChannel.WEB
        ));
    }

    @Test
    void 빈_ClientId는_거부한다() {
        assertThatThrownBy(() -> service().requireEnabled(" "))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_PRODUCT_CLIENT_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {" carelog", "carelog ", "care log", "CARELOG", "carelog!", "cárelog"})
    void ClientId_형식_위반은_조회_전에_거부한다(String invalidClientId) {
        assertThatThrownBy(() -> service().requireEnabled(invalidClientId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_PRODUCT_CLIENT_ID);
    }

    @Test
    void null_ClientId는_거부한다() {
        assertThatThrownBy(() -> service().requireEnabled(null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_PRODUCT_CLIENT_ID);
    }

    @Test
    void 알수없는_Client는_fail_closed로_거부한다() {
        when(productClientRepository.findByClientId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().requireEnabled("unknown"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.UNKNOWN_PRODUCT_CLIENT);
    }

    @Test
    void 비활성_Client는_fail_closed로_거부한다() {
        when(productClientRepository.findByClientId("disabled")).thenReturn(Optional.of(
                ProductClient.create("disabled", Product.CARELOG, ProductClientChannel.WEB, false)
        ));

        assertThatThrownBy(() -> service().requireEnabled("disabled"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DISABLED_PRODUCT_CLIENT);
    }

    private ProductClientRegistryService service() {
        return new ProductClientRegistryService(productClientRepository);
    }
}
