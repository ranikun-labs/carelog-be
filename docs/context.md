# 현재 작업 컨텍스트

> 최종 업데이트: 2026-03-19

---


## 📁 문서 목록

| 파일 | 내용 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 전체 시스템 아키텍처 및 기술 스택 정의 |
| [ERD_v1.md](ERD_v1.md) | 데이터베이스 엔티티 설계 및 관계도 |
| [DomainArchitecture.md](DomainArchitecture.md) | 도메인 모델링 및 비즈니스 로직 설계 원칙 |
| [SoftwareEngineeringPrinciples.md](SoftwareEngineeringPrinciples.md) | 개발 원칙 및 코드 스타일 가이드 |
| [monorepo-strategy.md](monorepo-strategy.md) | Gradle Multi-Module 모노레포 전환 결정 배경 |
| [scg-auth-design.md](scg-auth-design.md) | Spring Cloud Gateway 인증 설계 (JWT 검증, Blacklist, 라우팅, carelog-be 전환 전략) |

---

## 🗓 지난 작업 완료

- [x] Spring Boot 3.4.5 기반 프로젝트 기초 환경 설정
- [x] 공통 인프라 구축 (GlobalExceptionHandler, ApiResponse, Logging AOP)
- [x] JPA Auditing 및 BaseEntity 구현
- [x] 사용자(User) 도메인 기본 CRUD 및 단위 테스트 완료
- [x] 관계(Relation) 도메인 기본 생성 로직 및 단위 테스트 완료
- [x] TenantBaseEntity 추가 (organization_id + Hibernate Filter)
- [x] BaseEntity에서 deletedAt 제거 → User, Relation 개별 선언으로 이동
- [x] Relation에 @Version 추가 (낙관적 락)
- [x] 로컬 개발용 Docker PostgreSQL 환경 구성
- [x] JWT 인증 체계 구축 (Access/Refresh Token, 로그인·로그아웃·재발급)
- [x] ThreadLocal + AOP 기반 멀티테넌트 자동 격리 (TenantContext, TenantFilter, TenantAspect)
- [x] Relation JWT 연동 (managerId JWT 추출, organizationId 세팅)
- [x] Relation publicId 도입 — 요청/응답 전체 내부 DB pk 제거, UUID 기반으로 전환
- [x] CustomUserDetails isEnabled() deletedAt 기반 계정 상태 연결

---

## 📋 설계 결정사항

### 엔티티 상속 구조
```
BaseEntity (Audit 필드만: createdAt, updatedAt, createdBy, updatedBy)
├── SystemAuditLog
└── TenantBaseEntity (organizationId 추가)
    ├── User            (deletedAt 개별 선언)
    ├── Relation        (deletedAt 개별 선언)
    ├── JournalTemplate
    ├── RelationJournal (updatedAt/updatedBy는 항상 createdAt/createdBy와 동일 — append-only)
    └── TenantAuditLog
```

### User 역할 전략
- `UserRole` = MANAGER / CUSTOMER (단일 테이블)
- `managerType` = PHYSICAL_THERAPIST 등 — MANAGER일 때만 필수, CUSTOMER는 NULL
- 가입 엔드포인트 분리: `POST /users/managers`, `POST /users/customers`
- 서비스 레이어에서 role에 따라 managerType 필수 여부 검증

#### CUSTOMER를 User 테이블에 둔 이유
- v1: 매니저가 고객을 추가하는 방식 (고객 로그인 없음, `email`/`password` nullable)
- v3 예정: 고객도 로그인하여 자신의 일지 조회, AI 답변/문서 열람 기능 추가
- 인증이 필요한 기능이므로 처음부터 User 테이블에서 관리
- 확장 플로우: 초대 링크로 고객이 직접 `email`, `password` 세팅 → 로그인 가능

#### Customer 조회 전략
- **현재 (v1)**: Customer는 JWT 로그인 없음 → `publicId` 기반 조회 불가
- **조회 방법**: `name` 기반 API (`GET /users/customers?name=...`)로 처리
- ⚠️ **TODO**: Customer name 검색 API 미구현 — Journal 이후 또는 Customer 관련 기능 개발 시 추가 필요
- **v3 전환 시**: 로그인 추가되면 서비스 레이어의 customer 소유권 체크 코드가 자동으로 활성화됨 (별도 수정 불필요)

