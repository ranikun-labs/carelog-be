# 현재 작업 컨텍스트

> 최종 업데이트: 2026-03-10

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

### Step 2: `feat/security-jwt` (carelog-be)

> ⚠️ Hibernate 테넌트 필터(`organizationFilter`)는 현재 선언만 된 상태. JWT 구현 후 Security Filter에서 `session.enableFilter("organizationFilter").setParameter("organizationId", ...)` 활성화해야 실제 테넌트 격리가 동작함.

| 항목 | 상태 |
|------|------|
| Spring Security FilterChain 설정 | 대기 |
| JWT 발급 (Access Token + Refresh Token) | 대기 |
| JWT 필터 구현 | 대기 |
| `organizationId`, `publicId` 클레임 포함 | 대기 |
| Login 엔드포인트 | 대기 |

### Step 3: `carelog-gateway` 신규 프로젝트 생성

| 항목 | 상태 |
|------|------|
| Spring Cloud Gateway 프로젝트 생성 | 대기 |
| 라우팅 설정 (`/api/**` → carelog-be, `/rag/**` → FastAPI) | 대기 |
| JWT 검증 Global Filter | 대기 |
| Redis 연동 (Refresh Token 저장, Token Blacklist) | 대기 |
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