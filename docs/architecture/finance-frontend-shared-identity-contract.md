# Finance Harness Frontend Shared Identity 소비 계약 분석

> Status: **Analysis / Draft**
>
> 이 문서는 확정 ADR이 아니다. Shared Identity로 물리 분리하기 전에 현재
> `carelog-be` Auth/OAuth와 첫 외부 소비자 후보인 Finance Harness Frontend 사이의
> 계약 사실, 호환성, 최소 확장 지점을 고정하기 위한 Cross-Repository 분석이다.

## 1. 목적

이 분석의 질문은 새 인증 시스템을 설계하는 것이 아니라 다음을 구분하는 것이다.

- Finance Harness Frontend가 현재 Carelog Auth API를 실제로 소비할 수 있는 범위
- 현재 코드에서 제품 중립적으로 재사용할 수 있는 Identity Core
- Carelog CRM, Gateway, DB에 결합된 계약
- Auth Service 추출 전에 고정해야 하는 Frontend Port와 Web/Mobile 세션 정책
- 사람이 직접 소유해야 할 Core 결정과 Codex에 맡길 반복 구현

현재 Auth Provider는 별도 Shared Identity Runtime이 아니라 `carelog-be` 내부
Auth/OAuth 모듈이다. 이 문서에서 “Target Shared Identity”는 미래 계약을 뜻하며 현재
운영 사실을 뜻하지 않는다.

## 2. 조사 Repository와 Revision

| 역할 | Repository | Local Path | Branch/상태 | Revision |
| --- | --- | --- | --- | --- |
| 현재 Auth Provider | `care-log/carelog-be` | `/Users/work/Github/carelog-be` | `dev` | `fbe74514d3f7fa5814379c29d3968e98d73ff8c5` |
| Gateway 공개 계약 | `care-log/carelog-be` PR #34 | 원격 브랜치 읽기 전용 | `feat/kakao-oauth-gateway-publication`, OPEN/Draft | `d367a243875f33a54389d91df11ef7a4597f6691` |
| Consumer 후보 | `aixion1506/finance-harness-fe` | `/Users/work/Github/finance-harness-fe` | 원격 기본 브랜치 `master` | `8634e460bc7be45d8c4445b49780c98c2125d375` |

두 Repository는 조사 시작 시 추적 파일 변경이 없었고 `pull --ff-only` 결과
최신이었다. `.codegraph/`, `.cursor/`, `.oh-my-ai/` 비추적 디렉터리는 조사·수정·추적
대상에서 제외했다. Finance FE는 전체 조사 동안 읽기 전용이다.

Architecture 전제:

```text
Current                         Near-term
Spring Cloud Gateway            Spring Cloud Gateway
└─ carelog-be                    ├─ Carelog Auth Service
   ├─ Auth/OAuth Module          └─ Carelog Core
   └─ Carelog Core

Long-term
Spring Cloud Gateway
├─ Shared Identity
├─ Carelog Core
├─ Finance Harness Backend
└─ Dev Harness Backend
```

Gateway는 Portfolio Service가 아니라 공통 Ingress/Security Boundary다.

## 3. Finance FE 현재 구조

### 3.1 기술 기반과 명령

| 항목 | 확인 결과 |
| --- | --- |
| Node | `.node-version`, `.nvmrc`: `22.23.1`; engine `>=22.22.0 <23` |
| Package Manager | pnpm `11.15.1` |
| Vite / React / TypeScript | Vite `8.1.5`, React `19.2.8`, TypeScript `6.0.3` |
| Router | `react-router` `8.2.0`, `BrowserRouter`, basename `/` |
| Capacitor | Core/iOS/Android dependency `8.4.2`, config만 존재 |
| Unit Test | Vitest `4.1.10`, jsdom, Testing Library |
| E2E | Playwright `1.61.1` |
| Lint / Format | ESLint `10.7.0`, Prettier `3.9.6` |
| 개발 | `pnpm dev` |
| Build | `pnpm build` |
| Lint | `pnpm lint` |
| Typecheck | `pnpm typecheck` |
| Unit Test | `pnpm test` |
| E2E | `pnpm test:e2e` |
| 통합 검증 | `pnpm verify`, `pnpm verify:full` |

### 3.2 Routing과 Layout

- Router entry는 `src/main.tsx`, route tree는 `src/app/AppRouter.tsx`, path의 단일
  원본은 `src/constants/routes.ts`다.
- 공개 영역은 `/:locale/*`, 앱과 Capacitor 공통 SPA 영역은 `/app/*`다.
- 현재 앱 route는 `/app`, `/app/onboarding`, `/app/ask`, `/app/journal`,
  `/app/journal/new`, `/app/journal/:id`, `/app/journal/:id/review`다.
- `AppShell`이 `/app/*` 경계이며 `TabLayout`은 home/ask/journal 세 화면에만
  적용된다.