### 식별자 전략
| 필드 | 용도 |
|------|------|
| `id` | 내부 PK (JPA 연관관계, DB 조인용) |
| `public_id` | 외부 노출용 UUID (Spring Cloud Gateway, FastAPI RAG 서버 연동 시 순차 ID 은닉) |
| `organization_id` | 멀티테넌시 격리용 (Hibernate Filter로 자동 WHERE 조건 적용, JWT 클레임에서 주입) |

### 멀티테넌시 전략
- organization_id 기반 Shared Schema (논리 격리)
- Hibernate Filter로 `WHERE organization_id = ?` 자동 적용
- JWT에 `userId` + `role` + `organizationId` 클레임 포함

#### Organization 도메인 전략
- **MVP**: Organization 테이블 없음. 매니저 가입 시 `UUID.randomUUID()`로 organizationId 생성 → User에 직접 저장
- **의도적 비정규화**: 매니저 1명 = 조직 1개이므로 MVP에서 구분 불필요
- **Customer organizationId**: 매니저 소속 고객은 동일한 organizationId 공유 (JWT 클레임에서 꺼내 주입) → Hibernate Filter 동작 조건
- **추후**: STAFF 추가/멀티테넌시 본격화 시점에 Organization 엔티티 추가 + Flyway 마이그레이션

##### Organization 테이블 확장 시 마이그레이션 전략
```sql
-- 1. organizations 테이블 생성
CREATE TABLE organizations (
    id      BIGINT PRIMARY KEY,
    public_id UUID UNIQUE NOT NULL,  -- 현재 users.organization_id 값 재사용
    name    VARCHAR NOT NULL
);

-- 2. 기존 users 데이터에서 organization 추출
INSERT INTO organizations (public_id, name)
SELECT DISTINCT organization_id, 'temp-name'
FROM users WHERE role = 'MANAGER';

-- 3. FK 추가 (데이터 재생성 없이 UUID 값 그대로 연결)
ALTER TABLE users
ADD CONSTRAINT fk_users_org
FOREIGN KEY (organization_id) REFERENCES organizations(public_id);
```
> 핵심: `users.organization_id`(UUID) = `organizations.public_id` → 기존 데이터 파괴 없이 FK 연결 가능

##### MVP에서 Organization 테이블을 두지 않는 이유
- Organization 테이블이 있었다면: Organization 생성 → ID 반환 → User FK 연결 순서로 트랜잭션 범위 확대
- cascade 설계, 조인 쿼리, 별도 Repository 필요
- 매니저 1명 = 조직 1개인 지금은 그 복잡도가 이득보다 큼 → UUID 값 복사로 대체

### 삭제 전략
| 패턴 | 적용 대상 |
|------|----------|
| SoftDelete (`deletedAt`) | User, Relation |
| SUPERSEDED 패턴 | Journal (물리 삭제 불가) |
| append-only (삭제 없음) | TenantAuditLog, SystemAuditLog |

### Journal 이력 관리 전략 (SUPERSEDED + previousId 꼬리물기)

#### 설계 배경
의료법상 진료 기록 보관 의무(5~10년)로 인해 물리 삭제 불가. 삭제 차단 3계층 적용.

| 계층 | 방법 |
|------|------|
| DB 레벨 | 앱 계정 `REVOKE DELETE ON relation_journals` |
| Repository 레벨 | `delete()` / `deleteById()` override → `CustomException` throw |
| API 레벨 | 삭제 엔드포인트 미제공 (의도적) |

#### 수정 시 동작 방식
```
수정 전:  [A, ACTIVE, previous_id=null]

수정 후:
  [A, SUPERSEDED, previous_id=null]   ← 기존 레코드 상태만 변경
  [B, ACTIVE,     previous_id=A.id]   ← 새 레코드 INSERT
```
- `previous_id`: 자기 참조 FK (이전 버전의 `id`를 가리킴)
- 새 레코드는 새 `public_id` 발급 (URL이 바뀌므로 클라이언트는 항상 최신 id로 재조회)
- 단, 꼬리 추적은 `previous_id` FK를 따라가면 전체 이력 복원 가능

#### 이력 조회 — PostgreSQL Recursive CTE
```sql
-- 최신 레코드(id=12)로부터 전체 이력을 한 쿼리로 조회
WITH RECURSIVE journal_history AS (
    SELECT * FROM relation_journals WHERE id = 12         -- 시작점
    UNION ALL
    SELECT j.*
    FROM relation_journals j
    INNER JOIN journal_history jh ON j.id = jh.previous_id -- 꼬리 역추적
)
SELECT * FROM journal_history ORDER BY created_at DESC;
```
JPA Repository에서 `@Query(nativeQuery = true)`로 동일하게 사용.

