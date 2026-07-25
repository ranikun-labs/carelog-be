# SCG(Spring Cloud Gateway) 도입 트러블슈팅

## 배경

carelog 서비스는 온프레미스 BE(Spring Boot, 8080)와 EC2 Gateway(Spring Cloud Gateway, 9000)를 Gradle 멀티모듈 모노레포로 구성했다.
Gateway가 JWT 검증 + Redis Blacklist 체크 + 라우팅을 담당하고, BE는 Gateway가 주입한 헤더로 인증하는 구조다.

```
클라이언트 → SCG(9000) → JWT 검증 → X-Gateway-Secret 주입 → BE(8080)
                       → Redis Blacklist 체크
```

보안 설계의 핵심은 두 가지다.
- **헤더 스푸핑 방어**: 외부에서 X-User-Id 등 헤더를 위조해 BE에 직접 요청하는 것을 차단
- **내부 신뢰 표시**: Gateway만이 X-Gateway-Secret을 붙여서 보낼 수 있음. BE는 이 시크릿이 없으면 403 반환

---

## 1. `@Value`로 YAML 리스트 주입 불가

### 🆘 문제 요약

`JwtGlobalFilter`에서 `@Value("${gateway.public-paths}")`로 `List<String>` 주입 시 애플리케이션 기동 실패.

```
Could not resolve placeholder 'gateway.public-paths' in value "${gateway.public-paths}"
```

### 📅 발생 시점

Gateway 모듈 최초 기동 시

### 🧩 원인 분석

Spring의 `@Value`는 단일 scalar 값을 placeholder로 치환하는 메커니즘이다. YAML 시퀀스(리스트)는 내부적으로 인덱싱된 프로퍼티(`gateway.public-paths[0]`, `gateway.public-paths[1]`)로 바인딩되기 때문에, `gateway.public-paths`라는 키 자체는 scalar로 존재하지 않는다.

즉, `PropertySourcesPropertyResolver`가 `gateway.public-paths`를 단일 값으로 조회하지만 해당 키가 없어 placeholder를 resolve하지 못한다.

```yaml
# 이 구조에서 gateway.public-paths 키 자체는 존재하지 않음
gateway:
  public-paths:
    - /api/v1/auth/login   # gateway.public-paths[0]
    - /api/v1/auth/refresh # gateway.public-paths[1]
```

### 🛠 해결 방법

`@Value` 대신 `@ConfigurationProperties`를 사용한다. `@ConfigurationProperties`는 prefix 하위의 모든 프로퍼티를 타입 안전하게 바인딩하며, YAML 시퀀스를 `List<String>`으로 자동 변환한다.

```kotlin
// Before
@Component
class JwtGlobalFilter(
    @param:Value("\${gateway.public-paths}")
    private val publicPaths: List<String>,
    @param:Value("\${gateway.internal-secret}")
    private val internalSecret: String
)

// After - GatewayConfig.kt
@ConfigurationProperties(prefix = "gateway")
data class GatewayConfig(
    val publicPaths: List<String> = emptyList(),
    val internalSecret: String = ""
)

// CarelogGatewayApplication.kt
@SpringBootApplication
@EnableConfigurationProperties(GatewayConfig::class)
class CarelogGatewayApplication

// JwtGlobalFilter.kt
@Component
class JwtGlobalFilter(
    private val gatewayConfig: GatewayConfig
) : GlobalFilter, Ordered {
    private val publicPaths get() = gatewayConfig.publicPaths
    private val internalSecret get() = gatewayConfig.internalSecret
}
```

### 🔄 Before vs After

| | Before | After |
|---|---|---|
| 바인딩 방식 | `@Value` placeholder | `@ConfigurationProperties` |
| YAML 리스트 지원 | ✗ | ✅ |
| 타입 안정성 | 런타임 오류 | 컴파일 타임 바인딩 |
| 설정 응집도 | 파라미터별 분산 | `GatewayConfig` 단일 객체 |

### 📈 개선 효과

- 설정 관련 프로퍼티가 `GatewayConfig` 한 곳에 응집되어 추후 설정 항목 추가 시 변경 범위가 명확해짐
- 컴파일 타임에 바인딩 오류를 잡을 수 있어 런타임 기동 실패 가능성 감소

### 💬 회고

