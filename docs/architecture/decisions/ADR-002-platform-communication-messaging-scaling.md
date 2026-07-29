# ADR-002: Platform Communication·Messaging·Scaling 기준

| 항목 | 내용 |
| --- | --- |
| 상태 | Accepted |
| 결정일 | 2026-07-29 |
| 결정 범위 | Product·Shared Platform 간 통신, 메시징, 동기 호출, 확장 기술 도입 기준 |
| Repository | `care-log/carelog-be` |
| 관련 Jira | `RPL-14` |
| 관련 결정 | [ADR-001](ADR-001-carelog-runtime-deployment-profiles.md) |

## Context

[ADR-001](ADR-001-carelog-runtime-deployment-profiles.md)은 Carelog MVP의 현재
Runtime과 Deployment Profile을 소유한다. 이 ADR은 그 배치를 다시 설계하지 않고,
논리적으로 분리되는 Product와 Shared Platform이 어떤 통신 방식을 기본값으로
사용하고 메시징·RPC·Orchestration 기술을 언제 도입할지를 결정한다.

현재 실제 Runtime은 다음과 같다.

```text
Cloudflare Tunnel
→ Spring Cloud Gateway
→ carelog-be
   ├─ Auth/OAuth Module
   └─ Carelog Core
```

현재 독립 Runtime은 Spring Cloud Gateway와 `carelog-be`뿐이다. Shared Identity,
Shared AI, Finance Harness Backend, Dev Harness Backend, NATS JetStream, Kafka,
Kubernetes는 현재 배포된 Runtime이 아니다. 초기 Physical Host는 Mac mini M4 한
대이며, 같은 Host에 배치하는 것은 논리적 Service 분리와 모순되지 않는다.

Gateway는 다섯 Product·Shared Platform Service에 포함하지 않는 공통
Ingress/Security Boundary다. 외부 요청의 인증 Context를 검증·정규화하지만,
내부 Service-to-Service 호출을 중계하는 범용 Service Proxy가 아니다.

## Decision

### 기술 기본값

| 목적 | 기본 기술 | 현재 상태 |
| --- | --- | --- |
| 외부 API | HTTP/JSON | 현재 기본값 |
| 내부 동기 통신 | HTTP/JSON | Service 추출 시 기본값 |
| AI Token Streaming | SSE | Streaming 요구가 생길 때 기본값 |
| 비동기 Event·Job | 실제 요구 발생 시 NATS JetStream | 미도입 |
| Business Source of Truth | PostgreSQL | 현재 사용 |
| Session·Cache·단기 상태 | Redis | 현재 사용 |
| 고빈도 RPC | 병목 입증 후 일부 gRPC | 보류 |
| CDC·장기 Replay | 요구 발생 후 Kafka | 보류 |
| Orchestration | 다중 Node·Replica·운영 병목 후 Kubernetes | 보류 |

서비스 수가 늘었다는 사실만으로 NATS, gRPC, Kafka 또는 Kubernetes를 도입하지
않는다.

### 외부와 내부 호출 경로

외부 Client는 Gateway를 통해서만 공개 API에 접근한다.

```text
Client → Gateway → Product 또는 Shared Identity 공개 Endpoint
```

내부 Service 호출은 Gateway를 경유하지 않고 private network에서 직접 수행한다.

```text
Carelog → Direct HTTP → Shared AI
```

다음 구조는 금지한다.

```text
Carelog → Gateway → Shared AI
```

Gateway는 외부 Ingress와 Security Boundary이므로 내부 호출 경로에 넣어 장애 지점,
인증 의미, Rate Limit 책임을 불필요하게 결합하지 않는다. 내부 호출의 Service
Authentication과 Authorization 방식은 실제 Service 추출 전에 별도 계약으로
확정한다.

### Shared Identity 요청 흐름

로그인, Refresh, 계정 관리처럼 Identity가 직접 소유하는 요청만 Gateway를 통해
Shared Identity로 보낸다.