#### 인덱스 전략
```sql
-- previous_id 역추적 성능 (이력 조회 필수)
CREATE INDEX idx_journal_previous_id ON relation_journals(previous_id);

-- JSONB 전문 검색 (MVP 이후, 병목 측정 후 적용)
CREATE INDEX idx_journal_content_gin ON relation_journals USING GIN (content);
```
> Flyway 미사용(ddl-auto: update) — GIN 인덱스는 Hibernate 자동 생성 불가. DB 직접 실행 필요.

#### JournalTemplate 설계
- `fields` 컬럼: JSONB 배열. 양식 필드 스펙 정의.
- 템플릿 없이도 자유 양식 작성 가능 (`template_id` nullable)
- 상태: `ACTIVE` / `INACTIVE` (비활성화만, 삭제 없음)

#### RelationJournal 컬럼 분리 설계 (PII 분리 + AI 파이프라인 보안 경계)

**확정 구조**: `content` 단일 JSONB → 4필드 분리

| 컬럼 | 타입 | 분리 근거 |
|------|------|----------|
| `title` | varchar | 정렬/필터 대상 + AI 파이프라인 구조적 제외 (UI 메타데이터) |
| `visit_date` | date | 정렬/필터 대상 (방문일 기준 조회) |
| `case_data` | JSONB | 업종별 동적 업무 데이터 — AI 파이프라인 전달 대상 |
| `private_data` | JSONB | PII (이름, 연락처 등) — 내부 전용, AI 전달 제외 |

**컬럼 분리 기준 (실무 표준)**
- 정렬 / 필터 / 인덱스 대상 → 고정 컬럼
- 구조가 자주 바뀌거나 업종별로 다른 데이터 → JSONB
- 보안 경계가 다른 데이터 → 별도 JSONB

**보안 경계 설계 포인트**
- `clinical_data`만 익명화하여 벡터 DB / AI API 전달
- `personal_data`는 내부 전용 격리 — AI 파이프라인 진입 불가
- 백엔드 저장 시점에 템플릿 `category` 정의와 대조하여 재분류 검증 레이어 필수 (프론트 신뢰 불가)

**면접 서사**
> "단일 JSONB에서 4필드 분리는 AI 보안 경계를 코드 레벨이 아닌 컬럼 레벨로 강제한 설계 판단."

#### 벡터 동기화 전략 (단계별)

| 단계 | 방식 | 핵심 |
|------|------|------|
| MVP | `@Async` + `@Retryable` | 트랜잭션 커밋 후 비동기 동기화, 3회 재시도(Exponential Backoff). 서버 재시작 시 이벤트 유실 허용 |
| V2 | PostgreSQL Outbox + `@Scheduled` 폴러 | 저널 수정 트랜잭션 안에 outbox INSERT 묶음. 상태 머신(PENDING→PROCESSING→COMPLETED/FAILED→DEAD). 이벤트 유실 없음 |
| V3 (운영) | CDC(Debezium/WAL) + Reconciliation + 알림 | 폴링 지연 제거. RDB↔벡터 DB 정합성 배치 검증. Dead Letter 발생 시 Slack 웹훅 |

**면접 서사**
> "벡터 동기화는 MVP에서 @Async + @Retryable로 빠르게 검증하고, V2에서 PostgreSQL Outbox로 이벤트 영속성 확보, 운영 단계에서 CDC + Reconciliation으로 전환. 각 단계에서 규모에 맞는 복잡도를 선택."

### 동시성 전략
| 방식 | 적용 대상 |
|------|----------|
| 낙관적 락 (`@Version`) | Relation |

---

## 📋 작업 이력 및 다음 작업 순서

### Step 1: `feat/tenant-base` ✅ 완료

| 항목 | 상태 |
|------|------|
| User — `managerType`, `publicId` 필드 추가 | 완료 |
| 가입 엔드포인트 분리 (`/users/managers`, `/users/customers`) | 완료 |
| ManagerCreateRequest, CustomerCreateRequest DTO 분리 | 완료 |
| Relation — `@Version` 제거 | 완료 |
| User — DB CHECK constraint 추가 | 완료 |