`@Value`와 `@ConfigurationProperties`의 차이를 단순히 "복잡도"의 문제로 보는 시각이 많은데, 실제로는 **바인딩 대상의 성격**에 따라 선택해야 한다. 단일 scalar 값은 `@Value`, 구조화된 설정 그룹은 `@ConfigurationProperties`가 적합하다. 특히 MSA에서 Gateway 설정은 확장 가능성이 높으므로 처음부터 `@ConfigurationProperties`로 설계하는 것이 맞다.

---

## 2. SCG public path에 X-Gateway-Secret 미포함

### 🆘 문제 요약

로그인, 회원가입 등 인증 불필요 경로(public path)로 요청 시 BE에서 403 Forbidden 반환.

### 📅 발생 시점

Gateway 기동 후 Swagger에서 `/api/v1/auth/login` 호출 시

### 🧩 원인 분석

보안 설계의 핵심 개념을 혼동한 것이 원인이다.

- `X-Gateway-Secret`: **Gateway → BE 신뢰 표시** (모든 요청에 필요)
- JWT 검증: **사용자 인증** (public path는 불필요)

이 둘은 독립적인 관심사인데, public path 처리 로직에서 X-Gateway-Secret 주입을 누락했다.

```kotlin
// Before - public path 처리 시 sanitizedExchange 그대로 전달
// sanitizedExchange는 X-Gateway-Secret이 제거된 상태
if (publicPaths.any { path.startsWith(it) }) {
    return chain.filter(sanitizedExchange) // X-Gateway-Secret 없음
}
```

BE의 `GatewayHeaderAuthFilter`는 모든 요청에서 X-Gateway-Secret을 검증하므로, 시크릿 없이 들어온 요청은 public path 여부와 무관하게 403을 반환한다.

```
클라이언트 → SCG → public path 판단 → X-Gateway-Secret 제거된 채 BE 전달
                                                                    ↓
                                                    GatewayHeaderAuthFilter: 시크릿 없음 → 403
```

### 🛠 해결 방법

public path도 X-Gateway-Secret은 포함해서 BE로 전달한다. JWT 검증만 건너뛰고, 신뢰 표시는 유지한다.

```kotlin
// After
if (publicPaths.any { path.startsWith(it) }) {
    val publicExchange = sanitizedExchange.mutate()
        .request { it.headers { headers ->
            headers.set("X-Gateway-Secret", internalSecret)
        }}
        .build()
    return chain.filter(publicExchange)
}
```

### 🔄 Before vs After

```
Before:
public path → sanitizedExchange(시크릿 없음) → BE → 403

After:
public path → sanitizedExchange + X-Gateway-Secret 추가 → BE → 정상 처리
             (단, X-User-Id 등 사용자 헤더는 없음 → SecurityContext 미설정 → anonymous)
```

### 📈 개선 효과

- public path도 반드시 Gateway를 통해서만 BE에 도달할 수 있음이 보장됨
- BE를 직접 호출하는 경우 public path도 403으로 차단 → 내부망 노출 방어

### 💬 회고

이 문제의 핵심은 **"인증(Authentication)"과 "신뢰(Trust)"를 구분하는 것**이다. X-Gateway-Secret은 "이 요청이 Gateway를 통해 왔는가"를 묻는 신뢰 레이어이고, JWT는 "이 사용자가 누구인가"를 묻는 인증 레이어다. public path는 인증을 면제하는 것이지, 신뢰 검증을 면제하는 것이 아니다.

MSA 보안 설계에서 이 두 레이어를 혼동하면 내부망을 직접 공격하는 경로가 열릴 수 있다.

---

## 3. OPTIONS preflight CORS 차단

### 🆘 문제 요약

Swagger UI(9000)에서 API 호출 시 실제 요청 전에 OPTIONS 요청이 403으로 차단되어 모든 API 호출 실패.

```
Request URL: http://localhost:8080/api/v1/users/managers
Request Method: OPTIONS
Status Code: 403 Forbidden
```

### 📅 발생 시점

Gateway(9000)를 통해 Swagger UI 접속 후 API 호출 시

### 🧩 원인 분석

두 가지 원인이 복합적으로 작용했다.

**원인 1 — CORS preflight 메커니즘**

브라우저는 Cross-Origin 요청(출처가 다른 요청) 전에 OPTIONS 메서드로 preflight 요청을 먼저 보낸다. Swagger UI가 `localhost:9000`에서 열렸고 API는 `localhost:8080`으로 호출하므로 포트가 달라 Cross-Origin으로 판단된다.

