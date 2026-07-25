# Spring Cloud Gateway 인증 설계

> 작성일: 2026-03-19

---

## 전체 흐름

```
클라이언트
    ↓ HTTPS
Cloudflare Tunnel
    ↓
[EC2] carelog-gateway (SCG + Redis)
    ├── 공개 경로 → 그냥 통과
    ├── JWT 서명 검증 (HS256, jjwt)
    ├── Redis Blacklist 조회 → 있으면 401
    └── 검증 통과 시 헤더 추가 후 내부망으로 포워딩
         X-User-Id, X-Organization-Id, X-Role, X-Public-Id
    ↓ 내부망
[온프레미스] carelog-be
    ├── GatewayHeaderAuthFilter: 헤더 → CustomUserDetails → SecurityContext
    ├── TenantFilter: X-Organization-Id → TenantContext (ThreadLocal)
    ├── TenantAspect: Hibernate Filter 활성화
    └── 비즈니스 로직
```

---

## 저장소 역할

| 저장소 | 데이터 | TTL |
|--------|--------|-----|
| 클라이언트 | Access Token + Refresh Token | - |
| Redis (EC2) | Blacklist된 Access Token | 남은 유효기간 (자동 만료) |
| PostgreSQL (온프레미스) | Refresh Token | 7일 |

---

## 로그아웃 처리: Option A (carelog-be 전담)

```
POST /auth/logout
    ↓ SCG: JWT 검증 + 헤더 추가
    ↓ carelog-be
    ├── DB: RefreshToken 삭제
    └── Redis: AccessToken → blacklist:{token} = "1" (TTL = 남은 유효기간)
```

**Option B (SCG가 로그아웃 담당)를 선택하지 않은 이유**
- SCG가 DB에 접근해야 함 (RefreshToken 삭제)
- SCG는 라우팅/검증 전담, 비즈니스 로직은 carelog-be에 집중
- shared Redis 사용으로 carelog-be → Redis 직접 쓰기 가능

---

## ⚠️ 인프라 전제 조건

### Redis 네트워크 접근 경로
carelog-be(온프레미스)가 로그아웃 시 EC2 Redis에 직접 쓰기 접근이 필요하다.

```
[온프레미스] carelog-be → [EC2] Redis:6379
```

이 경로가 열려 있지 않으면 Option A 설계 자체가 성립하지 않는다.

**확인 필요 항목 (코드가 아닌 인프라 작업)**
- Cloudflare Tunnel 또는 VPN으로 온프레미스 ↔ EC2 양방향 통신이 가능한 상태인지
- EC2 Security Group에서 Redis 포트(6379)가 온프레미스 IP에 열려 있는지
- Redis에 인증(requirepass) 설정 여부

> 이 부분은 코드로 해결되지 않는다. 인프라 구성 확정 후 진행할 것.

---

## SCG 공개 경로 (JWT 검증 제외)

```
/api/v1/auth/login       ← 로그인 (토큰 없음)
/api/v1/auth/refresh     ← 토큰 갱신 (Refresh Token은 body로)
/api/v1/users/managers   ← 매니저 가입 (토큰 없음)
```

---

## carelog-be 인증 전환 전략

### JwtAuthenticationFilter 제거 → GatewayHeaderAuthFilter 추가

SCG가 JWT를 검증하고 헤더로 유저 정보를 전달하므로, carelog-be에서 JWT 직접 검증 불필요.

```
[제거] JwtAuthenticationFilter — JWT 파싱 + DB 조회로 SecurityContext 설정
[추가] GatewayHeaderAuthFilter — 헤더 읽어서 SecurityContext 설정 (DB 조회 없음)
```

### CustomUserDetails 하위 호환

기존 컨트롤러/서비스 전체가 `@AuthenticationPrincipal CustomUserDetails`를 사용 중이므로,
`CustomUserDetails`에 헤더 기반 생성자를 추가해 하위 호환을 유지한다.

```java
// 기존 (DB 조회 결과인 User 엔티티로 생성)
public CustomUserDetails(User user) { ... }

// 신규 (헤더 값으로 직접 생성, DB 조회 없음)
public CustomUserDetails(String userId, UUID organizationId, String role, UUID publicId) { ... }
```

이로 인해 변경이 필요 없는 것들:
- 모든 컨트롤러 (`@AuthenticationPrincipal CustomUserDetails` 그대로)
- `TenantFilter` (`authentication.getPrincipal() instanceof CustomUserDetails` 그대로)
- `AuditorAwareImpl` (`userDetails.getUsername()` → userId 반환, 그대로)

### SecurityConfig 변경 포인트

```java
// 제거
.addFilterBefore(new JwtAuthenticationFilter(...), UsernamePasswordAuthenticationFilter.class)

// 추가
.addFilterBefore(new GatewayHeaderAuthFilter(), UsernamePasswordAuthenticationFilter.class)
```

---

## ⚠️ 헤더 스푸핑 방어 (보안 필수)

