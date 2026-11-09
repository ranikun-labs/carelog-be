# Carelog 프로젝트

## 기술 스택
- Backend: Spring Boot 3.4 (Java 17)
- Gateway: Spring Cloud Gateway (Kotlin)
- DB: PostgreSQL + Redis
- Auth: JWT + SCG 헤더 인증

## 도메인 기능

### 1. 회원(의료진) 관리

| 기능 | 설명 |
| --- | --- |
| 회원 가입 | 의료진 계정 등록 (이름, 이메일, 비밀번호) → BCrypt 암호화 저장 |
| 로그인 | 이메일/비밀번호 기반 로그인 (JWT 발급) |
| 로그아웃 | JWT 만료 처리 (Redis blacklist) |
| 회원 정보 수정 | 이름, 비밀번호 등 수정 가능 |
| 역할 관리 | 관리자 / 일반 의료진 권한 구분 |

### 2. 고객(환자) 관리

| 기능 | 설명 |
| --- | --- |
| 환자 등록 | 이름, 생년월일, 연락처, 주소 등 기본 정보 입력 |
| 환자 정보 수정 | 변경 사항 수정 |
| 환자 삭제 | 논리 삭제 (deletedAt) |
| 환자 조회 | 이름, 연락처, 생년월일 등 검색 필터 |

#### 고객 데이터 암호화

- 전화번호, 생년월일, 주소 → AES 양방향 암호화 (`@Convert` / `AttributeConverter`)
- 이름 → 검색 필요 시 평문 저장 가능

### 3. 고객-담당자 관계 관리

| 기능 | 설명 |
| --- | --- |
| 관계 설정 | 치료사와 환자 1:1 연결 (`relations` 테이블) |
| 관계 상태 | 활성 / 종결 등 상태 관리 |

### 4. 치료 일지(진료 기록) 관리

| 기능 | 설명 |
| --- | --- |
| 일지 등록 | 환자 + 작성자(의료진) + JSONB 자유 필드 |
| 일지 수정/삭제 | 작성자 본인만, 논리 삭제 |
| 일지 목록 | 키워드, 기간, 환자 이름 필터링 |
| 댓글 | 의료진 의견 작성, 본인만 수정/삭제, soft-delete |

## MVP 범위

### 1차 MVP

- JWT 인증 + RBAC (치료사별 환자 접근 제한)
- 환자 개인정보 AES 암호화
- 상담·치료 일지 (JSONB 템플릿 기반, `relation_entries` / `entry_templates` 테이블)
- 진행 단계 Lifecycle 관리 (관심 → 상담 중 → 치료 중 → 종결)
  - Spring Events로 이력 자동 저장

## 코드 규칙
- 패키지 구조: `도메인/web`, `도메인/app`, `도메인/domain`
- 논리 삭제: `deletedAt` 필드
- 멀티테넌트: `TenantContext` + `TenantAspect`
- 코드 주석은 한글로

## Foundation 읽기 순서
- Backend Architecture, Identity, 서비스 경계, DB 소유권, 서비스 통신, 분산 정합성, Token/Event Contract 변경 전 `docs/foundation/foundation-context.md`를 먼저 읽는다.
- Foundation의 Draft 미래 계약과 현재 Carelog Runtime을 구분하고, 실제 지원 여부는 코드·Migration·테스트로 확인한다.
- CUSTOMER를 Identity Principal로 해석하거나 Account 대상으로 Backfill하지 않는다.


## PR 작성 및 본문 갱신
- PR 생성 또는 본문 수정 전에 대상 base 브랜치의 `.github/PULL_REQUEST_TEMPLATE.md` 실제 내용을 확인한다.
- 저장소에 작업 유형별 전용 PR 템플릿이 있으면 현재 작업과 맞는 형식을 우선한다.
- `/핵심정리` 형식은 한국어로 `목적`, `변경`, `검증`, `Finding`, `Source Merge`, `Production Gate`, `참조`를 구분해 작성한다.
- 실제 실행한 검증과 근거만 기록하고, 실행하지 않은 항목은 `PASS`가 아니라 `NOT_RUN`으로 표시한다.
- 작성자(Writer)의 검증 결과와 독립 Reviewer의 판정을 구분한다. 독립 `PASS`는 검수한 코드 HEAD가 명시되고 근거가 일치할 때만 기록한다.
- 코드 HEAD, base 브랜치, 테스트 명령·결과·링크가 실제 ref와 실행 근거에 맞는지 확인한다.
- Source Merge 승인과 Production 배포 승인을 별도 Gate로 기록한다. PR 본문 작성만으로 Merge 승인을 간주하지 않는다.
- Secret, PII, 민감한 SQL 파라미터를 본문에 기록하지 않는다. 필요한 설명에는 비식별·마스킹한 예시만 사용한다.