```text
Client → Gateway → Shared Identity
```

현재 Gateway는 JWT 서명과 Blacklist를 검증하고 기존 Claims 기반 인증 Context를
Product에 전달한다. issuer/audience 계약이 설계·구현된 이후의 Target Flow에서는
Gateway가 JWT 서명·issuer·audience·Blacklist를 검증한 뒤 신뢰 인증 Context를
전달한다. Product는 어느 흐름에서도 자체 업무 인가를 수행한다.

```text
Client → Gateway JWT/Blacklist 검증
       → 신뢰 인증 Context
       → Product 자체 인가
```

Product는 일반 요청마다 Shared Identity를 동기 호출하지 않는다. Product에서
Identity로 향하는 직접 동기 호출은 다음의 제한된 Management Use Case로 한정한다.

- 계정 상세
- Provider Linking
- 계정 정지·탈퇴
- 관리자 계정 처리
- 특수 Introspection

아키텍처 관계도에서 `Product → Shared Identity` 화살표를 사용할 때는
`limited management call`이라고 표시한다. 계정 상태 전파처럼 요청 경로에 필수적이지
않은 정합성은 실제 요구가 생기면 Event·Projection으로 분리한다.

### Shared AI 책임 경계

Shared AI는 제품 중립적인 AI 실행 메커니즘을 소유한다.

- Provider Adapter
- API Key와 Secret
- Model Alias
- Timeout
- 멱등성과 오류 특성을 고려한 제한 Retry
- Rate Limit
- Usage와 Cost
- 공통 Observability
- Provider 장애 처리
- 기술적 Safety

각 Product Service는 제품 의미와 최종 판단을 소유한다.

- 제품별 System Prompt
- Domain Context와 Context 선택
- Finance Harness의 투자자문 제한
- Carelog 업무 정책
- Dev Harness Approval
- Product Tool
- 결과 검증
- 저장과 업무 반영

Shared AI는 제품별 Prompt, Workflow, Domain Policy를 독점하지 않는다. 공통
Observability와 기술적 Safety가 Product의 업무 정책 또는 결과 책임을 대체하지
않는다.

Provider-neutral RAG Adapter, Embedding Runtime, Vector Infrastructure, 공통 AI Job
Runtime과 공통 실행 Orchestration은 Shared AI의 제품 중립 기술 Capability 후보이며
현재 구현된 Runtime이 아니다. Product는 Corpus, Retrieval Policy, Domain Context,
Domain Validation과 결과 저장·업무 반영을 소유한다. 구체적 소유권과 도입 범위는 실제
Use Case 발생 후 후속 Decision에서 확정한다.

### 동기 호출 불변조건

- 사용자 요청 하나의 필수 동기 Downstream은 가급적 하나 이하로 유지한다.
- Identity와 Commerce를 일반 Product 요청마다 호출하지 않는다.
- 긴 필수 동기 호출 Chain을 만들지 않는다.
- 모든 내부 동기 호출에 Timeout을 둔다.
- Retry는 멱등 요청에만 제한하며 무제한·중첩 Retry를 금지한다.
- Circuit Breaker와 Product-facing Error Mapping은 호출 Product의 Adapter가
  책임진다.
- Correlation ID를 Downstream과 응답·Log 경계에 전달한다.

동기 Downstream이 둘 이상 필수가 되는 Use Case는 API Composition, 읽기 Projection,
비동기 처리 중 어느 방식으로 Chain을 줄일지 설계 검토를 거친다.

### NATS JetStream 도입 기준

NATS JetStream은 현재 Runtime이 아니다. 첫 명확한 비동기 Use Case가 생긴 뒤
도입하며, 단순한 미래 가능성만으로 설치하거나 운영하지 않는다.

첫 후보는 다음과 같다.

- 장시간 AI Job
- AI Usage/Cost 비동기 기록
- Audit Consumer
- 계정 상태 전파
- 알림
- Entitlement Projection

도입할 때는 NATS Core가 아니라 JetStream을 사용하고 다음 계약을 적용한다.