- 공개/앱 Not Found는 각각 구현돼 있다.
- React Error Boundary는 구현돼 있지 않다.
- 현재 protected route 구조나 route guard는 없다. `/app/*`는 인증 여부와 무관하게
  렌더된다.
- OAuth Callback route는 현재 없다. 추가 시 path 단일 원본에
  `/app/auth/callback/:provider` 수준으로 정의하고, 하단 탭이 없는 `AppShell` 직속
  route로 두는 것이 현재 경계와 맞는다. 이는 제안이며 구현 사실이 아니다.
- Browser History 방식이므로 Web hosting은 callback 직접 진입도 `index.html`로
  fallback해야 한다.

### 3.3 API와 인증 상태

현재 실제 API client, DTO, error normalization, timeout, base URL 환경변수,
credential/cookie 설정은 모두 없다. `fetch`/Axios 기반 업무 API 호출도 없고 범용 API
추상화도 없다.

| 기능 | 현재 상태 |
| --- | --- |
| 로그인 화면 | 없음 |
| Auth Context | 없음 |
| Session Store | 없음 |
| Access Token 저장 | 없음 |
| Refresh Token 저장 | 없음 |
| Cookie Session | 없음 |
| Auth용 local/session storage | 없음. localStorage는 locale에만 사용 |
| Current User | 없음 |
| Route Guard | 없음 |
| Refresh | 없음 |
| Logout | 없음 |
| OAuth Callback | 없음 |
| Auth Error Code 처리 | 없음 |

따라서 “얇은 Frontend Adapter로 가능”은 현재 코드가 이미 호환된다는 뜻이 아니다.
Adapter, session store, callback route와 guard를 새로 구현해야 한다는 뜻이다.

### 3.4 Capacitor

| 항목 | 현재 상태 |
| --- | --- |
| `ios/`, `android/` platform | 둘 다 없음 |
| 공식 `appId` | 미확정 |
| Custom URL Scheme | 없음 |
| Universal Link / App Link | 없음 |
| Capacitor Browser Plugin | dependency/구현 없음 |
| Deep Link Listener | 없음 |
| Native Secure Storage | dependency/구현 없음 |
| WebView Cookie 공유 | 정책/구현 없음 |
| Mobile Callback 계획 | STEP 13으로 유보 |

Capacitor package dependency가 있다는 사실은 native OAuth가 구현됐다는 의미가 아니다.

## 4. carelog-be Auth 현재 계약

모든 path는 backend context path `/api/v1`을 포함한 외부 path다. 성공 응답은 로그아웃을
제외하고 `{ status, message, data }` envelope다.

| Controller | Method / Path | Request DTO / Validation | Response DTO / 실제 성공 상태 | 오류 계약 | Backend 공개 여부 | Gateway route |
| --- | --- | --- | --- | --- | --- | --- |
| `AuthController` | `POST /api/v1/auth/login` | `LoginRequest(userId, password)`, 둘 다 `@NotBlank` | `LoginResponse(accessToken, refreshToken)`, 200 | `INVALID_CREDENTIALS` 401 | 공개 | 현재 일반 Carelog route, public path |
| `AuthController` | `POST /api/v1/auth/refresh` | `TokenRefreshRequest(refreshToken)`, `@NotBlank` | `TokenRefreshResponse(accessToken, refreshToken)`, 200 | `INVALID_REFRESH_TOKEN`/`REFRESH_TOKEN_EXPIRED` 401, `REFRESH_TOKEN_NOT_FOUND` 404 | 공개 | 현재 일반 Carelog route, public path |
| `AuthController` | `POST /api/v1/auth/logout` | DTO 없음, `Authorization: Bearer ...`, 인증 principal | body 없음, **204**. Swagger의 200 설명과 다름 | 인증 없음 401; backend는 malformed prefix를 204로 처리하지만 Gateway가 먼저 검증 | 보호 | 일반 Carelog route, JWT/blacklist 적용 |
| `AuthController` | `POST /api/v1/auth/oauth/kakao/authorization` | `KakaoAuthorizationRequest(clientChannel, returnTo)`, `@NotNull`, `@NotBlank` | `KakaoAuthorizationResponse(authorizationUrl)`, 200 | `UNSUPPORTED_CLIENT_CHANNEL`/`INVALID_OAUTH_RETURN_TO`/`UNSUPPORTED_OAUTH_PROVIDER` 400, `OAUTH_STATE_STORE_UNAVAILABLE` 503 | exact method/path 공개 | PR #34 전용 route |
| `AuthController` | `POST /api/v1/auth/oauth/kakao/exchange` | `KakaoExchangeRequest(code, state)`, 둘 다 `@NotBlank` | `KakaoExchangeResponse(accessToken, refreshToken)`, 200 | `INVALID_OAUTH_STATE`/`OAUTH_PROVIDER_AUTH_FAILED` 401, `OAUTH_ACCOUNT_NOT_LINKED`/`OAUTH_IDENTITY_CONFLICT` 409, state store 503 | exact method/path 공개 | PR #34 전용 route |
| `UserController` | `POST /api/v1/users/managers` | Carelog `ManagerCreateRequest` | Carelog `UserResponse`, 201 | Carelog 회원 규칙 | 공개 | 일반 Carelog route, public path |