```
브라우저 → OPTIONS localhost:8080  (preflight)
         → POST   localhost:8080  (실제 요청, preflight 성공 시)
```

**원인 2 — GatewayHeaderAuthFilter가 OPTIONS 차단**

`GatewayHeaderAuthFilter`는 모든 HTTP 메서드에 대해 X-Gateway-Secret을 검증한다. OPTIONS 요청에는 X-Gateway-Secret이 없으므로 403을 반환했다.

또한 403 반환 시 Spring Boot가 `/error` 엔드포인트로 포워딩하는데, `/error`도 Spring Security에서 인증 필요 경로로 설정되어 있어 401이 반환되는 연쇄 문제가 발생했다.

```
OPTIONS → GatewayHeaderAuthFilter → 403
        → Spring Boot /error 포워딩
        → Spring Security → 인증 없음 → 401
```

### 🛠 해결 방법

두 가지를 함께 적용했다.

**1. OPTIONS 요청을 GatewayHeaderAuthFilter에서 제외**

```java
// GatewayHeaderAuthFilter.java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    return "OPTIONS".equalsIgnoreCase(request.getMethod());
}
```

**2. Spring Security에 CORS 설정 추가**

```java
// SecurityConfig.java
http.cors(cors -> cors.configurationSource(corsConfigurationSource()))

@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

**3. `/error` 경로 PUBLIC_URLS에 추가**

```java
private static final String[] PUBLIC_URLS = {
    "/auth/login", "/auth/refresh", "/users/managers",
    "/swagger-ui/**", "/v3/api-docs/**", "/api/v1",
    "/error"  // 추가
};
```

### 🔄 Before vs After

```
Before:
OPTIONS → GatewayHeaderAuthFilter(403) → /error → Spring Security(401)

After:
OPTIONS → shouldNotFilter = true → CORS 설정으로 200 응답
POST    → GatewayHeaderAuthFilter(정상) → 실제 처리
```

### 📈 개선 효과

- CORS preflight가 정상 처리되어 Swagger 및 프론트엔드에서 API 호출 가능
- `/error` 경로도 허용되어 에러 응답이 클라이언트에 정확히 전달됨

### 💬 회고

MSA에서 CORS는 **Gateway에서 중앙 처리**하는 것이 원칙이다. 개별 서비스가 CORS를 처리하면 서비스가 늘어날수록 설정이 분산된다. 현재 구현은 BE에서 CORS를 처리하고 있는데, 운영 환경에서는 Gateway의 `spring.cloud.gateway.globalcors` 설정으로 이관하는 것이 바람직하다.

또한 `shouldNotFilter`로 OPTIONS를 통과시키는 것은 임시방편이다. 근본 해결책은 CORS를 Gateway에서 처리해 BE까지 OPTIONS가 내려오지 않게 하는 것이다.

---

## 4. Swagger 서버 URL이 BE 직접 주소(8080)로 고정

### 🆘 문제 요약

Swagger UI에서 API 호출 시 Gateway(9000)가 아닌 BE(8080)를 직접 호출해 X-Gateway-Secret 없이 요청이 전달되어 403 반환.

```
Request URL: http://localhost:8080/api/v1/users/managers  ← Gateway 우회
```

### 📅 발생 시점

Gateway(9000)를 통해 Swagger UI 접속 후 API 호출 시

### 🧩 원인 분석

springdoc은 OpenAPI 명세를 생성할 때 `servers` 항목에 자동으로 애플리케이션이 실행 중인 서버 주소를 넣는다. BE는 8080에서 실행 중이므로 `http://localhost:8080/api/v1`이 기본 서버 URL로 생성된다.

Swagger UI는 이 servers 항목을 보고 API를 호출할 주소를 결정한다. Gateway를 통해 Swagger UI에 접근했더라도, API 호출은 OpenAPI 명세에 적힌 `localhost:8080`으로 직접 날아간다.

```
브라우저 → localhost:9000/api/v1/swagger-ui (Swagger UI 로드)
         → localhost:8080/api/v1/users/managers (API 호출, Gateway 우회!)
```

### 🛠 해결 방법

`SwaggerConfig`의 `OpenAPI` 빈에서 servers를 명시적으로 Gateway 주소로 고정한다.