- Delivery는 at-least-once를 기본으로 한다.
- Consumer는 idempotent해야 한다.
- 처리 기록에는 `UNIQUE(event_id)`와 동등한 중복 방지 제약을 둔다.
- Consumer의 DB Commit이 성공한 뒤 ACK한다.
- MQ를 Business Source of Truth로 사용하지 않는다.
- 유실되면 안 되는 Event에만 Transactional Outbox를 적용한다.
- 모든 Event에 Transactional Outbox를 강제하지 않는다.

Event Envelope, Subject Naming, Retention, Dead Letter 처리, Reconciliation, Publisher
Failure, 첫 Producer/Consumer, Backup·Restore 정책은 첫 Use Case와 함께 후속
결정한다. 이 ADR은 실제 NATS 설정, Docker Compose 또는 Production 코드를 추가하지
않는다.

### gRPC 도입 Trigger

내부 HTTP/JSON을 먼저 사용한다. 다음 중 하나 이상이 측정과 Profiling으로 확인되고
HTTP/JSON 최적화만으로 목표를 충족하지 못할 때 일부 경로에 gRPC를 검토한다.

- 지속적인 고빈도 내부 호출
- JSON 직렬화 CPU 병목
- 큰 Binary Payload
- 양방향 Streaming
- 다중 언어 Proto SDK 필요
- 내부 p99 수 ms 단위 최적화 필요

도입 시에도 전체 Service를 일괄 전환하지 않고 입증된 병목 경로만 대상으로 한다.

### Kafka 도입 Trigger

다음 요구와 이를 운영할 물리 Node·인력 역량이 함께 확보될 때 Kafka를 검토한다.

- CDC
- 장기 Event Log
- 대규모 Replay
- 다수 분석 Consumer
- Kafka Connect 또는 Kafka Streams
- 물리 Node와 운영 역량 확보

NATS JetStream 후보 Use Case만 존재하거나 서비스 수가 늘었다는 이유로 Kafka를
도입하지 않는다.

### Kubernetes 도입 Trigger

다음 운영 요구가 나타나고 Compose 기반 운영이 실제 병목이 된 뒤 Kubernetes를
검토한다.

- 여러 물리 Node
- Service Replica
- 자동 수평 확장
- 무중단 배포
- 다수 운영자
- Compose 운영이 실제 병목

Mac mini M4 한 대와 낮은 SLA의 초기 운영에서는 Kubernetes가 기본값이 아니다.

## Consequences

- HTTP/JSON과 SSE로 초기 계약을 단순하게 유지한다.
- Gateway가 외부 보안 경계라는 기존 결정을 보존하면서 내부 호출의 불필요한 우회를
  막는다.
- 일반 Product 요청이 Shared Identity 가용성에 매번 결합되지 않는다.
- Shared AI의 공통 실행 책임과 Product의 Domain Policy 책임이 분리된다.
- 비동기 요구가 확인되기 전 MQ 운영 비용을 부담하지 않는다.
- gRPC, Kafka, Kubernetes는 측정된 Trigger와 운영 역량을 근거로 별도 승인해야 한다.

반면 첫 NATS Use Case가 생기면 Delivery, Idempotency, Outbox, Reconciliation을
구체화하는 후속 ADR과 운영 준비가 필요하다. 내부 Service Authentication,
Service Discovery, SSE의 Shared AI → Product → Gateway → Client Target Flow와
재연결·Last-Event-ID, 사용자 취소, Stream Timeout, Backpressure, 중간 장애, 결과
저장과 Stream 종료 정합성은 실제 Streaming 작업의 후속 Decision으로 남는다.

## Non-goals

- 현재 Runtime 또는 Deployment Profile 변경
- NATS, gRPC, Kafka, Kubernetes 설치·구현
- Production 코드, Runtime 설정, Test, Migration 변경
- Product별 Prompt·Workflow·Domain Policy의 Shared AI 이전
- Shared Identity의 매 요청 Introspection
