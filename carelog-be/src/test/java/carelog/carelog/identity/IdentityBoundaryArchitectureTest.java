package carelog.carelog.identity;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Identity Foundation 경계(Phase A)를 회귀로부터 고정하는 Architecture Test.
 *
 * <p>패키지 기준:
 * <ul>
 *   <li>Identity Application 코어: {@code carelog.carelog.identity.app} (직속)</li>
 *   <li>Identity Port: {@code carelog.carelog.identity.app.port}</li>
 *   <li>Identity 영속화: {@code carelog.carelog.identity.domain} (PlatformAccount/PasswordCredential/ExternalIdentity)</li>
 *   <li>Carelog Enrollment 코어: {@code carelog.carelog.user.app} (직속, UserServiceImpl 등)</li>
 *   <li>CRM: {@code carelog.carelog.user.domain} 등 {@code carelog.carelog.user..}</li>
 * </ul>
 * Auth 내부 경계(4개 규칙)는 {@code AuthBoundaryArchitectureTest}가 그대로 고정한다 — 이 클래스는 건드리지 않는다.
 */
@AnalyzeClasses(packages = "carelog.carelog")
class IdentityBoundaryArchitectureTest {

    /**
     * 규칙 1 — Identity Core/Port는 Carelog User(CRM) Entity/Repository를 노출·의존하지 않는다.
     *
     * <p>Identity는 로그인 가능한 Principal(Stable Account ID/Account Status/Password Credential/
     * External Identity)만 소유한다. organizationId/role/managerType/name 등 CRM 업무 속성은
     * Identity가 알 필요가 없다.
     */
    @ArchTest
    static final ArchRule identity_core_port는_carelog_user_entity_repository에_의존하지_않는다 =
            noClasses()
                    .that().resideInAnyPackage(
                            "carelog.carelog.identity.app",
                            "carelog.carelog.identity.app.port..")
                    .should().dependOnClassesThat().resideInAnyPackage("carelog.carelog.user..")
                    .because("Identity Core/Port는 Carelog User(CRM) Entity/Repository에 의존하지 않는다");

    /**
     * 규칙 2 — Carelog Enrollment 코어(UserServiceImpl 등)는 Credential/External Identity
     * 영속화를 직접 소유하지 않는다. 승인된 {@code IdentityAccountRegistrationPort}로만 조합한다.
     */
    @ArchTest
    static final ArchRule carelog_enrollment_core는_identity_영속화를_직접_소유하지_않는다 =
            noClasses()
                    .that().resideInAPackage("carelog.carelog.user.app")
                    .should().dependOnClassesThat().resideInAPackage("carelog.carelog.identity.domain..")
                    .because("Carelog Enrollment 코어는 승인된 IdentityAccountRegistrationPort로만 Identity Account를 생성해야 한다");
}
