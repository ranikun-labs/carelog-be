# Carelog Backend - Project Instructions

> 이 문서는 Carelog 프로젝트의 핵심 아키텍처 원칙과 개발 가이드를 요약합니다. 상세 내용은 `/docs` 폴더 참조.

---

## 1. 프로젝트 개요

**Carelog**: 치료사-고객 관계 관리 및 상담 일지 작성 플랫폼 (Spring Boot 백엔드)

**개발자 역할**: Tech Lead (NestJS/FastAPI 경험, DDD/DI 숙련, Spring 핵심 원리 이해)

**AI 협업 모델**: Claude는 스캐폴딩과 보일러플레이트 생성을 지원. Tech Lead가 핵심 비즈니스 로직을 작성.

---

## 2. 아키텍처 원칙

### 2.1. 헥사고날 아키텍처 (Ports & Adapters)

```
user/
├─ domain/                    # 순수 POJO (JPA 비의존)
│  └─ User.java
├─ application/
│  ├─ port/
│  │  ├─ in/                  # Inbound Port (Service 인터페이스)
│  │  │  └─ IUserService.java
│  │  └─ out/                 # Outbound Port (Repository 인터페이스)
│  │     └─ UserRepositoryPort.java
│  └─ service/
│     └─ UserServiceImpl.java # Port만 의존, JPA 모름
└─ adapter/
   ├─ web/                    # Inbound Adapter
   │  ├─ UserController.java
   │  └─ dto/
   └─ persistence/            # Outbound Adapter
      ├─ UserRepository.java  # JpaRepository 구현
      ├─ UserJpaEntity.java   # @Entity
      ├─ UserMapper.java      # Domain ↔ Entity 변환
      └─ UserJpaAdapter.java  # UserRepositoryPort 구현체
```

**핵심**: Service는 Port(인터페이스)에만 의존 → JPA 없이 단위 테스트 가능

### 2.2. 계층별 역할

| 계층 | 역할 | 구현 |
|------|------|------|
| **Presentation** | HTTP 처리, DTO 검증 | `@RestController` |
| **Application** | 유스케이스, 트랜잭션 관리 | `@Service`, `@Transactional` |
| **Domain** | 순수 비즈니스 로직 | POJO |
| **Infrastructure** | DB, 외부 API | `@Repository`, Clients |

---

## 3. 코드 작성 원칙

### 3.1. 엔티티 생성

**정적 팩토리 메서드 사용** (생성자 private)

```java
public class User {
    private User(...) {}  // private 생성자

    public static User createManager(...) {
        // 검증 로직
        return new User(...);
    }
}
```

**이유**: 생성 시점 비즈니스 규칙 캡슐화, 의도 명확화

### 3.2. DTO 생성

**from() 정적 팩토리 메서드**

```java
public record UserResponse(...) {
    public static UserResponse from(User user) {
        return new UserResponse(...);  // 단순한 경우
        // 또는 builder() 사용 (필드 많을 때)
    }
}
```

### 3.3. 엔티티 업데이트

**필드별 개별 메서드** (포괄적 update 메서드 금지)

```java
public void updateNickname(String nickname) {
    // 검증 로직
    this.nickname = nickname;
}

public void updatePhone(String phone) {
    this.phone = phone;
}
```

**이유**: 변경 의도 명확, PATCH 대응 용이, null 처리 단순

### 3.4. Mapper vs Entity 메서드

- **Entity**: 상태 변경의 주체 (비즈니스 규칙 포함)
- **Mapper**: 계층 간 데이터 변환만 (검증 로직 금지)

---

## 4. 핵심 개발 원칙

### 4.1. DRY (Don't Repeat Yourself)

```java
// ❌ 중복
userRepository.findById(id).orElseThrow(...)
userRepository.findById(id).orElseThrow(...)

// ✅ 헬퍼 메서드
private User findUserById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
}
```

### 4.2. 동시성 제어

- **DB Unique 제약**: `(manager_id, customer_id)` 조합에 UNIQUE
- **비관적 락**: `findByIdForUpdate()` 사용
- **예외 변환**: `DataIntegrityViolationException` → `RelationAlreadyExistsException`

### 4.3. 관심사의 분리

- **Service**: "무엇을" (비즈니스 로직)
- **Controller/Security**: "누가" (인가 로직)

### 4.4. JPA 엔티티 관리

```java
// ✅ 권장 (Soft Delete 대응)
User user = findUserById(id);
userRepository.delete(user);

// ❌ 비권장
userRepository.deleteById(id);
```

---

## 5. 기술 스택

| 영역 | 기술 |
|------|------|
| **프레임워크** | Spring Boot 3.x, Spring Data JPA |
| **DB** | PostgreSQL, QueryDSL (동적 쿼리) |
| **보안** | Spring Security, JWT, BCrypt |
| **암호화** | Jasypt (필드 레벨) |
| **이벤트** | Spring Events + `@Async` |
| **테스트** | JUnit 5, `@DataJpaTest`, `@WebMvcTest` |
| **문서화** | Swagger (Springdoc) |

---

## 6. 패턴 도입 기준

### UseCase 도입 시점
- 서비스가 여러 흐름을 담아 비대해질 때
- 권한/검증/트랜잭션을 유스케이스별로 분리하고 싶을 때

### Mapper 도입 시점
- 도메인 ↔ JPA 엔티티 분리 필요 시
- DTO 변환 코드가 중복될 때

### QueryDSL 도입 시점
- 동적 쿼리, 통계성 조회 필요 시
- Fetch Join으로 해결 안 되는 복잡한 쿼리

---

## 7. 현재 개선 필요 사항

### 7.1. JPA
- [ ] `spring.jpa.properties.hibernate.default_batch_fetch_size=100` 설정
- [ ] Fetch Join / `@EntityGraph`로 N+1 해결
- [ ] QueryDSL 의존성 추가 및 `JPAQueryFactory` 빈 등록
- [ ] 비즈니스 예외 클래스 생성 (IllegalArgumentException 대체)

### 7.2. Security
- [ ] JWT 인증 필터 구현
- [ ] `SecurityFilterChain` 재구성 (STATELESS 세션)
- [ ] `@PreAuthorize`로 메서드 레벨 보안
- [ ] Refresh Token Rotation (RTR) 전략

### 7.3. 운영
- [ ] 프로필 분리 (`application-local/dev/prod.yml`)
- [ ] Actuator 도입 (`/health`, `/metrics`)
- [ ] `logback-spring.xml` 구성
- [ ] `@RestControllerAdvice` 예외 핸들러 고도화
- [ ] 테스트 커버리지 확보

---

## 8. 커밋 원칙

- **Gitmoji 사용**: ✨ feat, 🐛 fix, ♻️ refactor, 📝 docs
- **명확한 메시지**: "무엇을" + "왜" (한글 OK)
- **논리적 단위**: 기능별 작은 커밋

---

## 9. 참고 문서

- **전체 아키텍처**: `/docs/gemini.md`
- **아키텍처 리뷰**: `/docs/carelog-architecture-review.md`
- **헥사고날 상세**: `/docs/DomainArchitecture.md`
- **패턴 가이드**: `/docs/ARCHITECTURE.md`
- **핵심 원칙**: `/docs/SoftwareEngineeringPrinciples.md`
- **ERD**: `/docs/ERD_v1.md`

---

**중요**: 상세 내용이 필요하면 위 문서들을 직접 참조하세요. 이 파일은 핵심 원칙만 요약합니다.
