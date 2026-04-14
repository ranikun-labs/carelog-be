# SCG 구현 투두리스트

## Context
carelog-be(온프레미스)와 carelog-gateway(EC2)를 Gradle Multi-Module 모노레포로 구성.
SCG: JWT 검증 + Redis Blacklist 체크 + 라우팅 담당
carelog-be: SCG가 주입한 헤더로 인증, 로그아웃 시 Redis Blacklist 등록

보안 설계:
- SCG → BE 헤더 위조 방어: `X-Gateway-Secret` 내부 시크릿 헤더 (SCG가 붙이고 BE가 검증)
- Blacklist 체크는 SCG에서, Blacklist 등록은 BE(logout)에서 — shared Redis

---

## 진행 현황

### ✅ 완료
- 모노레포 전환 (`settings.gradle`, 루트 `build.gradle`, carelog-be 이동)
- `carelog-gateway` 모듈 전체 (`build.gradle.kts`, `CarelogGatewayApplication.kt`, `JwtVerifier.kt`)
- `RedisBlacklistService.kt` (SCG — isBlacklisted 체크용)
- `JwtGlobalFilter.kt` (JWT 검증 + Blacklist 체크 + X-Gateway-Secret 포함 헤더 주입)
- `application.yml` (carelog-gateway — internal-secret 포함)
- `docker-compose.yml` — Redis 추가
- `.env` — `GATEWAY_INTERNAL_SECRET` 추가
- `UserPrincipal` 인터페이스 신규 생성
- `CustomUserDetails` — `implements UserPrincipal` 추가
- `GatewayUserDetails` 신규 생성 (헤더 기반)
- `TenantFilter` — `instanceof UserPrincipal` 로 변경

---

## 남은 작업

### Step C. carelog-be 인증 전환 (계속)

#### C-2. 필터 교체
- [ ] `GatewayHeaderAuthFilter` — SecurityContext 설정 완성 (파일은 있으나 미완성)
  - 현재: X-Gateway-Secret 검증까지만 구현됨
  - 남은 것: UsernamePasswordAuthenticationToken 생성 + SecurityContext 설정 + filterChain.doFilter
- [ ] `JwtAuthenticationFilter.java` 삭제
- [ ] `SecurityConfig` — `JwtAuthenticationFilter` 제거, `GatewayHeaderAuthFilter` 등록

#### C-3. 로그아웃 Redis Blacklist 등록
- [ ] `RedisBlacklistService` (carelog-be) 신규 생성 — `addToBlacklist(token, ttl)`
- [ ] `JwtTokenProvider` — `getRemainingValidity(token): Duration` 추가
- [ ] `AuthService` — `logout(userId, accessToken)` 시그니처 변경
- [ ] `AuthServiceImpl.logout()` — Redis blacklist 등록 + DB RefreshToken 삭제
- [ ] `AuthController.logout()` — `Authorization` 헤더에서 accessToken 추출 전달

### Step D. GitHub Actions CI/CD
- [ ] `.github/workflows/deploy-carelog-be.yml` — `paths: ['carelog-be/**']`
- [ ] `.github/workflows/deploy-carelog-gateway.yml` — `paths: ['carelog-gateway/**']`

### 추후 작업 (MVP 이후)
- Distributed Tracing — Micrometer Trace-ID 전파 (Trace-ID → 로그 MDC)
- Circuit Breaker — Resilience4j (연동 서비스 늘어날 때)

---

## 검증
- `POST /api/v1/auth/login` → 200, AccessToken 발급
- 토큰으로 `GET /api/v1/users/customers` → SCG 통과 → 200
- 헤더 없이 BE 8080 직접 호출 → 403 (X-Gateway-Secret 없음)
- `POST /api/v1/auth/logout` → Redis `blacklist:{token}` 등록 확인
- 로그아웃 토큰 재사용 → SCG에서 401
