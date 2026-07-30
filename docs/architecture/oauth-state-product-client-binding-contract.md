# OAuth State Product Client Binding 구현 계약

> 문서 분류: **Service-specific Auth/OAuth Contract**
>
> Implementation status: **not_started**
>
> Primary Docs Jira: [RPL-28](https://ranikunlabs.atlassian.net/browse/RPL-28)
>
> Related Research: [RPL-24](https://ranikunlabs.atlassian.net/browse/RPL-24)
>
> Depends On: [RPL-16](https://ranikunlabs.atlassian.net/browse/RPL-16),
> [RPL-25](https://ranikunlabs.atlassian.net/browse/RPL-25)

## 1. 목적과 상태

이 문서는 RPL-24 Research에서 첫 구현에 필요한 결정만 추출해 `carelog-be`의
Durable Contract로 고정한다. Raw Research 전문이나 세션 대화를 보존하는 문서가
아니다.

현재 Runtime Fact와 Target Architecture를 구분한다.

- 현재 Auth/OAuth Runtime은 `carelog-be` 내부 모듈이다.
- Product Completion Redirect Runtime은 아직 없다.
- 현재 Provider `redirect_uri`는 항상 Backend Callback이 아니다. MOBILE에서는
  configured custom scheme일 수 있다.
- Backend Callback에서 Product Completion Redirect로 이어지는 topology는 목표 후보일
  뿐 현재 Runtime이 아니다.
- 이 문서는 Production 코드, Runtime 설정, Redis, DB, Migration, Gateway, Frontend를
  변경하거나 배포하는 문서가 아니다.

Shared Identity의 현재·목표 경계는
[Runtime Deployment Profiles](runtime-deployment-profiles.md), 외부 소비자 관점의
현재 Auth 계약은
[Finance Frontend Shared Identity Contract](finance-frontend-shared-identity-contract.md)를
함께 따른다. Draft Foundation 계약과 현재 Runtime을 구분하는 원칙은
[Foundation Context](../foundation/foundation-context.md)를 따른다.

## 2. 현재 실제 OAuth Flow

아래는 문서 작성 시점의 `carelog-be` 코드 사실이다.

```text
Authorization 요청 { clientChannel, returnTo }
  → WEB/MOBILE Compatibility Product Client 해석
  → Product Client Registry에서 enabled 검증
  → Provider + Client별 configured redirect_uri 선택
  → State / PKCE / Nonce 생성
  → Redis에 OAuth State 저장
  → Provider가 configured redirect_uri로 code/state 전달
  → Product Client가 code/state를 Backend /exchange로 POST
  → Redis GETDEL
  → 저장된 provider와 요청 provider 일치 확인
  → 저장된 redirect_uri / PKCE verifier로 Provider token exchange
  → Provider principal 검증과 Carelog 로그인
```

현재 `OAuthStateRecord`는 `provider`, `redirectUri`, `returnTo`, `codeVerifier`,
`nonce`, `issuedAt`만 저장한다. State version, 명시적 만료 시각, Product Client
snapshot은 없다. 저장 TTL의 기본값과 tracked 설정은 5분이며,
`RedisOAuthStateStore`는 `oauth:state:*` JSON 값을 `GETDEL`로 단회 소비한다.

현재 `returnTo`는 안전한 상대 경로뿐 아니라 설정된 exact origin의 absolute URI도
허용한다. 첫 구현 목표는 이를 path-only 정책으로 좁히는 것이다.

Provider가 configured `redirect_uri`로 이동한 뒤 Product Client가 `/exchange`를
호출하는 것이 현재 계약이다. Provider가 언제나 Backend Callback을 호출한 뒤 Backend가
HTTP 302로 Product에 보내는 구조로 해석하면 안 된다.

## 3. Redirect·Navigation·Origin 개념 분리

| 개념 | Owner | Trust source | Current Runtime | Target | 구현 시기 |
| --- | --- | --- | --- | --- | --- |
| Provider Redirect/Callback URI | Auth/OAuth | 서버의 provider + Product Client 설정 | `OAuthRedirectUriResolver`가 configured URI를 선택한다. WEB URL 또는 MOBILE custom scheme일 수 있으며 State에 저장해 token exchange에 재사용한다. | Product Client별 exact 등록값을 사용하고 State의 callback identity와 결합한다. | PR 1에서 State binding, topology 변경은 Architecture Gate 이후 |
| Product Completion Redirect URI | Product + Auth 계약 | 향후 Product Client Registry의 서버 관리 설정 | 없음. Backend Callback → Completion Redirect Runtime도 없음 | 실제 consumer가 생길 때 exact allowlist와 전달 topology를 함께 확정한다. | PR 2 |
| `returnTo` | Product navigation | 공격자 입력을 서버 정책으로 검증한 값 | 상대 경로 또는 configured allowed origin의 absolute URI를 허용하고 State에 저장한다. 성공 token 응답의 redirect 계약은 아니다. | path-only 검증 후 원문을 State에 저장한다. | PR 1 |
| CORS Origin | Gateway/HTTP ingress | Browser `Origin`은 비신뢰 입력, 허용 정책은 서버/Gateway 설정 | OAuth redirect나 `returnTo`의 신뢰 근거가 아니다. 현재 Gateway의 OAuth publication/CORS 계약을 이 문서가 확정하지 않는다. | 배포 topology에 맞는 method/path/origin 정책을 Gateway 계약으로 별도 확정한다. | 별도 Gateway/CORS 작업 |

네 값은 서로 대체할 수 없다. 특히 Provider callback allowlist, Product completion
allowlist, `returnTo` path 정책, CORS origin 목록을 하나의 설정이나 검증기로 합치지
않는다.

## 4. 공격자 제어 입력과 신뢰 경계

| 입력·데이터 | 경계 | 처리 원칙 |
| --- | --- | --- |
| `clientChannel` | Public Authorization DTO | WEB/MOBILE compatibility 식별자일 뿐 신뢰할 Client Context가 아니다. Registry 결과와 결합한다. |
| 향후 `clientId` | Public Authorization DTO 후보 | 문자열 자체를 신뢰하지 않고 Registry의 enabled record로 해석한다. 첫 구현 Public DTO에는 추가하지 않는다. |
| `returnTo` | Public Authorization DTO | path-only 정책으로 State 생성 전에 검증한다. 검증된 원문만 State에 저장한다. |
| `code` | Public Exchange DTO | Provider가 발급한 일회성 입력으로 취급하며 로그에 남기지 않는다. |
| `state` | Public Exchange DTO | 비밀값은 아니지만 인증 상관관계 값이다. 형식 검증 후 Redis에서 단회 소비하며 로그에 남기지 않는다. |
| Provider 응답 | 외부 Provider boundary | Provider adapter의 token/principal 검증을 통과한 결과만 사용한다. |
| `Origin`과 인입 Header | Gateway/HTTP boundary | 공격자가 위조할 수 있다. Product Client, redirect, `returnTo` 신뢰의 근거로 사용하지 않는다. |
| Completion Redirect 후보 | 향후 Backend→Product boundary | 현재 입력·Runtime이 아니다. Architecture Gate와 exact server-side registration 전 사용하지 않는다. |

Exchange는 HTTP 요청에 Product Client Context를 다시 받거나 신뢰하지 않는다. State에서
복원한 snapshot과 현재 Registry record만 신뢰한다.

## 5. 첫 구현에서 승인된 계약

첫 구현은 compatibility Product Client와 path-only `returnTo`를 기존 OAuth 흐름에
결합한다.

### 5.1 State payload

`OAuthStateRecord`는 최소한 다음 의미를 보존한다.

- State schema `version`
- `productClientId`
- `product`
- `channel`
- provider identity와 token exchange에 사용할 provider callback URI
- 검증된 `returnTo`
- 기존 PKCE verifier와 nonce
- `issuedAt`
- `expiresAt`

`productClientId`, `product`, `channel`은 Authorization 시작 시
`ProductClientReader.requireEnabled(...)`로 검증된 `RegisteredProductClient`의
snapshot이다. Public DTO 문자열을 그대로 snapshot으로 만들지 않는다.

### 5.2 Exchange 검증

Exchange는 다음 순서를 지킨다.

1. Public `state` 형식을 검증한다.
2. Redis `GETDEL`로 State를 단회 소비한다.
3. State 존재 여부, version, `issuedAt`/`expiresAt`, provider identity를 검증한다.
4. State의 `productClientId`로 Registry를 재조회하고 enabled 상태를 검증한다.
5. Registry의 `clientId`, `product`, `channel`을 State snapshot과 exact 비교한다.
6. 모든 검증이 성공한 뒤에만 Provider token exchange를 호출한다.
7. 기존 principal 검증과 로그인 흐름을 계속한다.

unknown, disabled, version 오류, 만료, provider/clientId/product/channel mismatch는
fail-closed다. 외부에는 unknown·disabled·mismatch의 세부 Registry 상태를 구분해
노출하지 않는다.

기존 Redis `GETDEL`, TTL, replay protection, PKCE, nonce를 유지한다. 기존 WEB/MOBILE
Authorization DTO와 `code`/`state`만 받는 Exchange DTO도 유지한다.

## 6. `returnTo` path-only 정책

첫 구현의 `returnTo`는 다음 조건을 모두 만족해야 한다.

| 규칙 | 결정 |
| --- | --- |
| 시작 문자 | `/` 필수 |
| network-path reference | `//` 시작 거부 |
| URI 구성요소 | scheme, authority, host 거부 |
| separator | backslash 거부 |
| percent encoding | 초기 구현에서는 `%`를 전면 거부 |
| path traversal | `.` 또는 `..`인 path segment 거부 |
| query | 허용 |
| fragment | 거부 |
| blank | 거부 |
| whitespace | raw `returnTo`의 어느 위치에 있든 모든 whitespace character를 거부한다. Path와 Query 내부의 ASCII space, horizontal tab, CR, LF 및 기타 whitespace를 모두 State 생성 전에 거부한다. |
| 최대 길이 | 2,048자 |
| 문자 집합 | Non-ASCII character는 거부한다. ASCII 범위라도 whitespace와 control character는 모두 거부한다. |
| 저장 | 정규화·decode·재조립하지 않고 검증된 원문을 그대로 State에 저장 |

URI parsing 전에 raw 입력에서 모든 whitespace, control character, backslash,
percent character, Non-ASCII character를 거부한다. 이 순서는 Java API나 특정 정규식
구현을 Canonical 계약으로 고정하지 않으며, 위 보안 불변조건만 고정한다.

Query 자체는 허용하지만 raw Query 내부의 whitespace는 허용하지 않는다. query 내부의
값은 navigation 데이터일 뿐 권한·Client 식별 근거가 아니다. `%20`은 whitespace
decode 정책이 아니라 기존 `%` 전면 거부 규칙으로 차단한다. 검증기에서 decode 후
재인코딩하거나 경로를 canonicalize하면 검증 문자열과 사용 문자열이 달라질 수 있으므로
금지한다.

## 7. 오류와 상태 전이

아래 표는 첫 구현의 외부 계약과 소비 시점을 정의한다.

| 상황 | State 소비 시점 | 외부 결과 |
| --- | --- | --- |
| 잘못된 `returnTo` | State 생성·Redis 저장 전 | 400 |
| malformed public `state` | Redis 접근 전 | 401 |
| missing / expired / replay State | 조회 또는 `GETDEL` 결과 없음 | 401 |
| provider mismatch | `GETDEL` 후 | 401 |
| State version/time 오류 | `GETDEL` 후 | 401 |
| Client unknown / disabled / mismatch | `GETDEL` 후, Provider 호출 전 | 401 |
| Redis 장애 | 저장 또는 소비 실패 | 503 |
| Registry DB 장애 | State 소비 후 재조회 실패 가능 | 기존 5xx 경계를 유지 |

`GETDEL` 이후 검증 실패한 State는 복구하거나 Redis에 되돌리지 않는다. 실패한 시도도
재사용을 차단하는 것이 fail-closed와 replay protection에 맞다. Registry DB 장애로
State가 이미 소비될 수 있는 비용은 허용하며, 사용자는 Authorization부터 다시
시작한다.

## 8. 사용자 직접 Core

다음은 구현자가 직접 소유할 최소 핵심 코드다.

1. `OAuthBoundProductClient`의 clientId/product/channel 불변조건
2. State version, 발급·만료 시각, Product Client snapshot 검증
3. `ReturnToValidator`의 path-only 정책과 검증된 원문 보존
4. `GETDEL` 이후 provider → version/time → Registry → exact snapshot 검증 순서
5. mismatch, disabled, replay의 비가역 상태 전이
6. `OAuthAuthorizationService`와 `OAuthLoginService`의 application orchestration
7. Provider 호출 전 차단을 증명하는 핵심 Red Test

Security 결정을 fixture 대량 수정과 섞지 않는다. 먼저 공격 시나리오 Red Test로 실패
경계를 고정하고 최소 Core를 구현한다.

## 9. AI 반복 작업

Core 결정 이후 다음 반복 작업은 AI에 위임할 수 있다.

- `OAuthStateRecord` 생성 fixture 일괄 갱신
- `returnTo` parameterized edge case 확장
- Redis JSON round-trip, TTL, `GETDEL`, 동시 소비 회귀
- Controller DTO와 HTTP status 회귀
- PostgreSQL 기반 로그인 통합 테스트
- Auth/OAuth 전체 Suite
- Architecture Test
- PR/Jira evidence 정리

AI 반복 작업도 Production 계약을 임의로 확장하거나 Architecture Gate를 통과한 것으로
간주하지 않는다.

## 10. 구현 PR 분할

### PR 1 — OAuth State Product Client Binding + path-only `returnTo`

- compatibility Product Client snapshot을 State에 저장
- version/time/provider/client snapshot 검증
- enabled Registry 재조회와 exact 비교
- path-only `returnTo`
- 기존 WEB/MOBILE Public DTO, PKCE, nonce, TTL, `GETDEL` 보존

### Architecture Gate

다음 topology와 credential 전달 계약을 먼저 결정한다.

- Provider가 Backend Callback을 호출하는 공통 topology
- Backend Callback 이후 Product Completion Redirect
- Web/Mobile별 Token 또는 Session handoff

### PR 2 — Product Completion Redirect

- 실제 consumer와 callback topology가 확정된 뒤에만 구현
- Product Completion Redirect config와 exact validation을 함께 구현
- 등록만 하고 사용하지 않는 선행 allowlist는 만들지 않음

### PR 3 — External explicit `clientId`

- Public DTO에 explicit `clientId` 추가
- 실제 iOS/Android Product Client 등록
- compatibility WEB/MOBILE 계약의 migration과 회귀 포함

## 11. 첫 구현 Jira Scope

첫 구현 Jira 제목은 **“OAuth State에 Product Client Context와 path-only returnTo를
바인딩”**으로 고정한다.

포함 범위:

- State version/time과 Product Client snapshot
- Authorization에서 검증된 compatibility Client 저장
- Exchange에서 enabled Client 재조회와 exact snapshot 검증
- provider/clientId/product/channel mismatch fail-closed
- path-only `returnTo`
- 기존 Redis/PKCE/nonce/WEB/MOBILE/Public DTO 회귀

제외 범위:

- Completion Redirect Runtime
- external explicit `clientId`
- Backend HTTP 302
- Token/Session 전달
- issuer/audience/JWKS
- Gateway/CORS
- iOS/Android 등록
- Auth Service 물리 추출

## 12. 변경 Class 계획

아래는 구현 계획이며 이 문서 PR에서는 어떤 Class도 변경하거나 생성하지 않는다.

| Class | 현재 역할 | 첫 구현 계획 |
| --- | --- | --- |
| `OAuthStateRecord` | provider, redirect URI, `returnTo`, PKCE verifier, nonce, `issuedAt` 저장 | version, `expiresAt`, bound Product Client snapshot을 추가하고 완전한 State 불변식을 표현 |
| `OAuthBoundProductClient` | 현재 없음 | clientId/product/channel snapshot을 하나의 불변 값으로 표현 |
| `OAuthStateBindingVerifier` | 현재 없음 | version/time/provider와 Registry exact match를 Provider 호출 전에 fail-closed 검증 |
| `OAuthAuthorizationService` | compatibility Client 검증, callback 선택, State 생성·저장 | 검증된 `RegisteredProductClient`를 bound snapshot으로 State에 저장하고 `expiresAt` 계산 |
| `OAuthLoginService` | provider resolve, State `GETDEL`, provider 비교 후 token exchange | `ProductClientReader` 또는 verifier를 통해 enabled 재조회·exact 비교 후 token exchange |
| `ReturnToValidator` | 상대 경로 또는 allowlisted absolute origin 허용 | 6장의 path-only 정책으로 축소하고 원문 보존 |
| `RedisOAuthStateStore` | JSON 저장, TTL, `GETDEL` 소비 | 새 payload JSON round-trip을 보존하되 단회 소비 의미는 변경하지 않음 |
| 관련 Unit/Integration Test | 기존 State/Authorization/Login/Redis/Controller 회귀 | 공격·정합성 Matrix를 먼저 추가하고 fixture와 통합 회귀 확장 |

`ProductClientReader`의 적절한 소비 지점은 Exchange orchestration이다. verifier가
Registry port를 소유하더라도 호출 순서의 최종 책임은 `OAuthLoginService`에 있다.

## 13. Test Matrix

| 영역 | 필수 검증 |
| --- | --- |
| WEB/MOBILE snapshot | compatibility 기본 clientId, `CARELOG`, WEB/MOBILE channel이 각각 정확히 저장됨 |
| State version/time | supported version 성공, unknown/missing version 거부, 미래 `issuedAt` 거부, boundary expiry와 expired 거부 |
| Redis round-trip | 모든 필드 JSON round-trip, TTL 유지, 누락 필수 필드 fail-closed |
| Replay/concurrency | 동일 State 두 번째 소비 거부, 동시 consume에서 정확히 하나만 성공 |
| Provider binding | 요청 provider와 stored provider mismatch 시 `GETDEL` 후 401, Provider 미호출 |
| Client binding | clientId/product/channel 각각의 mismatch를 독립 거부 |
| Registry status | unknown/disabled Client를 동일 외부 오류로 거부하고 Provider 미호출 |
| Provider call ordering | 모든 State/Client 검증이 끝난 뒤에만 token exchange 호출 |
| 정상 `returnTo` | `/`, 다중 segment, whitespace가 없는 query, 최대 2,048자의 허용 ASCII 원문 보존 |
| 거부 `returnTo` | blank, 비-`/` 시작, `//`, scheme/authority/host, backslash, `%`(따라서 `%20` 포함), `.`/`..` segment, fragment, `/patient list`, `/?q=a b`, path 내부 horizontal tab, query 내부 horizontal tab, CR 또는 LF 포함 입력, Unicode whitespace, 기타 control/non-ASCII, 2,048자 초과 |
| 구버전 State | version/client snapshot이 없는 JSON을 fail-closed 401로 거부하고 Provider 미호출 |
| Redis 장애 | save/consume 장애가 503이며 인증 실패로 위장되지 않음 |
| Registry DB 장애 | 기존 5xx 경계 유지, 세부 Registry 상태 외부 비노출 |
| Public DTO 회귀 | Authorization은 `clientChannel`/`returnTo`, Exchange는 `code`/`state` 유지 |
| 로그인 통합 | PostgreSQL linked/unlinked/inactive identity 흐름과 기존 token 발급 회귀 |
| 로그 보안 | secret, authorization code, state, PKCE verifier, token이 application/access log에 노출되지 않음 |
| Architecture | Auth web/app/domain boundary와 Redis adapter 의존 방향 유지 |

### 구버전 Redis State 배포 호환성

기존 State의 최대 잔존 시간은 기본 TTL 5분이다. 구버전 payload에는 version과 Product
Client snapshot이 없으므로 신뢰할 Context를 복원할 수 없다. 첫 구현은 이를
fail-closed 401로 거부하고 재로그인을 요구한다.

보안 불변식을 약화하는 dual-read 호환 로직은 두지 않는다. 무중단 사용자 경험이
필요하면 배포 전후 최대 5분의 drain/재시도 안내를 운영 절차로 선택하되, 구버전 State를
새 계약으로 승격하지 않는다.

## 14. Architecture Escalation

다음은 Foundation/Architecture 세션의 별도 판단 전 구현하지 않는다.

- Backend Callback topology
- Product Completion Redirect 공통 계약
- Web/Mobile Token·Session 전달 방식
- issuer/audience/JWKS
- Gateway 인증 Context
- Shared Identity 물리 추출
- Repository·Schema·Migration 소유권

이 문서의 Accepted PR 1 Scope는 위 결정을 선점하지 않는다. Product Completion
Redirect, explicit clientId, Gateway/CORS, Session handoff를 “함께 하면 편한 인접
작업”이라는 이유로 PR 1에 추가하지 않는다.

## 15. Merge 이후 상태 전이

이 문서 PR의 독립 검수와 Merge 전에는 docs Jira를 완료 처리하지 않는다. Merge 이후
다음 evidence를 별도 단계로 기록한다.

1. RPL-24에 Canonical 문서와 Merge Commit 연결
2. Research 상태와 실제 Jira 현황 확인
3. 첫 구현 Jira를 확정하되 `할 일`로 유지
4. 구현 Branch는 오리지널 Auth 구현 세션에서 생성

이 문서의 Merge 자체가 Production 구현 시작, Architecture Gate 승인, Runtime 배포를
의미하지 않는다.
