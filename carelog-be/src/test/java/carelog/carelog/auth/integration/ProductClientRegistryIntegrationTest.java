package carelog.carelog.auth.integration;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.auth.app.port.productclient.Product;
import carelog.carelog.auth.app.port.productclient.ProductClientChannel;
import carelog.carelog.auth.domain.ProductClient;
import carelog.carelog.auth.domain.ProductClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class ProductClientRegistryIntegrationTest {

    @Autowired private ProductClientRepository productClientRepository;

    @Test
    void Flyway가_초기_Carelog_WEB_Client를_등록하고_Enum과_enabled를_보존한다() {
        assertThat(productClientRepository.findByClientId("carelog-web"))
                .hasValueSatisfying(client -> {
                    assertThat(client.getProduct()).isEqualTo(Product.CARELOG);
                    assertThat(client.getChannel()).isEqualTo(ProductClientChannel.WEB);
                    assertThat(client.isEnabled()).isTrue();
                });

        ProductClient disabled = productClientRepository.saveAndFlush(
                ProductClient.create("disabled-client", Product.DEV_HARNESS, ProductClientChannel.ANDROID, false)
        );
        assertThat(productClientRepository.findByClientId("disabled-client"))
                .hasValueSatisfying(saved -> {
                    assertThat(saved.getProduct()).isEqualTo(Product.DEV_HARNESS);
                    assertThat(saved.getChannel()).isEqualTo(ProductClientChannel.ANDROID);
                    assertThat(saved.isEnabled()).isFalse();
                    assertThat(saved.getId()).isEqualTo(disabled.getId());
                });
    }
}