`GET /current-user`, `/session`, `/me` 성격의 endpoint는 없다. `/users/user-id/{userId}`와
`/users/email/{email}`은 입력 식별자로 Carelog User를 조회하는 보호 API일 뿐 현재
session 조회 계약이 아니다.

현재 예외 envelope에는 stable machine-readable `code`가 없다. `ExceptionStatus` enum은
서버 내부 상태와 HTTP/message mapping을 제공하지만 응답에는 enum 이름이 노출되지 않는다.
Frontend가 한글 message 문자열에 분기하면 안 된다. Validation 전용 handler도 없어
validation failure의 안정적인 오류 계약이 고정돼 있지 않다.

## 5. Gateway 계약

PR #34는 조사 당시 OPEN/Draft이며 수정하거나 checkout하지 않았다.

- OAuth 공개 판정은 **POST + exact path**다.
  - `/api/v1/auth/oauth/kakao/authorization`
  - `/api/v1/auth/oauth/kakao/exchange`
- 두 전용 route는 `order: -20`, 일반 `/api/**` Carelog route는 `order: 0`이다.
- authorization/exchange는 서로 다른 Redis rate-limit bucket을 쓴다.
- rate limiter 장애는 OAuth 요청에 503으로 fail closed한다.
- key는 `X-Forwarded-For`와 `trusted-proxy-hops`를 반영한 client IP다. 운영 proxy
  topology와 hop 수가 불일치하면 식별 계약도 틀어진다.
- 인입 `X-User-Id`, `X-Organization-Id`, `X-Role`, `X-Public-Id`,
  `X-Gateway-Secret`은 제거한다.
- 공개 요청에도 Gateway가 `X-Gateway-Secret`을 다시 주입한다. Backend는 이 secret이
  없거나 틀리면 403이므로 직접 접근이 차단된다.
- 보호 요청은 JWT 서명을 검증하고 Redis blacklist를 조회한 뒤 verified claim을 위
  identity header로 변환한다.
- Access Token blacklist 조회는 Gateway가 소유하고, logout 등록은 backend가 소유한다.
- PR #34에는 CORS 설정이 추가되지 않았다.
- 특히 OAuth exact matcher는 POST만 공개한다. 별도 Gateway CORS 처리가 없는 현재
  코드에서 cross-origin browser의 `OPTIONS` preflight는 OAuth 공개 matcher에
  해당하지 않아 JWT 401 경로로 갈 수 있다. 따라서 cross-origin Web 호환성을
  확정할 수 없고, 현재 코드만으로는 blocked로 판정한다.

기존 `gateway.public-paths`는 `startsWith` 판정이지만 PR #34의 OAuth 두 경로는 exact
matcher를 사용한다. 두 모델을 Target Shared Identity에서 혼합하지 말고 method + exact
path 기준으로 통일해야 한다.

## 6. OAuth·PKCE 책임

### 6.1 현재 확인된 흐름

```text
Finance FE candidate
  └─ POST authorization { clientChannel, returnTo }
       └─ carelog-be
          ├─ provider/channel로 configured redirect URI 선택
          ├─ 32-byte server state 생성
          ├─ provider가 지원하면 PKCE verifier/challenge(S256) 생성
          ├─ Redis에 state record 저장(TTL 5분)
          └─ authorizationUrl 반환

Provider → configured Frontend callback?code=...&state=...
  └─ FE가 POST exchange { code, state }
       └─ Redis GETDEL로 state 단회 소비
          ├─ 저장된 redirect URI + verifier로 provider token 교환
          ├─ ExternalIdentity 조회
          └─ 연결된 Carelog account면 Carelog JWT 발급
```

| 책임 | 현재 소유자 |
| --- | --- |
| OAuth state 생성 | backend `OAuthAuthorizationService` |
| state 저장 | Redis `oauth:state:*` |
| state TTL | 5분 |
| state 단회 소비 | Redis `GETDEL` |
| PKCE verifier 생성 | backend |
| verifier 저장 | state record 내부, Redis |
| challenge | SHA-256 + base64url no-padding, `S256`만 |
| Redirect URI 선택 | backend config의 provider + `WEB`/`MOBILE` channel |
| FE authorization 입력 | `clientChannel`, `returnTo` |
| FE exchange 입력 | `code`, `state` |
| FE가 보내면 안 되는 값 | raw redirect URI, state 생성값, verifier, challenge |

