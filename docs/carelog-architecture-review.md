# Carelog Backend Architecture Review

이번 문서는 현재 Carelog 백엔드의 JPA, Spring Security, Spring Boot 운영 측면을 점검한 결과와 개선 권장 사항을 정리한 자료입니다. 검사 대상은 `src/main/java` 및 `src/main/resources` 하위의 현재 소스 코드입니다.

## 1. JPA & 데이터 계층

- **엔티티 반환 금지·DTO 변환**  
  - `UserController`에서 `UserResponse` DTO를 반환하도록 구성되어 있어 기본 원칙은 준수되고 있습니다.
- **트랜잭션 전략**  
  - `UserServiceImpl`에 `@Transactional(readOnly = true)`가 클래스 레벨로 선언되어 읽기 성능 측면에서 긍정적입니다.  
  - 쓰기 전용 메서드는 개별적으로 `@Transactional`을 선언하여 더티 체킹이 정상 동작합니다.
- **감사(Auditing) 구성**  
  - `BaseEntity`와 `AuditConfig`가 이미 존재하며 `AuditorAwareImpl`도 구현되어 있습니다.  
  - `CarelogApplication`의 `@EnableJpaAuditing` 주석은 제거해도 무방합니다(구성 클래스에서 이미 활성화).
- **미흡한 부분**  
  - 글로벌 `default_batch_fetch_size` 설정이 없어 N+1 완화 전략이 비어 있습니다.  
  - Fetch Join, `@EntityGraph`, QueryDSL 기반의 리포지터리 확장이 존재하지 않아 동적/복잡 쿼리 대응력이 부족합니다.  
  - `IllegalArgumentException`을 비즈니스 예외로 사용하고 있어 에러 응답이 500으로 포장됩니다.

### JPA 개선 제안

1. `application-*.yml`에 `spring.jpa.properties.hibernate.default_batch_fetch_size=100` 등 전역 배치 크기를 명시하세요.  
2. 빈번한 조회에 대해 Fetch Join 또는 `@EntityGraph`를 도입하여 N+1 탐지를 최소화하고 테스트를 추가하세요.  
3. QueryDSL 의존성과 `JPAQueryFactory` 빈을 추가해 동적 쿼리 및 통계성 조회 요구를 대비하세요.  
4. 비즈니스 검증 실패에는 도메인 전용 예외(`DuplicateUserEmailException` 등)를 만들고, 전용 핸들러에서 4xx 코드로 매핑하세요.

## 2. Spring Security (인증/인가)

- **현재 상태**  
  - `SecurityConfig`는 모든 HTTP 요청을 `permitAll`하고 있어 인증·인가가 사실상 비활성화되어 있습니다.  
  - JWT 라이브러리가 `build.gradle`에 포함돼 있으나 토큰 발급·검증 로직, 필터, `AuthenticationProvider`가 없습니다.  
  - Refresh Token 관리, RTR(Refresh Token Rotation) 전략, CORS 정책과 연계된 보안 흐름이 없습니다.
- **장점**  
  - `BCryptPasswordEncoder`를 빈으로 제공하고 있어 비밀번호 암호화 준비는 되어 있습니다.

### Security 개선 제안

1. `SecurityFilterChain`을 재구성하여 다음을 포함하세요.  
   - JWT 인증 필터(before `UsernamePasswordAuthenticationFilter`)  
   - 세션 미사용(`sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)`)  
   - `/auth/**` 등 일부 엔드포인트만 허용하고 나머지는 권한 기반으로 제한
2. `AuthenticationProvider`·`UserDetailsService` 구현을 통해 사용자 조회 및 비밀번호 검증을 캡슐화하세요.  
3. Access/Refresh Token 발급, RTR, 블랙리스트 혹은 Redis 기반 관리 정책을 명확히 하고 테스트를 작성하세요.  
4. 메서드 레벨 보안(`@EnableMethodSecurity`, `@PreAuthorize`)을 활성화하여 세분화된 권한 제어를 적용하세요.  
5. 감사 로그(인증 성공/실패, 토큰 재발급 등)를 남겨 추적성을 확보하세요.

## 3. Spring Boot 운영 & 공통 인프라

- **테스트 코드**  
  - `contextLoads` 외의 단위/통합 테스트가 없습니다.  
  - 서비스·리포지터리 검증, JWT 인증 흐름 검증, 예외 응답 테스트 등이 필요합니다.
- **프로필 관리**  
  - 단일 `application.properties` 파일에서 모든 환경을 처리하고 있으며 `ddl-auto=update`, `show-sql=true`가 상시 활성화된 상태입니다.  
  - 프로필 분리를 통해 운영/개발 설정, DB 자격 증명, 로깅 레벨을 명확히 구분해야 합니다.
- **모니터링·로깅**  
  - Actuator 의존성과 구성, 구조화된 로깅, 보안 관련 감사 로그가 없습니다.  
  - `Slf4j`가 준비되어 있으나 사용자 정의 예외 처리와 연동된 로그 수준 조정이 필요합니다.
- **예외 처리**  
  - `GlobalExceptionHandler`가 모든 예외를 500으로 래핑합니다.  
  - Bean Validation, 비즈니스 예외, 인증/인가 예외 등을 각각 4xx/5xx로 구분하는 핸들러가 필요합니다.

### 운영 측 개선 제안

1. `application-local.yml`, `application-dev.yml`, `application-prod.yml`로 환경을 분리하고, 운영 환경에서 `ddl-auto=none`, `show-sql=false`를 기본값으로 설정하세요.  
2. Actuator(`spring-boot-starter-actuator`)를 도입하고 `/actuator/health`, `/actuator/metrics`를 모니터링 도구와 연동하세요.  
3. `logback-spring.xml`을 추가해 JSON/패턴 로깅, 보안 이벤트 로깅, 프로필별 로그 레벨을 구성하세요.  
4. `@RestControllerAdvice`를 확장하여 `MethodArgumentNotValidException`, 도메인 예외, 인증 예외에 대한 응답 포맷을 일관되게 유지하세요.  
5. CI 파이프라인에 테스트 실행을 통합하고, 핵심 구성 요소별 테스트 케이스를 작성하세요.

## 4. 긍정적인 요소

- DTO를 통해 엔티티 노출을 막고 있으며, `spring.jpa.open-in-view=false`가 설정되어 있어 API 계층에서의 Lazy Loading 문제를 방지하고 있습니다.  
- 감사(Auditing) 베이스 클래스와 `AuditorAware` 구현이 이미 준비되어 있어 추적성을 확보할 기반이 마련되어 있습니다.  
- `@Transactional(readOnly = true)`를 기본 적용해 조회 트랜잭션의 스냅샷 생성을 억제하고 있습니다.

## 5. 우선순위 로드맵

1. **보안 인프라 구축**  
   - JWT 인증/인가, 메서드 보안, Refresh Token 관리, 세분화된 SecurityFilterChain 구성
2. **예외 및 응답 정책 정립**  
   - 도메인 전용 예외, 글로벌 핸들러 고도화, 공통 에러 포맷
3. **JPA 성능 및 쿼리 전략 강화**  
   - 배치 페치, Fetch Join/EntityGraph, QueryDSL 도입, N+1 테스트
4. **운영 환경 정비**  
   - 프로필 분리, Actuator·로깅, 테스트 스위트 정비 및 CI 연동

위 단계에 따라 개선하면, 요구하신 “네카라쿠배” 수준의 JPA 숙련도, JWT 기반 보안, Spring Boot 운영 성숙도를 갖춘 백엔드로 발전시킬 수 있습니다.
