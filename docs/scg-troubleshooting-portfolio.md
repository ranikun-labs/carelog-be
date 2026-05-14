# Spring Cloud Gateway 도입기 — 인증 아키텍처 전환과 트러블슈팅

## 배경과 문제 의식

carelog는 의료진 전용 상담·치료 기록 서비스다. 초기에는 Spring Boot 단일 모듈로 JWT 검증을 BE 내부에서 처리했다. 서비스가 성장하면서 다음 두 가지 문제가 명확해졌다.

**첫째, 인증 로직의 중복.** 서비스가 늘어날수록 각 서비스마다 JWT 검증 코드를 복사하게 된다. 변경 시 모든 서비스를 동시에 배포해야 하는 결합도 문제가 생긴다.

**둘째, 내부 서비스 직접 접근 차단 불가.** BE 포트(8080)가 노출된 상태에서는 JWT를 위조하거나 헤더를 조작해 직접 접근하는 공격을 막기 어렵다.

이를 해결하기 위해 **Spring Cloud Gateway(SCG)를 도입해 인증을 Gateway로 집중**하기로 했다. 동시에 BE는 Gateway가 주입한 헤더만 신뢰하는 구조로 전환했다.

---

## 목표 아키텍처

```
외부 클라이언트
      │
      ▼
 SCG (9000)  ─────────────────────────────────────
  │  ① JWT 서명 검증                               │
  │  ② Redis Blacklist 체크 (로그아웃 토큰 차단)    │
  │  ③ X-User-Id, X-Role 등 사용자 헤더 주입       │
  │  ④ X-Gateway-Secret 주입 (내부 신뢰 표시)      │
  └──────────────────────────────────────────────▶  BE (8080)
                                                    │
                                              GatewayHeaderAuthFilter
                                              X-Gateway-Secret 검증
                                              SecurityContext 설정
```

보안의 핵심 원칙은 두 가지다.

- **헤더 스푸핑 방어**: 외부 요청의 X-User-Id 등 헤더를 Gateway에서 제거 후 재주입. 클라이언트가 헤더를 위조해도 덮어씌워진다.
- **내부 신뢰 채널**: X-Gateway-Secret은 Gateway만이 알고 있는 내부 시크릿. BE는 이 헤더가 없는 요청을 전부 403으로 차단해 직접 접근을 원천 봉쇄한다.

이 설계를 구현하는 과정에서 다섯 가지 문제를 연속으로 마주쳤다.

---

## 구현 과정과 트러블슈팅

### 1단계: Gateway 설정 바인딩 — `@Value`로 YAML 리스트 주입 실패

Gateway를 처음 기동했을 때 바로 막혔다.

```
Could not resolve placeholder 'gateway.public-paths' in value "${gateway.public-paths}"
```

`JwtGlobalFilter`에서 인증을 건너뛸 public path 목록을 `@Value`로 주입하려 했는데, Spring이 해당 프로퍼티를 찾지 못한다는 오류였다. 설정 파일을 다시 봤을 때는 멀쩡했다.

```yaml
gateway:
  public-paths:
    - /api/v1/auth/login
    - /api/v1/auth/refresh
```

원인은 Spring의 `@Value` 메커니즘 자체에 있었다. `@Value`는 단일 scalar 값을 placeholder로 치환한다. YAML 시퀀스는 내부적으로 `gateway.public-paths[0]`, `gateway.public-paths[1]`처럼 인덱싱된 키로 분해되기 때문에, `gateway.public-paths`라는 키 자체는 scalar로 존재하지 않는다. `PropertySourcesPropertyResolver`가 조회할 키가 없으니 resolve에 실패한 것이다.

해결 방법은 `@ConfigurationProperties`로 전환이었다. `@ConfigurationProperties`는 prefix 하위 프로퍼티 전체를 타입에 맞게 바인딩하므로 YAML 시퀀스를 `List<String>`으로 자동 변환한다.

```kotlin
// Before
class JwtGlobalFilter(
    @param:Value("\${gateway.public-paths}") private val publicPaths: List<String>,
    @param:Value("\${gateway.internal-secret}") private val internalSecret: String
)

// After
@ConfigurationProperties(prefix = "gateway")
data class GatewayConfig(
    val publicPaths: List<String> = emptyList(),
    val internalSecret: String = ""
)
```

단순히 "리스트는 `@ConfigurationProperties`로"라는 규칙보다 중요한 것은 **바인딩 대상의 성격**이다. 단일 값이면 `@Value`, 구조화된 설정 그룹이면 `@ConfigurationProperties`가 맞다. 특히 Gateway 설정은 공개 경로, 시크릿, 타임아웃 등이 계속 추가될 가능성이 높으므로 처음부터 `@ConfigurationProperties`로 묶어두는 것이 확장에 유리하다.

---

### 2단계: 보안 설계 오류 — public path에서 X-Gateway-Secret 누락