> ⚠️ 비관적 락은 예약(Reservation) 도메인에서 필요 — 같은 시간대 중복 예약 선점 방지. Reservation 개발 시 적용.

> ⚠️ `POST /users/customers`는 개발용으로만 유지.
> 실제 운용에서는 `POST /relations` 호출 시 Customer 생성 + Relation 연결을 하나의 트랜잭션으로 처리해야 함.

### SCG + JWT 인증/인가 전체 아키텍처

#### 전체 흐름
```
클라이언트
    ↓ HTTPS
Cloudflare Tunnel
    ↓
SCG (외부 유일 노출)
    ├── JWT 서명 검증 (stateless)
    ├── Redis Blacklist 체크
    └── X-User-Id, X-Organization-Id, X-Role, X-Public-Id 헤더 추가
    ↓ 내부망
carelog-be (외부 직접 접근 불가)
    ├── 헤더에서 유저 정보 추출
    ├── Hibernate Filter 활성화 (organizationId)
    └── 비즈니스 로직
```

#### 저장소 역할
| 저장소 | 데이터 | 이유 |
|--------|--------|------|
| 클라이언트 | Access Token + Refresh Token | - |
| Redis | Blacklist된 Access Token | TTL = 남은 유효기간, 만료 시 자동 삭제 |
| RDB | Refresh Token | 상태 관리 필요 (재사용 감지, 강제 만료) |

#### Access Token 클레임
```
subject: userId
claims: {
    organizationId: "uuid",
    role: "MANAGER",
    publicId: "uuid"   ← FastAPI RAG 서버 연동 시 필요
}
TTL: 30분
```

#### 로그인 / 갱신 / 로그아웃 흐름
| 작업 | 처리 |
|------|------|
| 로그인 | ID/PW 검증 → Access + Refresh 발급 → Refresh → RDB 저장 |
| 갱신 | RDB Refresh 검증 → 새 Access 발급 → Refresh Rotation (기존 삭제 + 신규 저장) |
| 로그아웃 | Access Token → Redis Blacklist 추가 (TTL = 남은 유효기간) + RDB Refresh 삭제 |

#### Step 2 → Step 3 전환 시 주의
- Step 2: `JwtAuthenticationFilter`가 carelog-be에서 JWT 직접 검증
- Step 3: SCG가 검증 인수 → carelog-be의 `JwtAuthenticationFilter` **제거**
- carelog-be `SecurityConfig`도 헤더 기반 유저 정보 추출 방식으로 변경 필요
- Cloudflare Tunnel → SCG만 외부 노출 구조로 carelog-be 네트워크 격리 자연스럽게 해결

---

### Step 2: `feat/security-jwt` ✅ 완료

| 항목 | 상태 |
|------|------|
| Spring Security FilterChain 설정 | 완료 |
| JWT 발급 (Access Token + Refresh Token) | 완료 |
| JWT 필터 구현 | 완료 |
| `organizationId`, `publicId` 클레임 포함 | 완료 |
| Login 엔드포인트 | 완료 |
| ThreadLocal 기반 TenantContext + TenantFilter | 완료 |
| AOP 기반 TenantAspect (Hibernate Filter 활성화) | 완료 |

> ✅ 버그 수정 완료
> - `SecurityConfig` — `new TenantFilter()` 수정
> - `User.java` — `name == null` 체크 추가, 예외 타입 `INVALID_MANAGER_FIELDS`로 수정
> - `CustomUserDetails.isEnabled()` — `deletedAt` 기반 계정 상태 연결

#### 트러블슈팅 기록

**[1] Hibernate Filter 테넌트 격리 미동작**
- 원인: `TenantFilter`에서 `EntityManager.unwrap(Session.class)`로 직접 필터 활성화 시도 → Filter 레이어의 Session-A ≠ `@Transactional` 레이어의 Session-B (JPA Session은 트랜잭션 단위로 생성)
- 해결: Filter에서는 `TenantContext`(ThreadLocal)에 `organizationId`만 저장, `TenantAspect`가 `@Before`로 트랜잭션 진입 시점에 현재 Session에 필터 활성화
- 핵심: Servlet Filter와 Spring `@Transactional`은 생명주기가 달라 같은 Session을 공유하지 않음. 요청 전반에 걸친 값 공유는 ThreadLocal, 실제 DB 격리 활성화는 AOP `@Before`에서 처리

