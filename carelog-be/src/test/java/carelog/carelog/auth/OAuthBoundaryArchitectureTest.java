package carelog.carelog.auth;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** OAuth Core가 provider·identity·persistence 구현 세부사항을 알지 않도록 고정한다. */
@AnalyzeClasses(packages = "carelog.carelog")
class OAuthBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule oauth_core는_identity_redis_adapter_구현에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("carelog.carelog.auth.app.oauth..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "carelog.carelog.identity.domain..",
                            "org.springframework.data.redis..",
                            "carelog.carelog.auth.app.adapter.."
                    )
                    .because("OAuth Core는 Port로만 identity와 Redis에 접근해야 한다");

    @ArchTest
    static final ArchRule oauth_port는_Entity_ORM_타입을_노출하지_않는다 =
            noClasses()
                    .that().resideInAPackage("carelog.carelog.auth.app.port.oauth..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "carelog.carelog.identity.domain..",
                            "carelog.carelog.user..",
                            "jakarta.persistence..",
                            "org.hibernate..",
                            "org.springframework.data.."
                    )
                    .because("OAuth Port 계약은 identity Entity와 ORM 구현을 노출하면 안 된다");

    @ArchTest
    static final ArchRule oauth_core는_이메일_조회_repository에_의존하지_않는다 =
            noClasses()
                    .that().resideInAPackage("carelog.carelog.auth.app.oauth..")
                    .should().dependOnClassesThat().resideInAPackage("carelog.carelog.user.domain..")
                    .because("OAuth 로그인은 이메일로 Account를 조회하거나 자동 병합하면 안 된다");

    @ArchTest
    static final ArchRule oauth_core와_port는_Kakao_HTTP_구현에_의존하지_않는다 =
            noClasses().that().resideInAnyPackage("carelog.carelog.auth.app.oauth..", "carelog.carelog.auth.app.port.oauth..")
                    .should().dependOnClassesThat().resideInAPackage("carelog.carelog.auth.app.adapter.oauth.kakao..")
                    .because("Provider-neutral OAuth Core와 Port는 Kakao HTTP 구현을 알면 안 된다");

    @ArchTest
    static final ArchRule Kakao_DTO는_Web에_노출되지_않는다 =
            noClasses().that().resideInAPackage("carelog.carelog.auth.web..")
                    .should().dependOnClassesThat().resideInAPackage("carelog.carelog.auth.app.adapter.oauth.kakao.dto..");
}