알려진 핵심 계약과 실제 코드는 일치한다. 단, 현재 channel은 `WEB`/`MOBILE` 두 개뿐이고
Product Client 식별은 없다. `returnTo`는 state에 저장되지만 성공 exchange response에
반환되지 않아 소비자가 서버 검증 결과를 이동 경로로 사용할 수 없다.

Kakao adapter는 provider-neutral port 뒤에 있고 기본 `supportsPkce=true`를 사용한다.
Provider client 설정은 `carelog.auth.oauth.kakao` namespace에 결합돼 있다.

## 7. Token·Session 계약

| 항목 | 현재 Runtime 계약 |
| --- | --- |
| Access Token 위치 | login/exchange/refresh JSON body |
| Refresh Token 위치 | login/exchange/refresh JSON body |
| Cookie | 사용하지 않음 |
| Access/Refresh TTL | millisecond 환경 설정값. tracked production config에 숫자 고정값 없음 |
| OAuth state TTL | tracked config 5분 |
| Refresh session | Carelog PostgreSQL `refresh_token` table, account당 1 session 교체 |
| Refresh token 저장 | raw token 평문 |
| Refresh rotation | refresh 성공 시 DB row token/expiry 교체 |
| Logout | access token 남은 TTL만큼 Redis blacklist 등록 후 account refresh session 삭제 |
| JWT subject | `accountId` UUID |
| JWT custom claims | `organizationId`, `role`, `publicId` |
| issuer / audience | 없음 |
| product/client claim | 없음 |
| signing/discovery | symmetric shared secret, JWKS/issuer metadata 없음 |

`organizationId`, `role`, `publicId`는 `CRMIdentityProjectionPort`에서 조회된다. 토큰
발급 서비스 자체는 password/OAuth가 공유하지만 현재 claim source와 의미는 Carelog
CRM이다. Finance가 이 값을 그대로 Finance 권한으로 해석하면 안 된다.

Refresh API가 raw refresh token을 body로 요구하므로 Web은 token을 JS 접근 가능
storage에 보존해야 재시작 후 refresh할 수 있다. 이는 기술적으로 구현 가능하지만
Production Web 권장 계약은 아니다. Capacitor도 native secure storage와 bridge가
없다면 같은 문제가 생긴다.

## 8. Web·Capacitor 차이

### 8.1 Web SPA

| 전략 | 현재 backend로 가능 | Risk | 권장 |
| --- | --- | --- | --- |
| Access/Refresh 모두 JSON + browser storage | Adapter로 가능 | XSS 시 장기 refresh token 탈취 | Production 비권장 |
| 두 token을 memory만 사용 | 현재 가능 | reload 시 session 상실 | 제한된 local demo만 |
| Access memory + HttpOnly refresh cookie | 불가능 | 현재 cookie 발급/refresh/CSRF 계약 없음 | **Target Web 권장** |
| Cookie session 전체 | 불가능 | stateful session 계약 없음 | 별도 필요가 검증될 때만 |

Local Web은 같은 origin 또는 Vite proxy, provider/redirect 설정, 이미 연결된
ExternalIdentity가 준비되면 얇은 Adapter로 시연 가능하다. direct cross-origin은
Gateway preflight 때문에 현재 막힌다.

Production Web은 Product Client, redirect/origin allowlist, audience, stable error,
current session, HttpOnly refresh cookie와 CSRF/CORS 결정을 고정한 후 연결해야 한다.

### 8.2 Capacitor

| 전략 | 현재 backend로 가능 | Risk | 권장 |
| --- | --- | --- | --- |
| WebView 안에서 provider OAuth | 이론상 URL open만 가능, 구현 없음 | provider 정책, cookie/UX, callback 불명확 | 비권장 |
| System Browser OAuth | Auth/FE 모두 확장 필요 | callback 탈취/앱 전환 실패 | **권장** |
| Custom Scheme callback | `MOBILE` URI 설정은 가능하나 iOS/Android 구분 없음 | scheme hijacking | universal/app link가 불가할 때 보조 |
| Universal Link / App Link | Auth redirect registry와 native 설정 필요 | association/config 운영 필요 | **우선 권장** |
| WebView cookie 공유 | 계약 없음 | OS별 동작 차이와 session 유실 | 의존하지 않음 |
| Native Secure Storage | FE dependency/bridge 없음 | 미도입 시 refresh token 노출 | refresh token 필수 저장소 |

Mobile에서는 access token은 memory, refresh token은 native secure storage에 보관하고
system browser + claimed HTTPS callback을 우선한다. `Channel`은 `IOS`와 `ANDROID`로
분리하고 callback URI를 Product Client Registry에서 선택해야 한다.

## 9. Compatibility Matrix

판정은 현재 코드의 기술적 가능성과 Production Shared Identity 적합성을 분리한다.