**[2] JwtAuthenticationFilter 기동 시 NPE**
- 원인: `@Component` + `OncePerRequestFilter` 조합 → CGLIB이 `final` 메서드(`doFilter`) 프록시 생성 실패 → `@Slf4j`의 `log` 필드 null → `log.isDebugEnabled()` NPE. 이후 `@Bean` 등록 시도 시 순환 참조 발생
- 해결: `@Component` 제거 + `SecurityConfig`에서 `new JwtAuthenticationFilter(...)` 직접 생성
- 핵심: `OncePerRequestFilter` 구현체는 `@Component` 금지. Security Filter는 `SecurityConfig`에서 직접 생성해 등록하는 것이 실무 표준

**[3] Recursive CTE + Hibernate 6 @Filter 충돌 (`column "id" does not exist`)**
- 원인: `RelationJournal`이 `TenantBaseEntity` 상속 → `@Filter(organization_id = :organizationId)` 적용됨. Hibernate 6은 native query 실행 시 `@Filter` 조건을 쿼리에 주입하는데, Recursive CTE 구조에 주입하면서 내부 컬럼 참조가 깨짐 → `column "id" does not exist` 에러
- 해결: CTE 대신 Java 체인 추적으로 우회. `previousId` FK를 따라 루프로 이력 수집
- 핵심: Hibernate 6의 `@Filter`는 native query에도 적용됨. CTE처럼 복잡한 SQL 구조에서는 필터 주입이 쿼리를 망가뜨릴 수 있음
- 향후: Hibernate `Session.disableFilter()` 또는 별도 네이티브 쿼리 실행 방식으로 CTE 전환 예정

### Step 2.5: `feat/relation` JWT 연동 및 publicId 전환 ✅ 완료

| 항목 | 상태 |
|------|------|
| Relation 엔티티 publicId 추가 | 완료 |
| 요청/응답 전체 publicId 기반 전환 | 완료 |
| managerId JWT SecurityContext에서 추출 | 완료 |
| organizationId 세팅 (테넌트 격리) | 완료 |
| customerPublicId로 고객 조회 | 완료 |
| Relation API 접근 제어 (IDOR 방지, 소유권 체크, @PreAuthorize 역할 체크) | 완료 |

> ⚠️ User 엔드포인트도 publicId 기반으로 전환 필요 — Journal 이후 처리
> ✅ Customer name 검색 API 구현 완료 — `GET /users/customers?name=...`

### Step 3: `refactor/scg-monorepo` 🔄 진행 중

| 항목 | 상태 |
|------|------|
| Gradle Multi-Module 모노레포 전환 (settings.gradle, 루트 build.gradle) | ✅ 완료 |
| carelog-be → `carelog-be/` 서브디렉토리 이동 (git mv) | ✅ 완료 |
| `carelog-gateway` 모듈 신규 생성 | ✅ 완료 |
| SCG JwtGlobalFilter (JWT 검증 + Blacklist 체크 + X-Gateway-Secret 주입) | ✅ 완료 |
| SCG RedisBlacklistService (ReactiveStringRedisTemplate, isBlacklisted) | ✅ 완료 |
| X-Gateway-Secret 헤더 방어 (JwtGlobalFilter strip + 주입, application.yml) | ✅ 완료 |
| docker-compose Redis 추가 | ✅ 완료 |
| UserPrincipal 인터페이스 신규 생성 | ✅ 완료 |
| CustomUserDetails — implements UserPrincipal 추가 | ✅ 완료 |
| GatewayUserDetails 신규 생성 (헤더 기반) | ✅ 완료 |
| TenantFilter — instanceof UserPrincipal 로 변경 | ✅ 완료 |
| carelog-be: GatewayHeaderAuthFilter 추가 (X-Gateway-Secret 검증 + SecurityContext 설정) | 🔄 진행 중 (SecurityContext 설정 미완성) |
| carelog-be: JwtAuthenticationFilter 제거 | 대기 |
| carelog-be: SecurityConfig 헤더 기반으로 전환 | 대기 |
| carelog-be: RedisBlacklistService 신규 생성 (addToBlacklist) | 대기 |
| carelog-be: JwtTokenProvider — getRemainingValidity() 추가 | 대기 |
| carelog-be: AuthService/AuthServiceImpl.logout() — accessToken 파라미터 + Redis 등록 | 대기 |
| carelog-be: AuthController.logout() — Authorization 헤더 추출 | 대기 |
| GitHub Actions path filter 워크플로우 (be/gateway 배포 독립) | 대기 |

