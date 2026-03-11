# 현재 작업 컨텍스트

> 최종 업데이트: 2026-03-11

---

## 📁 문서 목록

| 파일 | 내용 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 전체 시스템 아키텍처 및 기술 스택 정의 |
| [ERD_v1.md](ERD_v1.md) | 데이터베이스 엔티티 설계 및 관계도 |
| [DomainArchitecture.md](DomainArchitecture.md) | 도메인 모델링 및 비즈니스 로직 설계 원칙 |
| [SoftwareEngineeringPrinciples.md](SoftwareEngineeringPrinciples.md) | 개발 원칙 및 코드 스타일 가이드 |

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

---

## 📋 설계 결정사항

### 엔티티 상속 구조
```
BaseEntity (Audit 필드만: createdAt, updatedAt, createdBy, updatedBy)
├── SystemAuditLog
└── TenantBaseEntity (organizationId 추가)
    ├── User       (deletedAt 개별 선언)
    ├── Relation   (deletedAt, @Version 개별 선언)
    ├── Journal
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
| SUPERSEDED 패턴 | Journal |
| append-only (삭제 없음) | TenantAuditLog, SystemAuditLog |

### 동시성 전략
| 방식 | 적용 대상 |
|------|----------|
| 낙관적 락 (`@Version`) | Relation |

---

## 📋 다음 작업 순서

### Step 1: `feat/tenant-base` 마무리

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

### Step 2: `feat/security-jwt` (carelog-be)

| 항목 | 상태 |
|------|------|
| Spring Security FilterChain 설정 | 완료 |
| JWT 발급 (Access Token + Refresh Token) | 완료 |
| JWT 필터 구현 | 완료 |
| `organizationId`, `publicId` 클레임 포함 | 완료 |
| Login 엔드포인트 | 완료 |
| ThreadLocal 기반 TenantContext + TenantFilter | 완료 |
| AOP 기반 TenantAspect (Hibernate Filter 활성화) | 완료 |

> ⚠️ **미수정 버그 (머지 전 처리 필요)**
> - `SecurityConfig.java:86` — `new TenantFilter(entityManagerFactory)` 빌드 에러. `new TenantFilter()`로 수정 + `EntityManagerFactory` 필드 제거 필요
> - `User.java:73` — MANAGER 생성 시 `name == null` 체크 누락
> - `User.java:75` — 잘못된 예외 타입 (`INVALID_USER_ROLE` → `INVALID_MANAGER_FIELDS`)

#### 트러블슈팅 기록

**[1] Hibernate Filter 테넌트 격리 미동작**
- 원인: `TenantFilter`에서 `EntityManager.unwrap(Session.class)`로 직접 필터 활성화 시도 → Filter 레이어의 Session-A ≠ `@Transactional` 레이어의 Session-B (JPA Session은 트랜잭션 단위로 생성)
- 해결: Filter에서는 `TenantContext`(ThreadLocal)에 `organizationId`만 저장, `TenantAspect`가 `@Before`로 트랜잭션 진입 시점에 현재 Session에 필터 활성화
- 핵심: Servlet Filter와 Spring `@Transactional`은 생명주기가 달라 같은 Session을 공유하지 않음. 요청 전반에 걸친 값 공유는 ThreadLocal, 실제 DB 격리 활성화는 AOP `@Before`에서 처리

**[2] JwtAuthenticationFilter 기동 시 NPE**
- 원인: `@Component` + `OncePerRequestFilter` 조합 → CGLIB이 `final` 메서드(`doFilter`) 프록시 생성 실패 → `@Slf4j`의 `log` 필드 null → `log.isDebugEnabled()` NPE. 이후 `@Bean` 등록 시도 시 순환 참조 발생
- 해결: `@Component` 제거 + `SecurityConfig`에서 `new JwtAuthenticationFilter(...)` 직접 생성
- 핵심: `OncePerRequestFilter` 구현체는 `@Component` 금지. Security Filter는 `SecurityConfig`에서 직접 생성해 등록하는 것이 실무 표준

### Step 3: `carelog-gateway` 신규 프로젝트 생성

| 항목 | 상태 |
|------|------|
| Spring Cloud Gateway 프로젝트 생성 | 대기 |
| 라우팅 설정 (`/api/**` → carelog-be, `/rag/**` → FastAPI) | 대기 |
| JWT 검증 Global Filter | 대기 |
| Redis 연동 (Access Token Blacklist) | 대기 |
| CORS 설정 | 대기 |

### Step 4: FastAPI 연동

| 항목 | 상태 |
|------|------|
| `/rag/**` 라우팅 + JWT 전달 | 대기 |
| FastAPI JWT 검증 연동 | 대기 |

### Step 5: `feat/journal` (carelog-be)

| 항목 | 상태 |
|------|------|
| JournalTemplate 엔티티 | 대기 |
| RelationJournal 엔티티 (JSONB) | 대기 |
| SUPERSEDED 패턴 + delete 차단 3계층 | 대기 |
| 기본 CRUD | 대기 |

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