| 계약 항목 | Finance FE 현재 상태 | carelog-be 현재 계약 | 판정 | 필요한 변경 |
| --- | --- | --- | --- | --- |
| Auth API Base URL | 없음 | Gateway `/api/v1` | Compatible with Frontend Adapter | FE env + typed client |
| Authorization 시작 | 없음 | Kakao 전용 POST | Compatible with Frontend Adapter | provider-neutral path는 후속 |
| Redirect URI | 없음 | server config, `WEB/MOBILE` | Requires Auth Contract Extension | Product/client/channel registry |
| Callback Route | 없음 | FE callback 전제이나 route 미지정 | Compatible with Frontend Adapter | Web route 추가, native 별도 |
| `code/state` | 처리 없음 | exchange에 두 값만 요구 | Compatible with Frontend Adapter | query parsing/즉시 제거 |
| PKCE | 없음 | server 생성/보관/S256 | Compatible | FE가 소유하지 않음 |
| Exchange | 없음 | Kakao 전용 POST | Compatible with Frontend Adapter | typed adapter |
| Access Token | 저장 없음 | JSON body | Compatible with Frontend Adapter | memory store |
| Refresh Token | 저장 없음 | JSON body | Requires Auth Contract Extension | Web cookie, Mobile secure storage |
| Session 저장 | 없음 | account당 DB session 1개 | Requires Auth Contract Extension | product/channel/device 정책 |
| Refresh | 없음 | body token, rotation | Compatible with Frontend Adapter | Production Web cookie variant |
| Logout | 없음 | Bearer + blacklist + session delete | Compatible with Frontend Adapter | cookie clear/idempotency 계약 |
| Current User | 없음 | endpoint 없음 | Requires Auth Contract Extension | `/session` 최소 projection |
| Route Guard | 없음 | backend 무관 | Compatible with Frontend Adapter | FE session bootstrap/guard |
| Error Code | 없음 | message만 노출 | Requires Auth Contract Extension | stable `code`, retryability |
| CORS | 없음 | backend broad pattern; Gateway PR에 없음 | Blocked | Gateway preflight/exact CORS |
| Cookie | 없음 | token cookie 없음 | Requires Auth Contract Extension | Secure/HttpOnly/SameSite/CSRF |
| Web SPA | SPA만 존재 | JSON token + `WEB` | Compatible with Frontend Adapter | local/same-origin 한정 |
| Capacitor iOS | platform 없음 | 공통 `MOBILE` | Blocked | appId, link, IOS channel, storage |
| Capacitor Android | platform 없음 | 공통 `MOBILE` | Blocked | appId, link, ANDROID channel, storage |
| Organization Claim | 모델 없음 | Carelog `organizationId` 필수 | Requires Auth Contract Extension | Identity와 product membership 분리 |
| Product/Client 구분 | 없음 | 없음 | Requires Auth Contract Extension | Product Client Registry |
| Token Audience | 없음 | `aud` 없음 | Requires Auth Contract Extension | resource audience 검증 |
| Redirect Allowlist | 없음 | provider/channel 단일 config | Requires Auth Contract Extension | client/channel exact allowlist |

### 환경별 최종 판정

| Surface | 판정 | 조건/차단점 |
| --- | --- | --- |
| Local Web | **얇은 Frontend Adapter로 가능** | same-origin/dev proxy, OAuth runtime config, 연결 계정 필요 |
| Production Web | **Auth 계약 확장 후 가능** | CORS/preflight, client registry, redirect/origin, audience, Web session |
| Capacitor iOS | **현재 계약으로 불가능** | native platform/callback 없음, channel/secure session 미분리 |
| Capacitor Android | **현재 계약으로 불가능** | native platform/callback 없음, channel/secure session 미분리 |

## 10. Carelog 종속성과 재사용 가능한 Identity Core

### 10.1 Carelog 종속

- Kakao provider 이름이 API path와 DTO class에 노출된다.
- 설정 namespace가 `carelog.auth.oauth.kakao`다.
- 미연결 계정 오류 message가 Carelog account를 직접 언급한다.
- JWT의 `organizationId`, `role`, `publicId`가 Carelog CRM projection에서 온다.
- signup/account endpoint와 `UserResponse`가 Carelog MANAGER domain이다.
- refresh session과 Identity schema를 Carelog DB/Migration이 소유한다.
- Gateway route target/id와 shared secret 운영이 Carelog deployment에 결합돼 있다.
- provider/channel redirect config는 client/product 단위가 아니다.

### 10.2 재사용 가능한 Identity Core

- `OAuthProviderPort`와 `OAuthProviderRegistry`
- server-owned state, Redis TTL/GETDEL 단회 소비
- server-owned PKCE verifier와 S256 challenge
- provider-neutral `ExternalIdentity(provider, providerSubject, accountId)`
- password/OAuth 공통 `AuthTokenIssuanceService`
- account 기반 refresh session rotation/revocation port
- access blacklist port와 Gateway blacklist 검증
- Gateway 인입 identity header 제거 후 검증 claim 재주입