#### Step 3 구현 결정 사항

**로그아웃 처리: Option A (carelog-be 전담)**
```
클라이언트 → SCG(JWT 검증) → carelog-be(/auth/logout)
                                  ↓
                     DB: RefreshToken 삭제
                     Redis: AccessToken Blacklist 등록 (TTL = 남은 유효기간)
```
- SCG는 라우팅/검증만 담당, 비즈니스 로직(DB 접근)은 carelog-be에 유지
- shared Redis: carelog-be(`StringRedisTemplate`) + carelog-gateway(`ReactiveStringRedisTemplate`) 모두 `blacklist:` prefix 키 공유

**CustomUserDetails 하위 호환 전략**

기존 컨트롤러/서비스 전체가 `@AuthenticationPrincipal CustomUserDetails` 사용 중.
`GatewayHeaderAuthFilter`가 헤더에서 읽어 `CustomUserDetails` 객체를 직접 생성하는 방식으로 하위 호환 유지.

```java
// 헤더 기반 생성자 추가 (DB 조회 없이 헤더 값으로만 구성)
public CustomUserDetails(String userId, UUID organizationId, String role, UUID publicId) { ... }
```

- 기존 컨트롤러/서비스 코드 변경 없음
- `TenantFilter`도 변경 없음 (`authentication.getPrincipal() instanceof CustomUserDetails` 그대로 동작)
- `AuditorAwareImpl`도 변경 없음 (`userDetails.getUsername()` → userId 반환)

**GatewayHeaderAuthFilter (carelog-be 신규)**
- `OncePerRequestFilter` 구현, `@Component` 금지 (SecurityConfig에서 직접 생성)
- `X-User-Id`, `X-Organization-Id`, `X-Role`, `X-Public-Id` 헤더 읽어서 SecurityContext 설정
- 헤더 없으면 그냥 통과 (공개 경로 처리 위임은 SecurityConfig permitAll에서)

**LoggingAspect `@Around` 포인트컷 방어 처리**
- 기존 CGLIB 버그 (트러블슈팅 [2] 참고): `@Component` + `OncePerRequestFilter` → CGLIB `final` 메서드 프록시 실패
- 현재 `@AfterThrowing`엔 `!within(*..*Filter)` 적용됨, `@Around` 3개엔 미적용 상태
- `GatewayHeaderAuthFilter` 추가 시 `LoggingAspect` `@Around` 포인트컷 3개에도 `&& !within(*..*Filter)` 추가 필요
- `@Component` 안 써도, 나중에 실수로 달 경우를 대비한 방어적 처리

**모듈 구성**
- Spring Cloud 버전: `2024.0.1` (Spring Boot 3.4.x 호환)
- carelog-gateway 패키지: `carelog.gateway`
- 공개 경로 (SCG JwtGlobalFilter 통과 목록): `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/users/managers`

**⚠️ 헤더 스푸핑 방어 (보안 필수)**
- 클라이언트가 `X-User-Id` 등 헤더를 직접 조작해 보낼 경우 SCG 우회 권한 탈취 가능
- `JwtGlobalFilter`에서 JWT 검증/주입 전에 인입 헤더 먼저 strip 필요
- 공개 경로도 strip 적용 대상 (검증 건너뛰어도 strip은 필수)
- 상세 구현: `docs/scg-auth-design.md` 참고

**⚠️ Redis 네트워크 인프라 전제 조건**
- Option A 구조에서 carelog-be(온프레미스) → EC2 Redis 쓰기 접근 필요
- Cloudflare Tunnel / VPN 양방향 통신 확인 선행 필요 (코드 작업 전 인프라 확정)

### Step 4: FastAPI 연동 🔜 대기

| 항목 | 상태 |
|------|------|
| `/rag/**` 라우팅 + JWT 전달 | 대기 |
| FastAPI JWT 검증 연동 | 대기 |

### Step 5: `feat/journal` (carelog-be) 🔜 진행 중

| 항목 | 상태 |
|------|------|
| ExceptionStatus — JOURNAL_* 3개 추가 | 대기 |
| JournalTemplate 엔티티 + Repository | 대기 |
| RelationJournal 엔티티 (JSONB, previousId 꼬리물기) | 대기 |
| JournalStatus, JournalTemplateStatus enum | 대기 |
| Repository delete 차단 (2계층) | 대기 |
| JournalService / JournalTemplateService | 대기 |
| JournalController (삭제 엔드포인트 없음) | 대기 |
| JournalTemplateController | 대기 |

