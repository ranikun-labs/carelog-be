---
module: carelog-be (SCG 인증 전환)
last_updated: 2026-06-24
branch: refactor/scg-monorepo
---

## 현재 상태

| 작업 | 상태 | 비고 |
|------|------|------|
| Gradle Multi-Module 모노레포 전환 | ✅ | settings.gradle, 루트 build.gradle |
| carelog-gateway 모듈 신규 생성 | ✅ | SCG JwtGlobalFilter, RedisBlacklistService |
| X-Gateway-Secret 헤더 방어 | ✅ | JwtGlobalFilter strip + 주입 |
| UserPrincipal 인터페이스 도입 | ✅ | CustomUserDetails, GatewayUserDetails 공통 타입 |
| GatewayUserDetails 신규 생성 | ✅ | 헤더 기반, DB 조회 없음 |
| GatewayHeaderAuthFilter | ✅ | X-Gateway-Secret 검증 + SecurityContext 설정 |
| TenantFilter UserPrincipal 전환 | ✅ | instanceof UserPrincipal |
| JwtAuthenticationFilter 제거 | ✅ | 파일 삭제 완료 |
| SecurityConfig 헤더 기반 전환 | ✅ | GatewayHeaderAuthFilter + TenantFilter |
| RedisBlacklistService (addToBlacklist) | ✅ | blacklist: prefix, TTL |
| JwtTokenProvider.getRemainingValidity() | ✅ | L135 |
| AuthService.logout() accessToken 파라미터 | ✅ | Redis Blacklist + DB Refresh 삭제 |
| AuthController.logout() Authorization 헤더 추출 | ✅ | |
| LoggingAspect @Around !within(*..*Filter) | ❌ | @AfterThrowing엔 있음, @Around 3개 누락 |
| GitHub Actions path filter 워크플로우 | ❌ | be/gateway 배포 독립 |

## 핵심 결정 로그

| 날짜 | 결정 | 이유 |
|------|------|------|
| 2026-06-xx | UserPrincipal 인터페이스 도입 (CustomUserDetails + GatewayUserDetails 공통) | TenantFilter가 두 구현체를 instanceof 하나로 처리. 기존 컨트롤러/서비스 코드 변경 없음 |
| 2026-06-xx | GatewayHeaderAuthFilter: 헤더 없으면 통과 (403 아님) | 공개 경로(login/refresh/register)는 헤더 자체가 없음. 인증 강제는 SecurityConfig permitAll/authenticated로 위임 |
| 2026-06-xx | 로그아웃 Option A — carelog-be 전담 | SCG는 라우팅/검증만, DB 접근(Refresh 삭제)은 carelog-be 유지. shared Redis(blacklist: prefix)로 SCG 블랙리스트 체크 공유 |
| 2026-06-xx | GatewayHeaderAuthFilter @Component 금지 | OncePerRequestFilter + @Component = CGLIB final 메서드 프록시 실패 → SecurityConfig에서 new로 직접 생성 |

## 파일 맵

```
carelog-be/src/main/java/carelog/carelog/
├── auth/
│   ├── app/
│   │   ├── GatewayUserDetails.java      # 헤더 기반 UserDetails (DB 조회 없음)
│   │   ├── UserPrincipal.java           # 공통 인터페이스 (getUserId, getOrganizationId, ...)
│   │   ├── JwtTokenProvider.java        # getRemainingValidity() L135
│   │   ├── RedisBlacklistService.java   # addToBlacklist(token, ttl)
│   │   ├── AuthService.java
│   │   └── AuthServiceImpl.java         # logout(userId, accessToken)
│   └── web/
│       ├── GatewayHeaderAuthFilter.java # X-Gateway-Secret 검증 + SecurityContext 설정
│       └── AuthController.java          # logout: Authorization 헤더 추출
├── common/
│   ├── config/
│   │   ├── SecurityConfig.java          # GatewayHeaderAuthFilter + TenantFilter 등록
│   │   └── aop/
│   │       └── LoggingAspect.java       # ⚠️ @Around 3개에 !within(*..*Filter) 누락
│   └── filter/
│       └── TenantFilter.java            # instanceof UserPrincipal

carelog-gateway/src/main/kotlin/carelog/gateway/
├── filter/
│   └── JwtGlobalFilter.kt               # JWT 검증 + Blacklist 체크 + 헤더 주입/strip
└── service/
    └── RedisBlacklistService.kt          # isBlacklisted (ReactiveStringRedisTemplate)
```

## 남은 태스크

| # | 작업 | 파일 | 비고 |
|---|------|------|------|
| 1 | LoggingAspect @Around 3개에 `&& !within(*..*Filter)` 추가 | `common/config/aop/LoggingAspect.java` L21, L59, L90 | 방어적 처리 |
| 2 | GitHub Actions path filter 워크플로우 | `.github/workflows/` | be/gateway 배포 독립 |
| 3 | Step 5 feat/journal 착수 | 새 브랜치 | 아래 설계 결정 참고 |

## 핸드오프 — 새 Claude 세션 시작 시 붙여넣기

```
refactor/scg-monorepo 브랜치 Step 3 마무리 단계.
SCG(carelog-gateway) → carelog-be 헤더 기반 인증 전환 완료.

완료된 것 ✅:
- GatewayHeaderAuthFilter (X-Gateway-Secret 검증 + SecurityContext 설정)
- UserPrincipal 인터페이스 + GatewayUserDetails (헤더 기반)
- JwtAuthenticationFilter 제거, SecurityConfig 헤더 기반 전환
- RedisBlacklistService, JwtTokenProvider.getRemainingValidity()
- AuthService/AuthController logout 완성

남은 것:
1. LoggingAspect @Around 3개 (L21, L59, L90) 에 && !within(*..*Filter) 추가
   - @AfterThrowing(L103)엔 이미 있음. @Around만 누락.
   - 파일: carelog-be/src/main/java/carelog/carelog/common/config/aop/LoggingAspect.java
2. GitHub Actions path filter 워크플로우 (.github/workflows/)
3. 이후 Step 5 feat/journal 브랜치 착수

핵심 결정:
- GatewayHeaderAuthFilter: @Component 금지, SecurityConfig에서 new로 직접 생성
- 헤더 없으면 403 아닌 통과 → SecurityConfig permitAll/authenticated가 인증 강제
- 로그아웃 Option A: carelog-be가 Redis blacklist + DB refresh 모두 처리

레퍼런스:
- docs/context/scg-monorepo/carelog-be.md (이 파일)
- docs/scg-auth-design.md (SCG 설계 원본)
- carelog-be/src/main/java/carelog/carelog/auth/web/GatewayHeaderAuthFilter.java
- carelog-be/src/main/java/carelog/carelog/common/config/SecurityConfig.java

규칙: 코드는 보여주기만 하고 직접 타이핑함. diff 형식(+/- 마커)으로 보여줄 것.
```