SCG가 검증 후 `X-User-Id` 등을 주입하는 구조에서, 클라이언트가 해당 헤더를 직접 조작해 요청을 보내면 SCG를 우회하고 권한을 탈취할 수 있다.

```
악의적 클라이언트 → SCG에 X-User-Id: admin 헤더 직접 포함
                  → SCG가 검증 없이 그대로 carelog-be에 포워딩
                  → carelog-be가 해당 헤더를 신뢰 → 권한 우회
```

### 방어 방법: SCG에서 인입 헤더 제거 (RemoveRequestHeader 필터)

`JwtGlobalFilter`에서 JWT 검증 전에, 클라이언트가 보낸 동명 헤더를 먼저 제거한다.

```java
// JwtGlobalFilter 내부 — 헤더 주입 전에 먼저 strip
ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
    .headers(headers -> {
        headers.remove("X-User-Id");
        headers.remove("X-Organization-Id");
        headers.remove("X-Role");
        headers.remove("X-Public-Id");
    })
    .build();
// 이후 검증된 값으로 재주입
```

또는 `application.yml`에서 route 필터로 선언:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: carelog-be
          uri: ${CARELOG_BE_URI}
          predicates:
            - Path=/api/**
          filters:
            - RemoveRequestHeader=X-User-Id
            - RemoveRequestHeader=X-Organization-Id
            - RemoveRequestHeader=X-Role
            - RemoveRequestHeader=X-Public-Id
```

> **주의**: 공개 경로(`/auth/login` 등)도 동일하게 적용되어야 한다. 공개 경로는 JWT 검증을 건너뛰더라도 헤더 strip은 반드시 실행해야 한다.

---

## LoggingAspect 방어 처리 (GatewayHeaderAuthFilter 추가 시 함께)

기존 CGLIB 버그 이력: `@Component` + `OncePerRequestFilter` 조합 시 CGLIB이 `final` 메서드(`doFilter`)를 프록시하려다 실패 → `@Slf4j log` 필드 null → NPE.

`GatewayHeaderAuthFilter`는 `@Component` 미사용으로 AOP 대상이 되지 않지만, 실수로 달릴 경우를 대비해 `LoggingAspect`의 `@Around` 포인트컷 3개에 `!within(*..*Filter)` 추가.

```java
// 현재: @AfterThrowing만 적용되어 있음
@AfterThrowing(pointcut = "execution(* carelog.carelog..*(..)) && !within(*..*Filter)", ...)

// 추가 필요: @Around 3개
@Around("execution(* carelog.carelog..web.*Controller.*(..)) && !within(*..*Filter)")
@Around("execution(* carelog.carelog..app.*ServiceImpl.*(..)) && !within(*..*Filter)")
@Around("execution(* carelog.carelog..domain.*Repository.*(..)) && !within(*..*Filter)")
```

---

## 모노레포 빌드 설정 주의사항

### subprojects 블록 최소화
루트 `build.gradle`의 `subprojects` 블록에는 `group`, `version`, `repositories`만 넣는다.
Kotlin 설정(`jvmToolchain` 등)을 subprojects에 넣으면 Java 모듈인 carelog-be도 영향받으므로,
모듈별 설정은 반드시 각 `build.gradle` / `build.gradle.kts`에서 선언한다.

### .env 파일 처리
- `co.uzzu.dotenv.gradle`은 적용된 서브프로젝트의 `projectDir`에서 `.env`를 탐색
- `carelog-be/build.gradle`에 적용 → `carelog-be/.env` 자동 인식 (경로 문제 없음)
- `cp .env carelog-be/.env` 후 루트 원본은 `rm .env`로 삭제 필요 (`.gitignore`에 있어 git 추적 안 됨)

---

## carelog-gateway 모듈 구성

| 컴포넌트 | 역할 |
|----------|------|
| `JwtGlobalFilter` | `GlobalFilter` + `Ordered` 구현. JWT 검증 + Blacklist 체크 + 헤더 추가 |
| `JwtVerifier` | jjwt 기반 서명 검증 + Claims 추출 |
| `RedisBlacklistService` | `ReactiveStringRedisTemplate` 기반 Blacklist 조회/등록 |

**의존성**
```gradle
spring-cloud-starter-gateway          // SCG (Reactive 기반)
spring-boot-starter-data-redis-reactive  // ReactiveStringRedisTemplate
jjwt-api:0.12.3                       // JWT 검증
```

**Spring Cloud 버전**: `2024.0.1` (Spring Boot 3.4.x 호환)

---

## shared Redis 키 규칙

```
blacklist:{accessToken}  →  값: "1"  TTL: 남은 유효기간
```

- carelog-gateway: `isBlacklisted(token)` 조회
- carelog-be: `addToBlacklist(token, ttl)` 등록 (로그아웃 시)

---

## 모듈별 인프라

| 서비스 | 배포 환경 | 포트 |
|--------|-----------|------|
| carelog-gateway | EC2 | 9000 |
| carelog-be | 온프레미스 | 8080 |
| Redis | EC2 (gateway와 동일) | 6379 |
| PostgreSQL | 온프레미스 | 5432 |