package carelog.carelog.customer;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "carelog.carelog")
class CustomerBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule product_customer_dto는_auth_identity_구현에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("carelog.carelog.customer.web.dto")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "carelog.carelog.auth..",
                            "carelog.carelog.identity.."
                    )
                    .because("Product Customer DTO는 현재 UserPrincipal을 소비하되 Auth/Identity 구현을 노출하지 않는다");
}