```java
// Before - servers 항목 없음 → springdoc이 8080으로 자동 생성
return new OpenAPI()
    .info(info)
    .addSecurityItem(securityRequirement)
    .components(components);

// After - Gateway 주소로 고정
return new OpenAPI()
    .info(info)
    .servers(List.of(
        new Server().url("http://localhost:9000/api/v1").description("Local Gateway")
    ))
    .addSecurityItem(securityRequirement)
    .components(components);
```

### 🔄 Before vs After

| | Before | After |
|---|---|---|
| Swagger 서버 URL | `http://localhost:8080/api/v1` (자동 생성) | `http://localhost:9000/api/v1` (명시) |
| API 호출 경로 | BE 직접 → 403 | Gateway 경유 → 정상 |

### 📈 개선 효과

- Swagger에서 실제 운영 요청 흐름(Gateway → BE)과 동일하게 테스트 가능
- Gateway의 JWT 검증, Blacklist 체크가 Swagger 테스트에서도 동작

### 💬 회고

이 문제는 BE의 context-path(`/api/v1`)와 Gateway URL의 관계를 정확히 이해해야 한다.

springdoc이 생성하는 API 경로는 context-path 기준 상대 경로다(`/users/managers`). servers의 url과 조합해 최종 호출 URL이 결정된다. 따라서 서버 URL에 `/api/v1`을 포함시켜야 `http://localhost:9000/api/v1/users/managers`로 올바르게 조합된다.

운영 환경에서는 이 서버 URL을 환경변수나 Spring profile로 분리해 하드코딩을 피해야 한다.

```yaml
# application-prod.yml
swagger:
  gateway-url: https://api.carelog.com
```

---

## 5. Spring Security `/error` 경로 미허용으로 인한 에러 응답 왜곡

### 🆘 문제 요약

인증 실패 시 클라이언트에 403이 아닌 401이 반환되고, 에러 메시지가 실제 원인과 다르게 표시됨.

```
인증 실패: /api/v1/error - Full authentication is required to access this resource
```

### 📅 발생 시점

X-Gateway-Secret 없이 BE에 직접 요청 시, 또는 인증이 필요한 경로에 미인증 접근 시

### 🧩 원인 분석

Spring Boot는 서블릿 필터나 핸들러에서 `sendError()`가 호출되면 내부적으로 `/error` 엔드포인트로 포워딩해 에러 응답을 구성한다.

문제는 이 포워딩 요청도 Spring Security 필터 체인을 통과한다는 점이다. `/error`가 `PUBLIC_URLS`에 없으므로 Spring Security는 인증이 없다고 판단해 `JwtAuthenticationEntryPoint`를 호출, 401 응답을 반환한다.

```
GatewayHeaderAuthFilter.sendError(403)
    → Spring Boot 내부 포워딩 → GET /error
    → Spring Security 필터 체인
    → /error는 인증 필요 경로
    → JwtAuthenticationEntryPoint → 401 반환
```

결과적으로 실제 원인은 403(X-Gateway-Secret 없음)인데 클라이언트는 401("인증 필요")을 받아 디버깅이 어려워진다.

### 🛠 해결 방법

`/error`를 `PUBLIC_URLS`에 추가한다.

```java
private static final String[] PUBLIC_URLS = {
    "/auth/login",
    "/auth/refresh",
    "/users/managers",
    "/swagger-ui/**",
    "/v3/api-docs/**",
    "/api/v1",
    "/error"  // 추가
};
```

### 🔄 Before vs After

```
Before:
실제 원인: 403 (X-Gateway-Secret 없음)
클라이언트 수신: 401 (인증 필요) ← 원인 왜곡

After:
실제 원인: 403
클라이언트 수신: 403 ← 정확한 에러 전달
```

### 📈 개선 효과

- 에러 원인이 클라이언트에 정확하게 전달되어 디버깅 시간 단축
- 로그에서도 실제 원인과 수신 상태가 일치해 모니터링 정확도 향상

### 💬 회고

Spring Security의 필터 체인이 포워딩 요청에도 적용된다는 점을 놓치기 쉽다. `sendError()`가 실제로는 요청을 종료하지 않고 새로운 내부 요청을 만든다는 서블릿 스펙을 이해하면 왜 이런 일이 발생하는지 명확해진다.

`/error`를 public으로 열면 에러 응답 자체에 민감한 정보가 노출될 수 있으므로, `BasicErrorController`가 반환하는 에러 응답에 스택트레이스나 내부 경로 등이 포함되지 않도록 `server.error.include-stacktrace=never` 설정을 함께 적용하는 것이 좋다.
