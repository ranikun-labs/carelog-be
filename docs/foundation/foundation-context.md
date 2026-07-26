# Backend Service Foundation 참조 컨텍스트

## 목적

Carelog Backend가 참조하는 상위 MSA Backend 공통 기준을 안내한다. 원문과 계약의 소유권은 Foundation Repository에 있으며, Carelog에는 원문을 복제하지 않는다. 이 문서는 현재 Runtime 사실과 Foundation의 미래 Draft 계약을 구분하기 위한 참조 adapter다.

## Canonical Source

- Repository: `https://github.com/aixion1506/harness-foundation-docs.git`
- Branch: `main`
- Commit: `a0a73f8ab582ca129d02dabd1edbdda519ed369c`
- Decision: `DEC-059`
- Technical document status: Draft

참조 기준점의 Repository-relative 경로:

- `docs/architecture/backend-service-foundation/service-boundaries.md`
- `docs/architecture/backend-service-foundation/database-ownership-and-reference-policy.md`
- `docs/architecture/backend-service-foundation/service-communication-policy.md`
- `docs/architecture/backend-service-foundation/distributed-consistency-policy.md`
- `docs/architecture/backend-service-foundation/documentation-ownership-and-placement.md`
- `docs/contracts/backend-service-foundation/identity-token-contract.md`
- `docs/contracts/backend-service-foundation/event-envelope-contract.md`

이 commit은 참조 기준점일 뿐이며 Draft는 구현 완료를 의미하지 않는다. Foundation 변경은 자동 동기화하지 않으며, 새 기준 적용은 명시적인 검토와 Carelog 문서 갱신을 거친다.

## 명칭 경계

- **Shared Platform**: `DEC-005`의 oh-my-ai Domain-neutral Contract / Shared Core 경계
- **Backend Service Foundation**: MSA Backend 공통 Architecture / Contract
- **Shared Identity**: Backend Service Foundation의 canonical 논리 서비스명
- **identity-platform**: 물리 분리 시 후보 Repository명이며, 현재 생성되거나 배포된 서비스가 아님

Shared Identity는 현재 Carelog에 별도로 배포된 물리 Runtime 서비스를 뜻하지 않는다.

## Carelog 적용 상태

### 현재 구현됨

- Carelog 내부 Identity 논리 경계와 MANAGER/CUSTOMER 분리
- `PlatformAccount`, `PasswordCredential`, `ExternalIdentity` 기반 및 Credential Port
- 비밀번호 인증 Source of Truth 전환
- Flyway V1~V4

### 아직 구현되지 않음

- Shared Identity 물리 분리
- 실제 OAuth Provider와 Account Linking
- 이메일 자동 계정 병합
- 독립 JWKS 계약과 Event Envelope Runtime 적용
- Product Membership 확장

## Carelog Identity 경계

- MANAGER는 로그인 Principal이다.
- CUSTOMER는 Manager가 관리하는 외부 CRM 고객이며 Identity User 또는 Platform Account 대상이 아니다.
- 기존 CUSTOMER 전체 Account Backfill은 금지한다.
- 이메일은 Snapshot일 수 있지만 자동 계정 병합 기준이 아니다.
- OAuth 성공과 Carelog 가입 완료는 동일하지 않으며, Shared Identity 물리 분리는 아직 시작하지 않았다.

## 읽기 순서

1. `AGENTS.md` 또는 `CLAUDE.md`
2. `docs/foundation/foundation-context.md`
3. pin된 Backend Service Foundation 원본 문서
4. Carelog 공개 Architecture 문서
5. 실제 코드, Migration, 테스트

## 충돌 해결 원칙

- Foundation 문서는 상위 공통 계약과 목표를, Carelog 문서는 제품별 적용 상태를 설명한다.
- 현재 Runtime 사실은 코드, Migration, 테스트로 확인한다.
- Draft는 구현 완료의 근거가 아니며 Foundation 미래 계약을 현재 Runtime처럼 표현하지 않는다.
- 공통 Foundation과 현재 코드가 다르면 자동 수정하지 않고 별도 설계 결정을 거친다.
- Carelog 제품 특화 정책은 Foundation 원문에 중복 소유하지 않는다.