Gateway를 띄우고 로그인 API를 호출하자 BE에서 403이 떨어졌다. 인증이 필요 없는 public path인데도 불구하고.

```
GatewayHeaderAuthFilter: X-Gateway-Secret 없음 → 403
```

코드를 보니 public path 처리 로직에서 실수가 있었다.

```kotlin
// 헤더 스푸핑 방어 — 외부 요청의 헤더를 전부 제거
val sanitizedExchange = exchange.mutate()
    .request { it.headers { headers ->
        headers.remove("X-Gateway-Secret")  // 외부 요청의 위조 시크릿 제거
        headers.remove("X-User-Id")
        // ...
    }}.build()

// public path는 JWT 검증 없이 통과
if (publicPaths.any { path.startsWith(it) }) {
    return chain.filter(sanitizedExchange)  // X-Gateway-Secret이 없는 채로 BE 전달
}
```

스푸핑 방어를 위해 외부 요청의 X-Gateway-Secret을 제거했는데, public path 분기에서 새로 붙이는 걸 빠뜨렸다.

이 문제의 본질은 **"인증(Authentication)"과 "신뢰(Trust)"를 혼동한 것**이다.

- **X-Gateway-Secret** = "이 요청이 Gateway를 통해 왔는가" → 신뢰 레이어, **모든 요청**에 필요
- **JWT 검증** = "이 사용자가 누구인가" → 인증 레이어, public path는 불필요

public path는 인증을 면제하는 것이지, Gateway 신뢰 검증을 면제하는 게 아니다. public path도 반드시 Gateway를 경유해야만 BE에 도달할 수 있어야 한다. 그래야 BE 직접 접근이 public path에서도 차단된다.

```kotlin
// After — JWT 검증은 건너뛰되, X-Gateway-Secret은 반드시 붙임
if (publicPaths.any { path.startsWith(it) }) {
    val publicExchange = sanitizedExchange.mutate()
        .request { it.headers { headers ->
            headers.set("X-Gateway-Secret", internalSecret)
        }}.build()
    return chain.filter(publicExchange)
}
```

---

### 3단계: CORS + 필터 체인 — OPTIONS preflight 차단과 에러 응답 왜곡

Swagger UI(9000)에서 API를 호출하자 모든 요청이 차단됐다. 에러를 보니 예상과 달랐다.

```
Request Method: OPTIONS
Status Code: 403 Forbidden
```

실제 POST 요청도 아니고 OPTIONS가 막혔다. 더 이상한 건 그 다음 로그였다.

```
인증 실패: /api/v1/error - Full authentication is required to access this resource
```

원인이 두 겹으로 얽혀 있었다.

**첫 번째 겹 — CORS preflight.** 브라우저는 Cross-Origin 요청 전에 OPTIONS 메서드로 "이 요청 해도 되냐"를 먼저 묻는다. Swagger UI는 `localhost:9000`에서 로드됐지만 API 명세에 적힌 서버 주소가 `localhost:8080`이었기 때문에 포트가 달라 Cross-Origin으로 판단, OPTIONS preflight를 먼저 보냈다.

**두 번째 겹 — 연쇄 차단.** OPTIONS 요청에는 X-Gateway-Secret이 없으므로 `GatewayHeaderAuthFilter`가 `sendError(403)`을 반환했다. 그런데 Spring Boot는 `sendError()`를 받으면 내부적으로 `/error` 엔드포인트로 포워딩해 에러 응답을 구성한다. 문제는 이 포워딩 요청도 Spring Security 필터 체인을 통과한다는 점이다. `/error`가 `PUBLIC_URLS`에 없으니 Spring Security가 401을 반환했다. 실제 원인은 403인데 클라이언트는 401을 받아 디버깅이 어려워지는 에러 왜곡이 발생했다.

```
OPTIONS → GatewayHeaderAuthFilter → sendError(403)
        → 내부 포워딩 → GET /error
        → Spring Security → 인증 없음 → 401  ← 원인 왜곡
```

세 가지를 함께 수정했다.

```java
// 1. OPTIONS는 GatewayHeaderAuthFilter에서 제외 (CORS preflight 통과)
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return "OPTIONS".equalsIgnoreCase(request.getMethod());
}

// 2. Spring Security에 CORS 설정 추가
http.cors(cors -> cors.configurationSource(corsConfigurationSource()))

// 3. /error를 PUBLIC_URLS에 추가 (에러 응답 왜곡 방지)
private static final String[] PUBLIC_URLS = {
    "/auth/login", "/auth/refresh", "/users/managers",
    "/swagger-ui/**", "/v3/api-docs/**", "/api/v1",
    "/error"
};
```