재사용 가능하다는 것은 현재 그대로 Shared Identity production contract라는 뜻이 아니다.
client registration, token audience, session surface와 schema ownership을 먼저 분리해야 한다.

## 11. Target Shared Identity Consumer Contract

### 11.1 지금 고정할 최소 개념

| 개념 | 시점 | 이유 |
| --- | --- | --- |
| Product Client ID | **Phase 1 필수** | redirect/origin/audience/provider 정책의 stable key |
| Product | Phase 1에는 client metadata로 충분 | 별도 복잡한 aggregate는 과설계 |
| Channel `WEB/IOS/ANDROID` | **Phase 1 필수** | callback와 session 보호 방식이 다름 |
| Redirect URI Allowlist | **Phase 1 필수** | runtime raw redirect 입력 금지 유지 |
| Allowed Origin | **Web 연결 전 필수** | CORS/CSRF와 redirect는 다른 allowlist |
| Token Audience | **추출 전 필수** | Carelog/Finance resource token 오용 방지 |
| Provider Enablement | Phase 1 최소 boolean/set | client별 허용 provider만 필요, 복잡한 policy engine 불필요 |

### 11.2 목표 Frontend Port

현재 DTO에서 바로 이동 가능한 최소안이다.

```ts
type AuthProvider = 'kakao' | (string & {});
type AuthChannel = 'WEB' | 'IOS' | 'ANDROID';

interface AuthApiClient {
  startAuthorization(input: {
    provider: AuthProvider;
    clientId: string;
    channel: AuthChannel;
    returnTo: string;
  }): Promise<{
    authorizationUrl: string;
  }>;

  exchange(input: {
    provider: AuthProvider;
    code: string;
    state: string;
  }): Promise<AuthSessionResult>;

  refresh(): Promise<AuthSessionResult>;
  logout(): Promise<void>;
  getCurrentSession(): Promise<CurrentSession>;
}

interface AuthSessionResult {
  accessToken: string;
  accessTokenExpiresAt: string;
  session: CurrentSession;
}

interface CurrentSession {
  authenticated: boolean;
  accountId?: string;
  clientId?: string;
  audience?: string[];
}
```

Web `refresh()`는 HttpOnly cookie를 사용해 body에 refresh token을 받지 않는다. Mobile은
같은 Port 뒤의 native session adapter가 secure storage의 refresh credential을
전달한다. Transport DTO가 달라도 Finance application code는 Port에만 결합한다.

Provider-neutral endpoint는 다음 migration 후보지만, Phase 1에서 기존 Kakao path를 즉시
삭제할 필요는 없다.

```text
POST /api/v1/auth/oauth/{provider}/authorization
POST /api/v1/auth/oauth/{provider}/exchange
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/auth/session
```

기존 Kakao endpoint를 compatibility alias로 유지하고 contract test로 동등성을 고정한 뒤
소비자 전환 후 제거한다.

## 12. Callback 책임 경계

| 경계 | 책임 |
| --- | --- |
| Shared Identity | provider 통신, state/PKCE, redirect allowlist, client/provider enablement, token/session, stable error code |
| Finance Frontend | 로그인 UI, callback route, `code/state` 전달, callback URL query 즉시 제거, Finance 성공 이동/오류 UX, Auth Port adapter |
| Finance Backend | 금융 업무 권한, Finance profile, 금융 domain data; Identity table 직접 접근 금지 |

Frontend는 provider secret, verifier, challenge, raw redirect URI, 자체 OAuth state를
소유하지 않는다. Shared Identity는 Finance 화면 이동 정책이나 금융 권한을 소유하지
않는다.

## 13. Migration Roadmap

| Phase | 선행 조건 | 완료 조건 | Repository / Owner | Risk | Rollback |
| --- | --- | --- | --- | --- | --- |
| 0 사실 고정 | clean revision | 이 문서와 matrix review | carelog-be docs / Identity+FE owners | 사실/목표 혼동 | Draft 폐기 |
| 1 Consumer Contract | Phase 0 합의 | client registry, channel, redirect/origin, audience, error/session ADR+contract tests | carelog-be Auth+Gateway / Identity owner | 기존 Carelog client 깨짐 | 기존 Kakao path/JSON refresh 유지 |
| 2 Auth Service 추출 | schema/API ownership 확정 | 독립 배포에서 Carelog contract test 통과, Carelog Core가 Identity DB 직접 접근하지 않음 | Carelog Auth Service / Identity owner | data/token cutover | Gateway route를 기존 carelog-be로 복귀 |
| 3 Finance Web 연결 | Web client 등록, CORS/cookie/session 완료 | login/callback/refresh/logout/session/guard E2E | Finance FE + Shared Identity + Gateway / FE·Identity owners | cookie/CORS/redirect 장애 | Finance auth feature flag off |
| 4 Capacitor 연결 | appId, native platforms, link association, secure storage | iOS/Android system-browser 실기기 E2E | Finance FE + Shared Identity / Mobile·Identity owners | callback/secure storage 차이 | Web-only 유지, native auth off |
| 5 Shared Identity 일반화 | 두 제품 소비 사실 축적 | Carelog/Finance/Dev client별 audience/provider/session 격리 | Shared Identity + consumers / Platform owner | premature generalization | client별 adapter 유지 |

