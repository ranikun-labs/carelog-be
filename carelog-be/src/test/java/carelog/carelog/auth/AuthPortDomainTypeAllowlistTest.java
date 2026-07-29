package carelog.carelog.auth;

import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClient;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.auth.domain.ProductClientRepository;
import carelog.carelog.auth.domain.RefreshToken;
import carelog.carelog.auth.domain.RefreshTokenRepository;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/** Port Auth Domain Allowlist가 Entity·Repository까지 허용하지 않는지 집중 검증한다. */
class AuthPortDomainTypeAllowlistTest {

    private final ClassFileImporter importer = new ClassFileImporter();

    @Test
    void Product와_ProductClientChannel만_Port_Domain_값_타입으로_허용한다() {
        ArchRule rule = allowlistRuleFor(허용_Port_유사_타입.class);

        assertThatCode(() -> rule.check(importer.importClasses(
                허용_Port_유사_타입.class, Product.class, ProductClientChannel.class
        ))).doesNotThrowAnyException();

        assertThat(AuthBoundaryArchitectureTest.isAllowedPortDomainType(importer.importClass(Product.class))).isTrue();
        assertThat(AuthBoundaryArchitectureTest.isAllowedPortDomainType(importer.importClass(ProductClientChannel.class)))
                .isTrue();
    }

    @Test
    void Auth_Entity와_Repository는_실제_Allowlist_Condition에서_거부한다() {
        ArchRule rule = allowlistRuleFor(금지_Port_유사_타입.class);

        assertThatThrownBy(() -> rule.check(importer.importClasses(
                금지_Port_유사_타입.class,
                ProductClient.class,
                RefreshToken.class,
                ProductClientRepository.class,
                RefreshTokenRepository.class
        ))).isInstanceOf(AssertionError.class)
                .hasMessageContaining(ProductClient.class.getName())
                .hasMessageContaining(RefreshToken.class.getName())
                .hasMessageContaining(ProductClientRepository.class.getName())
                .hasMessageContaining(RefreshTokenRepository.class.getName());

        assertThat(AuthBoundaryArchitectureTest.isAllowedPortDomainType(importer.importClass(ProductClient.class))).isFalse();
        assertThat(AuthBoundaryArchitectureTest.isAllowedPortDomainType(importer.importClass(RefreshToken.class))).isFalse();
        assertThat(AuthBoundaryArchitectureTest.isAllowedPortDomainType(importer.importClass(ProductClientRepository.class)))
                .isFalse();
        assertThat(AuthBoundaryArchitectureTest.isAllowedPortDomainType(importer.importClass(RefreshTokenRepository.class)))
                .isFalse();
    }

    private ArchRule allowlistRuleFor(Class<?> fixtureType) {
        return classes()
                .that().haveFullyQualifiedName(fixtureType.getName())
                .should(AuthBoundaryArchitectureTest.허용된_Auth_Domain_값_타입만_허용);
    }

    private static class 허용_Port_유사_타입 {
        private Product product;
        private ProductClientChannel channel;
    }

    private static class 금지_Port_유사_타입 {
        private ProductClient productClient;
        private RefreshToken refreshToken;
        private ProductClientRepository productClientRepository;
        private RefreshTokenRepository refreshTokenRepository;
    }
}
