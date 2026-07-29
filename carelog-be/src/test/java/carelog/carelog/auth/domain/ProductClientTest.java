package carelog.carelog.auth.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProductClientTest {

    @Test
    void 한_글자_ClientId와_정확히_백_글자_ClientId를_허용한다() {
        String hundredCharacters = "a" + "a".repeat(99);

        assertThat(ProductClient.create("a", Product.CARELOG, ProductClientChannel.WEB, true).getClientId())
                .isEqualTo("a");
        assertThat(ProductClient.create(hundredCharacters, Product.CARELOG, ProductClientChannel.WEB, true).getClientId())
                .isEqualTo(hundredCharacters);
    }

    @Test
    void 점_밑줄_하이픈을_포함한_ClientId를_허용한다() {
        assertThat(ProductClient.create("carelog.web_client-1", Product.CARELOG, ProductClientChannel.WEB, true).getClientId())
                .isEqualTo("carelog.web_client-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", " carelog", "carelog ", "care log", "CARELOG", "cárelog", "carelog!"})
    void 허용되지_않은_ClientId는_거부한다(String invalidClientId) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                ProductClient.create(invalidClientId, Product.CARELOG, ProductClientChannel.WEB, true));
    }

    @Test
    void null_및_백한글자_ClientId는_거부한다() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                ProductClient.create(null, Product.CARELOG, ProductClientChannel.WEB, true));
        assertThatIllegalArgumentException().isThrownBy(() ->
                ProductClient.create("a".repeat(101), Product.CARELOG, ProductClientChannel.WEB, true));
    }
}