## 14. Core vs Repetitive Work

### 14.1 사용자 직접 구현 권장 Core

| Core | 왜 Core인가 | 시작 파일/영역 | 예상 범위 | 핵심 불변식 |
| --- | --- | --- | --- | --- |
| Product Client Registry | 모든 보안 정책의 trust anchor | `auth/app/oauth`, 신규 domain/migration | client, product metadata, channel, provider | 알 수 없는/disabled client fail closed |
| Redirect URI 검증 | OAuth code 탈취 경계 | `OAuthRedirectUriResolver`, `ReturnToValidator` | exact allowlist와 등록 API/설정 | 요청 raw URI 신뢰 금지, exact match |
| Token Audience | service 간 token confused-deputy 방지 | `JwtTokenProvider`, Gateway `JwtVerifier` | `iss/aud` 발급·검증·rotation | audience 불일치 token 거부 |
| Web/Mobile Session | 보안 모델이 근본적으로 다름 | `AuthController`, session port, Gateway CORS | cookie/CSRF와 mobile credential | JS에서 Web refresh token 접근 불가; mobile secure storage |
| Auth Service API 경계 | 추출 후 호환성과 ownership 결정 | Auth controller/ports/contracts | versioning, session/error DTO | Carelog entity가 public DTO에 없음 |
| Auth Schema/Migration 소유권 | rollback과 data integrity 핵심 | V2/V4/V5 Identity migrations | schema 분리/dual-read/cutover | CUSTOMER backfill 금지, account FK 정합 |
| Token Revocation | logout/탈취 대응의 일관성 | blacklist/session ports + Gateway | session별 revoke, TTL, failure policy | revoke된 access는 Gateway에서 거부 |

### 14.2 Codex 반복 작업

- 합의된 DTO/record와 serializer 작성
- 기존 Kakao endpoint compatibility adapter
- stable error code mapping과 fixture
- Finance FE typed client/Session Adapter/route/guard의 반복 구현
- callback query 제거와 화면별 error mapping
- Gateway exact route/rate-limit/CORS contract test
- Vitest/Playwright/MockMvc fixture와 회귀 테스트
- Migration/rollback 스크립트의 기계적 뼈대
- 계약 문서, OpenAPI 예제, compatibility matrix 갱신

Core 불변식과 정책을 사람이 먼저 결정하고, Codex는 그 결정이 반복 코드와 테스트 전반에
일관되게 적용되도록 맡기는 경계가 적절하다.

## 15. Open Decisions

1. Finance Web과 Shared Identity를 same-site로 배치할지 cross-site로 배치할지
2. Web refresh cookie의 `SameSite`, CSRF token/header, domain/path 범위
3. Mobile refresh credential을 JWT로 유지할지 opaque rotating token으로 바꿀지
4. `clientId`와 token `aud`의 cardinality 및 Finance Backend audience 이름
5. Current Session이 Identity profile을 어디까지 반환할지
6. Carelog `organizationId/role/publicId`를 Identity token에서 제거하고 product
   membership 조회로 옮기는 cutover 순서
7. iOS/Android callback을 universal/app link만 허용할지 custom scheme fallback을 둘지
8. account당 단일 refresh session을 client/device별 session으로 확장할지
9. symmetric JWT에서 issuer/JWKS 기반 검증으로 전환하는 시점
10. `returnTo` 검증 결과를 exchange/session response에 반환할지 FE가 별도 transaction
    context를 소유할지

## 16. Security Notes

- browser storage에 refresh token을 영구 저장하지 않는다.
- callback은 `code/state`를 교환한 직후 history replace로 query를 제거한다.
- OAuth state/PKCE verifier는 계속 server-only로 유지한다.
- redirect allowlist와 CORS allowed origin은 목적이 다르므로 별도 필드로 관리한다.
- allowed origin wildcard와 credentials 조합을 Production 계약으로 사용하지 않는다.
- Gateway public route는 method + exact path, backend direct access는 gateway secret으로
  fail closed를 유지한다.
