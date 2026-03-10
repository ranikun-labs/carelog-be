# 현재 작업 컨텍스트

> 최종 업데이트: 2026-03-09 (엔티티 상속 구조 및 멀티테넌시 설계 확정)

---

## 📁 문서 목록

| 파일 | 내용 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 전체 시스템 아키텍처 및 기술 스택 정의 |
| [ERD_v1.md](ERD_v1.md) | 데이터베이스 엔티티 설계 및 관계도 |
| [DomainArchitecture.md](DomainArchitecture.md) | 도메인 모델링 및 비즈니스 로직 설계 원칙 |
| [SoftwareEngineeringPrinciples.md](SoftwareEngineeringPrinciples.md) | 개발 원칙 및 코드 스타일 가이드 |
| [carelog-architecture-review.md](carelog-architecture-review.md) | 현재 설계의 문제점 분석 및 개선 방향 보고서 |

---

## 🗓 지난 작업 완료

- [x] Spring Boot 3.4.5 기반 프로젝트 기초 환경 설정 (Java)
- [x] 공통 인프라 구축 (GlobalExceptionHandler, ApiResponse, Logging AOP)
- [x] JPA Auditing 및 BaseEntity 구현 (기본형)
- [x] 사용자(User) 도메인 기본 CRUD 및 단위 테스트 완료
- [x] 관계(Relation) 도메인 기본 생성 로직 및 단위 테스트 완료
- [x] Swagger UI 연동을 통한 API 문서화 기초 작업

## 📋 설계 결정사항

### 엔티티 상속 구조
```
BaseEntity (Audit 필드만)
├── SystemAuditLog
└── TenantBaseEntity (organizationId 추가)
    ├── User
    ├── Relation
    ├── Journal
    └── TenantAuditLog
```

- **BaseEntity** — `createdAt`, `updatedAt`, `createdBy`, `updatedBy` 감사 필드만
- **TenantBaseEntity** — `BaseEntity` 상속 + `organizationId`. Hibernate Filter 이 레벨에 선언
- **SystemAuditLog** — `BaseEntity` 상속, 인증 실패/시스템 오류 등 전역 로그

### 도메인별 추가 필드

| 엔티티 | 추가 선언 | 이유 |
|--------|----------|------|
| User | `deletedAt` | soft delete 필요 |
| Relation | `deletedAt`, `@Version` | soft delete + 동시 수정 충돌 |
| Journal | `status` (ACTIVE/SUPERSEDED) | 삭제 불가, SUPERSEDED 패턴 |
| TenantAuditLog | - | append-only |
| SystemAuditLog | - | append-only |

### 역할 구분
- `User.role` = MANAGER / STAFF / ADMIN
- `Relation.type` = CLIENT / TENANT / PATIENT

### 멀티테넌시 전략
- **organization_id 기반 Shared Schema (논리 격리)** 채택
- Hibernate Filter로 `WHERE organization_id = ?` 자동 적용
- JWT에 `userId` + `role` + `organizationId` 클레임 포함

### 삭제 전략

| 패턴 | 적용 대상 |
|------|----------|
| SoftDelete (`deletedAt`) | User, Relation |
| SUPERSEDED 패턴 | Journal |
| append-only (삭제 없음) | TenantAuditLog, SystemAuditLog |

### Journal 삭제 방어 3계층
1. **DB 레벨** — 앱 계정 DELETE 권한 없음
2. **Repository 레벨** — `delete()` 오버라이드 → 예외 throw
3. **도메인 레벨** — SUPERSEDED 패턴으로 수정 시 새 INSERT 처리

### DB 계정 권한

| 계정 | 권한 |
|------|------|
| 앱 실행 계정 | SELECT, INSERT, UPDATE |
| Flyway 계정 | DDL 포함 전체 |

## 📋 다음 작업

### Phase 1: Kotlin 마이그레이션

| 순서 | 항목 | 상태 | 비고 |
|------|------|------|------|
| 1-1 | `build.gradle.kts` Kotlin DSL 전환 | 완료 | Lombok 제거, MockK/Kotest 추가 |
| 1-2 | BaseEntity Kotlin 전환 | 완료 | Audit 필드 중심 (deletedAt/tenantId/version 제외) |
| 1-3 | Common 계층 Kotlin 전환 | 진행중 | Exception, DTO, Config 계층 변환 |
| 1-3a | Common 계층 보완 요소 추가 (Kotlin) | 대기 | `ClockConfig`, `ErrorCode` interface, `GlobalResponseCustomizer`, `OpenApiConfig` 개선 |
| 1-4 | 핵심 도메인(User, Relation) Kotlin 전환 | 대기 | Java to Kotlin 변환 및 Idiomatic 리팩토링 |
| 1-5 | 테스트 코드 Kotlin 전환 | 대기 | MockK/Kotest 적용 |

### Phase 2: 인증 & 보안

| 순서 | 항목 | 상태 | 비고 |
|------|------|------|------|
| 2-1 | Spring Security & JWT 기초 설정 | 대기 | 코틀린 DSL 활용한 FilterChain 구성 |
| 2-2 | JWT 내 `tenantId` 클레임 추가 및 발급 | 대기 | 멀티테넌시 식별자 탑재 |
| 2-3 | 테넌트 격리 보안 필터 구현 | 대기 | `SecurityContext` 내 테넌트 정보 보관 |

### Phase 3: 신규 도메인 개발

| 순서 | 항목 | 상태 | 비고 |
|------|------|------|------|
| 3-1 | 예약(Reservation) 도메인 신규 개발 | 대기 | 누락된 핵심 도메인 설계 및 구현 |
| 3-2 | 일지(Journal) 시스템 및 JSONB 구현 | 대기 | 업종별 동적 템플릿, delete 차단 적용 |

### Phase 4: 인프라 (배포/스테이징 단계)

| 순서 | 항목 | 상태 | 비고 |
|------|------|------|------|
| 4-1 | DB 계정 분리 | 대기 | 앱용 계정 생성 (SELECT/INSERT/UPDATE만 부여) |
| 4-2 | Flyway 마이그레이션 도입 | 대기 | 운영 데이터 보존 필요 시 적용 |

## 📌 커밋/PR 규칙

| 타입 | 용도 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (Kotlin 마이그레이션 포함) |
| `chore` | 잡무/설정 (gradle 설정 등) |
| `docs` | 문서 관련 |
| `test` | 테스트 코드 |
| `hotfix` | 긴급 수정 |
| `release` | 배포 준비 |
