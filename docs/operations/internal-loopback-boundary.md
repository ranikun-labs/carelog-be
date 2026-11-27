# 내부 엔드포인트 loopback 신뢰 경계

대상: enrollment `PUT /internal/v1/identity/carelog/enrollments/**`, claims `GET /internal/identity/claims/*`.
구현: `carelog-be/src/main/java/carelog/carelog/common/config/InternalLoopbackOnlyFilter.java`.

## 판정 방식
- 허용 조건은 `HttpServletRequest.getRemoteAddr()`(소켓 피어)가 `127.0.0.1`, `::1`인 경우뿐이다.
- `X-Forwarded-For`, `X-Real-IP` 등 헤더는 판정에 쓰지 않는다.
- 거부는 본문 없는 403이며 WARN 로그(path, peer)를 남긴다.

## 신뢰 경계 (중요)
- 같은 ECS task의 모든 컨테이너는 network namespace를 공유한다. 따라서 `127.0.0.1` 피어는 **같은 task 안의 어떤 컨테이너든** 될 수 있다.
- 이 경계는 "8443 TLS sidecar 한 개만"이 아니라 "task netns 구성원 전체를 신뢰한다"는 뜻이다. 같은 task에 신뢰할 수 없는 컨테이너를 두면 이 보장은 깨진다.
- VPC 측 `8080` 직접 접근은 피어가 VPC IP이므로 차단된다. 단 같은 task 내부의 다른 컨테이너가 `127.0.0.1:8080`으로 접근하는 것은 막지 못한다.
- 따라서 task 구성 변경(사이드카 추가 등) 시 이 문서의 신뢰 가정을 다시 검토한다.

## 전제 조건 (기동 시 강제)
loopback 플래그가 켜져 있으면 다음이 모두 충족되지 않을 때 기동이 실패한다.
- `server.forward-headers-strategy`는 명시적으로 `none` (`application.yml`에 설정됨).
- 감지된 클라우드 플랫폼이 forward 헤더를 신뢰하지 않음.
- `server.tomcat.remoteip.*` 값 없음.

이 조건이 깨지면 `remoteAddr`가 헤더 값으로 바뀌어 loopback 판정이 스푸핑될 수 있다.

## 플래그
| 대상 | 프로퍼티 | 환경변수 | 기본값 |
|---|---|---|---|
| enrollment PUT | `carelog.internal.loopback-only.enabled` | `CARELOG_INTERNAL_LOOPBACK_ONLY_ENABLED` | `false` |
| claims GET | `carelog.internal.claims-loopback-only.enabled` | `CARELOG_INTERNAL_CLAIMS_LOOPBACK_ONLY_ENABLED` | `false` |

## 롤백 window 절차
1. 구 Core(rev:3)는 8080 http로 claims를 호출한다. 이 window 동안 `CARELOG_INTERNAL_CLAIMS_LOOPBACK_ONLY_ENABLED`는 `false`로 둔다.
2. enrollment 쓰기는 `CARELOG_INTERNAL_LOOPBACK_ONLY_ENABLED=true`로 즉시 loopback 전용으로 둘 수 있다.
3. 구 Core가 완전히 내려간 뒤에만 claims 플래그를 `true`로 올린다.

검증: `EnrollmentRollbackWindowIntegrationTest`가 이 조합(enrollment ON, claims OFF)에서 VPC enrollment 403, VPC claims 도달(404), loopback enrollment 201을 고정한다.