- client IP rate limit은 실제 proxy topology와 `trusted-proxy-hops`를 함께 배포·검증한다.
- stable error code를 추가하되 provider raw error나 credential을 노출하지 않는다.
- raw refresh token DB 저장은 추출 전 hash/opaque session identifier 전환을 검토한다.
- CUSTOMER를 Identity Principal로 해석하거나 Platform Account로 backfill하지 않는다.

## 17. Evidence File Paths

### carelog-be `dev@fbe74514...`

- `docs/foundation/foundation-context.md`
- `carelog-be/src/main/java/carelog/carelog/auth/web/AuthController.java`
- `carelog-be/src/main/java/carelog/carelog/auth/web/dto/request/*`
- `carelog-be/src/main/java/carelog/carelog/auth/web/dto/response/*`
- `carelog-be/src/main/java/carelog/carelog/auth/app/AuthServiceImpl.java`
- `carelog-be/src/main/java/carelog/carelog/auth/app/AuthTokenIssuanceService.java`
- `carelog-be/src/main/java/carelog/carelog/auth/app/JwtTokenProvider.java`
- `carelog-be/src/main/java/carelog/carelog/auth/app/oauth/*`
- `carelog-be/src/main/java/carelog/carelog/auth/app/adapter/oauth/RedisOAuthStateStore.java`
- `carelog-be/src/main/java/carelog/carelog/auth/app/adapter/oauth/kakao/*`
- `carelog-be/src/main/java/carelog/carelog/auth/app/port/oauth/*`
- `carelog-be/src/main/java/carelog/carelog/auth/app/adapter/LegacyTokenSessionAdapter.java`
- `carelog-be/src/main/java/carelog/carelog/common/config/SecurityConfig.java`
- `carelog-be/src/main/java/carelog/carelog/auth/web/GatewayHeaderAuthFilter.java`
- `carelog-be/src/main/java/carelog/carelog/common/web/exception/ExceptionStatus.java`
- `carelog-be/src/main/java/carelog/carelog/common/web/exception/GlobalExceptionHandler.java`
- `carelog-be/src/main/java/carelog/carelog/identity/domain/PlatformAccount.java`
- `carelog-be/src/main/java/carelog/carelog/identity/domain/ExternalIdentity.java`
- `carelog-be/src/main/resources/application.yml`
- `carelog-be/src/main/resources/db/migration/V2__identity_foundation.sql`
- `carelog-be/src/main/resources/db/migration/V5__refresh_token_account_id.sql`

### Gateway PR #34 `d367a243...`

- `carelog-gateway/src/main/kotlin/carelog/gateway/config/GatewayConfig.kt`
- `carelog-gateway/src/main/kotlin/carelog/gateway/filter/JwtGlobalFilter.kt`
- `carelog-gateway/src/main/kotlin/carelog/gateway/filter/OAuthPublicRequestMatcher.kt`
- `carelog-gateway/src/main/kotlin/carelog/gateway/filter/OAuthRateLimitFailureClosedFilter.kt`
- `carelog-gateway/src/main/kotlin/carelog/gateway/ratelimit/OAuthClientIpKeyResolver.kt`
- `carelog-gateway/src/main/kotlin/carelog/gateway/ratelimit/OAuthFailClosedRateLimiter.kt`
- `carelog-gateway/src/main/resources/application.yml`

### Finance FE `master@8634e460...`

- `/Users/work/Github/finance-harness-fe/package.json`
- `/Users/work/Github/finance-harness-fe/.node-version`
- `/Users/work/Github/finance-harness-fe/src/main.tsx`
- `/Users/work/Github/finance-harness-fe/src/app/AppRouter.tsx`
- `/Users/work/Github/finance-harness-fe/src/constants/routes.ts`
- `/Users/work/Github/finance-harness-fe/capacitor.config.ts`
- `/Users/work/Github/finance-harness-fe/docs/route-architecture.md`
- `/Users/work/Github/finance-harness-fe/docs/frontend-roadmap.md`
- `/Users/work/Github/finance-harness-fe/docs/product-policy.md`

## 18. 결론

Finance FE는 현재 Auth를 **부분적으로** 소비할 수 있다. Local Web의 통제된
same-origin/dev-proxy 환경은 얇은 Adapter로 가능하지만, Production Web은 Shared
consumer로서 필요한 client/redirect/audience/error/Web session/CORS 계약 확장 후에만
가능하다. iOS/Android는 현재 native project와 callback/session 기반이 없고 backend도
`MOBILE` 하나로만 모델링하므로 현재 계약으로 불가능하다.

Auth Service 추출 전에 가장 먼저 사람이 직접 고정할 Core는 다음 네 가지다.

1. Product Client Registry
2. Redirect URI exact allowlist
3. Token issuer/audience
4. Web cookie와 Mobile secure-storage를 분리한 session 계약

이 네 가지 없이 물리 추출부터 하면 Carelog 결합을 새 서비스 안에 그대로 옮기게 된다.