다만 이 해결책은 완전하지 않다. MSA에서 **CORS는 Gateway에서 중앙 처리**하는 것이 원칙이다. BE가 CORS를 처리하면 서비스가 늘어날수록 설정이 분산된다. 현재 `shouldNotFilter`로 OPTIONS를 통과시키는 것도 임시방편이다. 운영 환경에서는 Gateway의 `spring.cloud.gateway.globalcors` 설정으로 이관해 OPTIONS가 BE까지 내려오지 않도록 해야 한다.

---

### 4단계: Swagger 서버 URL — Gateway 도입 후 API 호출이 BE를 직접 우회

CORS 문제를 해결하고 나니 또 다른 문제가 나타났다. 여전히 API 호출이 8080으로 가고 있었다.

```
Request URL: http://localhost:8080/api/v1/users/managers  ← Gateway 우회
```

Swagger UI를 9000을 통해 열었는데 API 호출은 8080으로 직접 가고 있었다. springdoc이 OpenAPI 명세를 생성할 때 `servers` 항목을 자동으로 애플리케이션 실행 주소(`localhost:8080`)로 채우기 때문이다. Swagger UI는 이 servers 항목을 보고 호출 주소를 결정하므로, Gateway를 거치지 않고 BE를 직접 호출한다.

```
브라우저 → localhost:9000/swagger-ui  (UI 로드)
         → localhost:8080/api/...     (API 호출, Gateway 우회!)
```

`SwaggerConfig`의 `OpenAPI` 빈에서 servers를 명시적으로 Gateway 주소로 고정했다.

```java
return new OpenAPI()
    .info(info)
    .servers(List.of(
        new Server().url("http://localhost:9000/api/v1").description("Local Gateway")
    ))
    .addSecurityItem(securityRequirement)
    .components(components);
```

여기서 `/api/v1`을 servers URL에 포함해야 하는 이유가 있다. springdoc이 생성하는 API 경로는 context-path 기준 상대 경로(`/users/managers`)다. Swagger는 이것을 servers URL과 단순 결합해 호출 URL을 만든다. servers URL이 `http://localhost:9000`이면 `http://localhost:9000/users/managers`가 되어 Gateway 라우팅(`/api/**`)에 걸리지 않는다. `/api/v1`을 포함해야 `http://localhost:9000/api/v1/users/managers`로 조합되어 정상 라우팅된다.

---

## 최종 아키텍처와 흐름 정리

다섯 가지 문제를 해결한 뒤 완성된 요청 흐름은 다음과 같다.

```
[인증이 필요한 요청]
클라이언트 → Gateway(9000)
  → 외부 X-User-Id 등 헤더 제거 (스푸핑 방어)
  → JWT 서명 검증
  → Redis Blacklist 체크
  → X-User-Id, X-Role, X-Public-Id, X-Gateway-Secret 주입
  → BE(8080)
     → GatewayHeaderAuthFilter: X-Gateway-Secret 검증
     → SecurityContext: GatewayUserDetails 설정
     → 비즈니스 로직 처리

[public path 요청]
클라이언트 → Gateway(9000)
  → 외부 X-User-Id 등 헤더 제거
  → JWT 검증 없이 통과
  → X-Gateway-Secret만 주입 (신뢰 레이어는 유지)
  → BE(8080)
     → GatewayHeaderAuthFilter: X-Gateway-Secret 검증 통과
     → SecurityContext: anonymous (사용자 정보 없음)
     → 비즈니스 로직 처리

[BE 직접 접근 시도]
공격자 → BE(8080) 직접
  → GatewayHeaderAuthFilter: X-Gateway-Secret 없음 → 403
```

---

## 핵심 인사이트

다섯 문제를 관통하는 공통 주제가 있다. **레이어 간 책임 경계를 명확히 하지 않으면 각 레이어가 서로의 역할을 침범하거나 누락시킨다.**

**신뢰(Trust) vs 인증(Authentication) 분리.** X-Gateway-Secret은 인프라 레이어의 신뢰 채널이고, JWT는 애플리케이션 레이어의 사용자 인증이다. 이 둘을 같은 것으로 취급하면 public path에서 신뢰 채널을 빠뜨리는 실수가 생긴다.

**CORS는 Gateway의 책임.** 개별 서비스가 CORS를 처리하면 서비스 수만큼 설정이 분산된다. Gateway가 진입점이라면 CORS도 Gateway에서 처리해야 한다. 현재 구현은 BE에서 처리하고 있어 기술 부채로 남아있다.

**Spring Security 필터 체인은 내부 포워딩에도 적용된다.** `sendError()`는 요청을 종료하지 않고 `/error`로 포워딩한다. 이 포워딩도 필터 체인을 통과하므로 `/error` 경로의 보안 설정이 에러 응답의 정확성에 직접 영향을 준다.

**OpenAPI 명세의 servers URL이 실제 호출 주소를 결정한다.** Gateway를 도입했다면 Swagger의 servers URL도 Gateway를 가리켜야 실제 인증 흐름을 테스트할 수 있다.