> 설계 결정: previousId 자기참조 FK 방식. 이력 조회는 PostgreSQL Recursive CTE. 상세 내용은 위 "Journal 이력 관리 전략" 참고.
> 조회 권한: MVP에서는 매니저만. Customer JWT 로그인 없으므로 제외.

### 면접 준비 (2026-03-17 화요일)

> 코드 설명 면접. 본인이 작성한 코드 구조와 세부 로직 파악 여부 평가.

**설명 흐름**
1. 전체 패키지 구조 — user / relation / journal / auth / common 레이어 분리
2. TenantBaseEntity + Hibernate Filter — 멀티테넌시 자동 격리 원리
3. JWT 인증 흐름 — Filter → SecurityContext → TenantContext ThreadLocal
4. Relation 도메인 — Factory method, publicId vs id 식별자 전략
5. Journal SUPERSEDED 패턴 — 의료법 배경, 삭제 차단 3계층, previousId 이력 추적

**예상 질문**
- Hibernate Filter가 뭔지, 언제 활성화되는지
- ThreadLocal을 왜 썼는지, 메모리 누수 방지를 어떻게 하는지
- Servlet Filter와 @Transactional의 Session 생명주기가 왜 다른지
- Access Token / Refresh Token 역할 분리 이유, Refresh Token Rotation
- OncePerRequestFilter에 @Component 왜 안 붙이는지
- publicId / id 둘 다 두는 이유
- soft delete vs SUPERSEDED 차이, 언제 어떤 걸 쓰는지
- Factory method (`Relation.create()`)를 왜 생성자 대신 쓰는지
- previousId 꼬리물기 vs group_id 트레이드오프
- Recursive CTE가 뭔지, 언제 쓰는지

**계층 책임 원칙**
- `SecurityContextHolder` 직접 참조는 Controller 책임 — Service가 Spring Security 인프라에 직접 의존하면 계층 오염 + 테스트 어려움
- 올바른 방식: Controller에서 `@AuthenticationPrincipal`로 꺼내서 Service에 파라미터로 전달
- 현재 `RelationServiceImpl`, `JournalServiceImpl` 모두 Service에서 직접 참조 중 → 리팩토링 필요 (MVP 이후)
- 면접 질문: "왜 Service에서 SecurityContextHolder 쓰나요?" → "인지하고 있고, Controller로 옮기는 게 맞습니다. 범위상 MVP 이후 일괄 리팩토링 예정입니다"

**핵심 어필 포인트**
- 멀티테넌시 + Hibernate Filter 동작 원리 (트러블슈팅 기록 숙지)
- SUPERSEDED 패턴 — 의료법 보관 의무 배경
- TenantBaseEntity 상속 선택 이유 — @FilterDef 중복 vs 유지보수 트레이드오프

**스프링 예상 질문**

거의 확실히 나올 것:
- Filter랑 Interceptor 차이
- AOP가 뭔지, 언제 쓰는지
- `@Transactional` 동작 원리 (프록시)
- `@Transactional(readOnly = true)` 왜 쓰는지

이 프로젝트 코드 보다가 나올 것:
- `OncePerRequestFilter` 왜 쓰는지, 일반 Filter랑 차이
- ThreadLocal 왜 썼는지, 언제 clear 해야 하는지
- Servlet Filter에서 Session 못 쓰는 이유 (트러블슈팅 기록)
- `@PreAuthorize` 동작 원리 — AOP 기반이라는 거 알고 있는지

꼬리 질문으로 나올 것:
- Hibernate Filter랑 `@Where` 차이
- `FetchType.LAZY` 왜 쓰는지, N+1이 뭔지
- `@MappedSuperclass` vs `@Inheritance` 차이

---

### 인프라 구조 (배포)
```
[EC2]     SCG (carelog-gateway) + Redis
[온프렘]  Spring Boot (carelog-be) + FastAPI + PostgreSQL
```
> 의료법 대응: 환자 데이터는 온프렘, 라우팅/토큰만 EC2

---

## 📌 커밋/PR 규칙

| 타입 | 용도 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `chore` | 잡무/설정 |
| `docs` | 문서 관련 |
| `test` | 테스트 코드 |