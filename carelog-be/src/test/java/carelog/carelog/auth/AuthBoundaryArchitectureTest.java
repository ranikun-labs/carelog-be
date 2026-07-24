package carelog.carelog.auth;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Auth 내부 경계(Identity Port 분리, 544fc7a) 를 회귀로부터 고정하는 Architecture Test.
 *
 * <p>제품 동작이 아니라 <b>의존 방향</b>만 검증한다. 이번 경계에서 실제로 필요한 최소 규칙만 두고,
 * 전체 Repository/Entity 접근을 과도하게 막지 않는다. (예: {@code CustomUserDetailsService}는
 * CRM({@code user.domain})에 의존하는 것이 정당한 보안 브릿지이므로 규칙 대상이 아니다.)
 *
 * <p>패키지 기준:
 * <ul>
 *   <li>Application 코어: {@code carelog.carelog.auth.app} (직속 클래스만)</li>
 *   <li>Port: {@code carelog.carelog.auth.app.port}</li>
 *   <li>Adapter: {@code carelog.carelog.auth.app.adapter}</li>
 *   <li>Auth 영속화: {@code carelog.carelog.auth.domain} (RefreshToken / RefreshTokenRepository)</li>
 *   <li>CRM: {@code carelog.carelog.user} (User Entity / UserRepository / UserRole 등)</li>
 * </ul>
 * Gateway 전용 구현체는 별도 모듈({@code carelog-gateway}, Kotlin)에 있어 be 클래스패스에 없으므로
 * 구조적으로 의존 자체가 불가능하다 → 별도 규칙을 두지 않는다.
 */
@AnalyzeClasses(packages = "carelog.carelog")
class AuthBoundaryArchitectureTest {

    /**
     * 규칙 1 — AuthServiceImpl 은 승인된 내부 Port 뒤로 숨긴 것들에 직접 의존하면 안 된다.
     *
     * <p>금지 대상: CRM Entity/Repository({@code user..}), Legacy Adapter 구현체({@code auth.app.adapter..}),
     * 구체적 Auth 영속화({@code auth.domain..}: RefreshToken Entity/Repository).
     * 이 결과 AuthServiceImpl 의 CRM/세션/자격증명 접근은 CredentialPort / CRMIdentityProjectionPort /
     * TokenSessionPort 를 통해서만 이뤄진다.
     */
    @ArchTest
    static final ArchRule authServiceImpl_은_CRM_영속화_어댑터에_직접_의존하지_않는다 =
            noClasses()
                    .that().haveSimpleName("AuthServiceImpl")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "carelog.carelog.user..",              // CRM Entity / Repository
                            "carelog.carelog.auth.app.adapter..",  // Legacy Adapter 구현체
                            "carelog.carelog.auth.domain.."        // 구체 Persistence(RefreshToken/Repository)
                    )
                    .because("AuthServiceImpl은 승인된 내부 Port로만 CRM/세션/자격증명에 접근해야 한다");

    /**
     * 규칙 2 — Port 인터페이스는 구현 세부 타입을 경계 밖으로 노출하면 안 된다.
     *
     * <p>Port 의 입력·출력은 내부 계약 DTO / VO / primitive·identifier 여야 한다.
     * 따라서 CRM Entity/Repository, Auth 영속화 타입, ORM/Spring Data 전용 타입에 의존하면 안 된다.
     */
    @ArchTest
    static final ArchRule port는_Entity_Repository_ORM_타입을_노출하지_않는다 =
            noClasses()
                    .that().resideInAPackage("carelog.carelog.auth.app.port..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "carelog.carelog.user..",            // CRM JPA Entity / Repository
                            "carelog.carelog.auth.domain..",     // RefreshToken Entity / Repository
                            "jakarta.persistence..",             // ORM 전용 타입
                            "org.hibernate..",                   // ORM 전용 타입
                            "org.springframework.data.."         // Repository 추상 타입
                    )
                    .because("Port 계약은 내부 DTO/VO/primitive만 노출해야 한다");

    /**
     * 규칙 3 — Auth Application(코어 + Port) 은 Adapter 구현체에 역으로 의존하면 안 된다.
     *
     * <p>의존 방향은 Application → Port(인터페이스), Adapter → Port(구현) 이어야 한다.
     * Adapter 는 Repository/Entity 에 의존할 수 있으나, 그 역방향은 금지한다.
     * {@code resideInAPackage("...app")}(후행 {@code ..} 없음)는 하위 패키지(port/adapter)를 제외하고
     * app 직속 클래스만 대상으로 한다.
     */
    @ArchTest
    static final ArchRule application계층은_adapter_구현체에_역의존하지_않는다 =
            noClasses()
                    .that().resideInAnyPackage(
                            "carelog.carelog.auth.app",          // Application 코어(직속)
                            "carelog.carelog.auth.app.port..")   // Port 계층
                    .should().dependOnClassesThat().resideInAPackage("carelog.carelog.auth.app.adapter..")
                    .because("의존 방향은 Application/Port → Adapter가 아니라 Adapter → Port여야 한다");

    /**
     * 규칙 4 — Auth Application 코어와 Port 는 구체 Redis Persistence 타입에 직접 의존하면 안 된다.
     *
     * <p>Access Token blacklist 는 {@code TokenBlacklistPort} 뒤로 숨기고, {@code StringRedisTemplate} 등
     * {@code org.springframework.data.redis..} 구현 타입은 Redis Adapter({@code auth.app.adapter..})
     * 안에만 가둔다. {@code resideInAPackage("...app")}(후행 {@code ..} 없음)로 코어 직속 클래스
     * ({@code AuthServiceImpl} 포함)만 대상으로 하며, Adapter 하위 패키지는 제외한다.
     * (Port 의 Redis 타입 노출 금지는 규칙 2의 {@code org.springframework.data..} 로 이미 커버되나,
     * 경계 의도를 명시적으로 고정하기 위해 코어를 함께 대상에 둔다.)
     */
    @ArchTest
    static final ArchRule application코어와_port는_구체_redis_타입에_직접_의존하지_않는다 =
            noClasses()
                    .that().resideInAnyPackage(
                            "carelog.carelog.auth.app",          // Application 코어(직속: AuthServiceImpl 등)
                            "carelog.carelog.auth.app.port..")   // Port 계약
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.data.redis..")
                    .because("구체 Redis Persistence(StringRedisTemplate 등)는 Redis Adapter에만 가둬야 한다");
}
