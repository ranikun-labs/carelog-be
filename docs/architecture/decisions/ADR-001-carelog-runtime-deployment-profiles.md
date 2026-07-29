# ADR-001: Carelog MVP Runtime·Deployment Profile

| 항목 | 내용 |
| --- | --- |
| 상태 | Accepted — Profile 1만 현재 승인 |
| 결정일 | 2026-07-28 |
| 결정 범위 | Carelog MVP의 공개 진입점, Runtime·Data 배치, Portfolio Service Boundary와 추출 기준 |
| Repository | `care-log/carelog-be` |
| 관련 PR | #34 `feat/kakao-oauth-gateway-publication` (Draft, 구현 변경 없음) |
| 관련 통신 결정 | [ADR-002](ADR-002-platform-communication-messaging-scaling.md) |
| 승인 범위 | Development, Local Live E2E, Pilot, Low-SLA Initial Operation |
| 재검토 조건 | Hybrid/AWS Trigger, 실제 SLA·RPO/RTO 합의, Cloudflare Header chain 검증, 검증된 다제품 소비 |

## Context

Carelog는 1인 개발·운영의 초기 MVP다. 서버비와 운영 지점을 최소화하면서 Kakao OAuth, Gateway JWT 검증·Header 정규화·Rate Limit, PostgreSQL 및 향후 pgvector, GPT API 기반 AI 기능을 준비한다. 현재 목표는 HA나 높은 SLA가 아니다.

Portfolio의 상위 경계는 Active Product인 Carelog, Stabilization/Maintenance 상태의 Dev Harness, Foundation/Planned Build인 Finance Harness, Supporting Platform인 Shared Identity, Architecture/Planned인 Shared AI다. 논리 책임 분리, 독립 Deployment Unit, Physical Host는 서로 다른 결정이다.

현재 Runtime 계약은 Gateway와 `carelog-be`가 Redis를 사용한다. Gateway OAuth Rate Limit, OAuth state/PKCE verifier, 로그아웃 Token blacklist는 서로 다른 책임이며 `blacklist:*`는 Backend가 쓰고 Gateway가 읽는 공유 계약이다.

Foundation의 미래 Draft와 Portfolio Target은 현재 배포 사실을 뜻하지 않는다. 이 ADR은 현재 사실, Near-term 승인 대상, 장기 논리 경계를 명시적으로 구분한다.

## Portfolio Service Topology

| 구분 | Service Boundary | 소유 책임 | 현재 운영 모드 |
| --- | --- | --- | --- |
| Product | Carelog Core | 고객·관계·기록·Follow-up·Handoff·제품 정책 | Active Product |
| Product | Dev Harness Backend / Control Plane | Multi-AI 작업 제어·세션·승인 실행 | Stabilization / Maintenance; Backend는 Target |
| Product | Finance Harness Backend | 금융 질문·Checklist·Journal·Review·금융 정책 | Foundation / Planned Build |
| Shared Platform | Shared Identity | Account·OAuth·Token 경계 | Supporting Platform; 현재는 Carelog 내부 모듈 |
| Shared Platform | Shared AI | Model 호출·공통 안전·비용 경계 | Architecture / Planned |

Spring Cloud Gateway는 위 다섯 Product·Platform Service에 포함하지 않는 공통 Ingress / Security Boundary다.

## Decision

### Profile 1 — Mac mini Baseline (현재 선택)

**Approved for Development, Local Live E2E, Pilot and Low-SLA Initial Operation**

```text
사용자
→ Cloudflare Edge
→ Cloudflare Tunnel
→ Mac mini M4의 cloudflared
→ Spring Cloud Gateway
→ carelog-be
   ├─ Auth/OAuth Module
   └─ Carelog CRM Core
→ Redis 1개
→ PostgreSQL 1개 (pgvector는 향후 계획)
```

- EC2, RDS, ElastiCache, ALB/NLB, Nginx, Tailscale/WireGuard, 공유기 Port Forwarding을 사용하지 않는다.
- 현재 독립 Deployment Unit은 Spring Cloud Gateway와 `carelog-be`다. Auth/OAuth는 `carelog-be` 내부 Module이며 독립 Auth Service가 아직 운영 중인 것은 아니다.
- 독립 AI Runtime, GPT API 연동, pgvector Extension/Migration은 현재 구현 완료 상태가 아니다.
- Backend, Redis, PostgreSQL은 인터넷에 직접 노출하지 않는다. 이 Profile은 HA 또는 높은 SLA 설계가 아니다.

### Profile 2 — Hybrid Edge Improvement (Deferred)

```text
사용자 → Cloudflare Tunnel → EC2
                           ├─ cloudflared
                           ├─ Spring Cloud Gateway
                           └─ Gateway Redis
→ Tailscale 또는 WireGuard → Mac mini
                           ├─ Application Runtime
                           ├─ Backend Redis
                           └─ PostgreSQL
```

Gateway 공격 트래픽 격리, 여러 Backend의 공통 Gateway, 안정적인 Edge Host, Mac 재시작과 공개 진입점 장애 분리, EC2·VPN·Redis 이중 운영을 감당할 예산과 역량이 필요할 때만 도입한다.

### Profile 3 — AWS Migration Target (Document Only)

```text
사용자 → Cloudflare Tunnel → 단일 EC2
                           ├─ cloudflared
                           ├─ Spring Cloud Gateway
                           ├─ Application Runtime
                           └─ Redis
→ Private Single-AZ RDS PostgreSQL (pgvector는 후속 결정)
```

1차 이전 목표일 뿐 현재 구축하지 않는다. Managed Redis/Valkey, RDS Multi-AZ, 다중 Instance, Tunnel replica 또는 Load Balancer, 중앙 Monitoring·Deployment Automation은 이후 후보다.

AWS 재검토 Trigger는 Mac 전원·ISP·디스크 장애 반복, 합의 RPO/RTO 미달성, 중요 데이터·SLA 증가, PostgreSQL 부하, 단일 Mac 자원 한계, 원격 장애 복구 곤란, 다중 Instance 필요다.

## Current Deployable State

현재 Physical Host는 Mac mini M4 한 대다. 같은 Host에 실행된다는 사실은 같은 Service라는 뜻이 아니다.

| 구분 | 현재 사실 |
| --- | --- |
| Module Boundary | `carelog-be` 내부 Auth/OAuth Module과 Carelog CRM Domain Module |
| Logical Service Boundary | Carelog Core, Dev Harness Backend, Finance Harness Backend, Shared Identity, Shared AI가 목표 경계 |
| Deployment Unit | Spring Cloud Gateway, `carelog-be` |
| Physical Host | Mac mini M4 |

`carelog-be` 내부의 Auth 책임 분리는 진행 중이다. 독립 Shared Identity Service, Dev Harness Spring Boot Backend, Finance Harness Spring Boot Backend, AI Runtime Service는 현재 운영 중인 Runtime으로 표현하지 않는다.

## Near-term Deployment Target

Auth 분리는 Near-term 승인 대상이지만 물리 Process 분리 승인을 뜻하지 않는다. Product Client, Redirect, issuer/audience, Error/Session API, Web/Mobile Session, Schema/Migration 소유권과 내부 의존성 분리를 먼저 완료해야 한다. 이후 같은 Mac mini에서 별도 Process 또는 Container로 실행할 수 있으며, 별도 서버를 뜻하지 않는다.

```text
Mac mini M4
├─ cloudflared
├─ Spring Cloud Gateway
├─ Carelog Auth Service              (Near-term approved)
├─ Carelog Core Service              (Auth 추출 후)
├─ Shared AI Runtime                 (Near-term planned)
├─ Redis 1개
└─ PostgreSQL 1개
```

추출된 Carelog Auth Service는 독립 Build·Deploy·Rollback, Configuration·Secret, 인증 API 계약, Auth Data Migration 경계를 소유한다. Shared AI Runtime은 처음부터 제품 공통 사용을 고려한 독립 Deployment Unit으로 설계하지만, 현재 구현되지는 않았다. 초기 소비자는 Carelog이며 Public Route로 직접 노출하지 않는다.

Dev Harness Backend와 Finance Harness Backend는 각 Product 개발 단계에서 추가한다. Near-term에 다섯 Runtime이 모두 구현된 것으로 보지 않는다.

## Long-term Logical Platform

장기 목표는 Product Service가 Shared Identity 계약과 Shared AI Runtime을 내부 호출로 소비하는 구조다.

```text
일반 Product API:
Client → Spring Cloud Gateway JWT/Blacklist 검증 → 신뢰 인증 Context → Product 자체 인가

Identity 소유 API:
Client → Spring Cloud Gateway → Shared Identity

AI 호출:
Product Service → Direct HTTP → Shared AI → External Model Provider
```

Shared Identity Login/OAuth·Refresh·계정 관리 Endpoint는 Gateway를 통해 공개될 수 있으나 Shared AI는 기본 Public Route로 노출하지 않는다. Product Service는 일반 요청마다 Shared Identity를 호출하지 않고 Gateway의 신뢰 인증 Context로 자체 인가한다. Identity 직접 호출은 계정 상세·Provider Linking·계정 정지/탈퇴·관리자 처리·특수 Introspection 같은 제한된 관리 호출이다. Product Service는 사용자·업무 권한과 제품 데이터 접근, AI 결과 반영 여부를 통제한다.

### Carelog Core

Carelog Core는 고객, 관계, 상담·케어 기록, Timeline, Follow-up, Handoff, 조직·업무 Context, Carelog 제품 정책, AI 실행 요청 권한과 결과 반영 통제를 소유한다. OAuth Provider, Access/Refresh Token, 다른 제품 인증 데이터, 공통 Model Provider 정책과 사용량 정책은 소유하지 않는다.

### Dev Harness Backend / Control Plane

목표 책임은 Multi-AI 작업 제어, Project Context, Session Handoff, Skill·Role Routing, Approval Queue, 승인 기반 실행, 작업 상태와 Closed-loop Work Management다. 현재 Local Core·CLI·문서 구조와 미래 Spring Boot Backend/Cloud Control Plane을 혼동하지 않는다.

### Finance Harness Backend

목표 책임은 금융 질문, 판단 전 Checklist, 투자 Journal, Review·복기, 금융 Product Policy와 데이터다. 현재는 Foundation / Planned Build이며 독립 Backend가 완성된 상태가 아니다.

### Shared Identity

목표 책임은 공통 사용자 식별, Account, Password Login, OAuth Provider·State·PKCE, Access/Refresh Token, ExternalIdentity, Account 상태, Logout·Token Revocation, 제품 중립 인증 API·Event 계약이다.

Carelog Auth Service에서 Shared Identity로 일반화하는 것은 Finance Harness 또는 Dev Harness Cloud가 실제 공통 인증을 사용하고, 둘 이상의 제품이 Account·OAuth·Token 경계를 요구하며, 제품 중립 API·데이터 소유권·Migration 전략이 안정화된 뒤에만 검토한다.

### Shared AI

목표 책임은 GPT API를 포함한 Provider Adapter, API Key·Secret, Model Alias, Timeout, 제한 Retry, Rate Limit, Usage·Cost, 공통 Observability, Provider 장애 처리와 기술적 Safety다. 로컬 LLM 모델 서버를 뜻하지 않는다.

제품별 System Prompt·Workflow·Domain Context·Tool, Carelog 업무 정책, Finance Harness의 투자자문 금지 정책, Dev Harness의 실행 승인 정책, 결과 검증과 저장·반영은 각 Product Service가 소유한다. Shared AI는 제품 업무 판단이나 Product Policy를 독점하지 않는다.

## Redis Decision and Logical Ownership

현재 Physical Redis Instance는 **1개**다. Key 이름은 변경하지 않는다.

| 논리 소유자 | Key / 권한 | 원칙 |
| --- | --- | --- |
| Gateway | `request_rate_limiter.*` 읽기·쓰기, `blacklist:*` 조회 | OAuth Rate Limit과 Token Revocation 조회만 수행 |
| 현재 Auth Module / 향후 Shared Identity | `oauth:state:*` 생성·소비, `blacklist:*` 작성·필요 시 삭제 | PKCE verifier와 Logout Revocation 소유 |
| Carelog Core | Auth Key 직접 접근 금지 | 인증 계약을 통해서만 소비 |
| Dev Harness / Finance Harness | 필요 발생 시 전용 Prefix | Auth Key 직접 접근 금지 |
| Shared AI | 향후 Queue·Job·Usage 전용 Prefix | OAuth·Blacklist·Rate Limit Key 접근 금지 |

Key prefix, ACL User, Command Permission, TTL, Persistence 책임으로 논리 경계를 만든다. Rate Limit Key는 장기 Backup 대상이 아니며 OAuth State는 5분 단기 상태다. Blacklist는 보안 상태이므로 Persistence가 필요하다.

현재 Backend/Auth가 `blacklist:*`를 쓰고 Gateway가 같은 Key를 읽는다. Hybrid Edge에서 Redis를 단순히 두 인스턴스로 분리하면 Revocation 계약이 깨질 수 있으므로 Blacklist 소유권·전달·재시도·정합성 계약을 먼저 결정한다.

## Data Ownership Boundary

현재 Physical PostgreSQL Instance는 **1개**다. 현재 구현을 과장하지 않으면서 다음 논리 소유권을 목표로 한다.

| 논리 소유자 | 데이터 |
| --- | --- |
| Shared Identity | Account, Credential, ExternalIdentity, Refresh Session, 인증·계정 상태 |
| Carelog Core | 고객, 관계, Timeline, 상담·케어 기록, Follow-up, Handoff |
| Dev Harness Backend | Project Context, Session, Handoff, Approval, Workflow 상태 |
| Finance Harness Backend | 금융 질문, Checklist, Journal, Review, 금융 Product 상태 |
| Shared AI | Provider Configuration Reference, AI Job, Usage, Audit, Knowledge Metadata, 필요 시 Vector Data |

다른 소유자의 Table 직접 Write를 금지하고 Cross-boundary Join을 최소화한다. 독립 추출 시 API/Event 계약으로 전환하며 Schema·Database 분리는 후속 결정이다. 현재 모든 Service가 하나의 Schema를 무제한 공유하는 구조를 목표로 하지 않는다. pgvector 소유권은 실제 Use Case에 따라 정하며 현재 설치·Migration·운영 완료 상태가 아니다.

## Service Extraction Roadmap and Triggers

1. **Current Auth/OAuth Boundary 안정화**
2. **Product Client Registry**
3. **Redirect URI exact allowlist**
4. **issuer/audience 계약**
5. **Stable Error Code와 Current Session API**
6. **Web/Mobile Session 계약**
7. **Auth Schema/Migration 소유권**
8. **내부 Auth Module과 Carelog Core 의존성 분리**
9. **독립 Carelog Auth Service 추출** — 독립 Build·Runtime·Configuration, Gateway Route, Token Revocation·Rollback을 검증한다.
10. **실제 다제품 소비 후 Shared Identity 일반화** — 제품 중립 API, 다제품 Tenant·Client 경계, Migration·Backward Compatibility를 확장한다.

API·데이터 소유권·보안 계약은 물리 Process 분리보다 먼저 고정한다. Finance Harness와 Dev Harness Backend, Shared AI Runtime의 실제 구축 순서는 Portfolio WIP와 검증된 Product 요구를 따른다.

Finance Harness와 Dev Harness의 실제 순서는 Portfolio WIP 정책을 따르며 동시에 모두 구현한다고 가정하지 않는다.

## Security and Availability Boundary

- 공유기 Port Forwarding을 사용하지 않는다. Tunnel Origin은 SCG만 가리키며 Backend·Redis·PostgreSQL용 별도 Public Tunnel Route를 만들지 않는다.
- SCG와 Application Runtime은 loopback 또는 격리된 private bridge에 bind하고, Redis·PostgreSQL은 외부 Interface에 bind하지 않는다.
- Gateway는 외부 `X-User-*`, `X-Gateway-Secret`을 제거하고 검증된 Header와 `X-Gateway-Secret`만 재주입한다.
- OAuth code, state, verifier, Token, 환자·고객 개인정보를 로그에 남기지 않는다. Secret은 Repository, Compose, Image, 로그에 저장하지 않는다.
- GPT API Key는 Shared AI가 구현되기 전에는 어떤 Browser·Gateway에도 전달하지 않으며, 실제 민감정보 전송은 별도 정책 승인 전 금지한다.

Mac mini, 가정 전원, ISP, cloudflared, SCG, Application Runtime, Redis, PostgreSQL은 단일 장애점이다. Profile 1은 낮은 SLA 파일럿까지만 허용한다.

## Deferred Options and Consequences

즉시 EC2/RDS, Gateway 전용 EC2, 온프레미스 Nginx, EC2↔Mac VPN, ALB/NLB, ElastiCache, Kubernetes, Multi-AZ, Service Mesh는 현재 MVP에는 비용·관리 지점·복잡도를 늘리므로 제외한다. HTTP/SSE/NATS와 gRPC·Kafka·Kubernetes의 구체적 선택 및 도입 Trigger는 [ADR-002](ADR-002-platform-communication-messaging-scaling.md)가 소유한다.

이 결정은 서버비와 관리 지점을 최소화하면서 현재 revocation 계약을 보존한다. 반면 단일 Host·ISP·전원 장애, Backup/Restore 책임, 높은 SLA 불가, 향후 pgvector와 Core Transaction의 자원 경쟁을 감수한다.

## PR #34 Merge Gate

PR #34는 Draft를 유지한다. Merge 전 Frontend Callback, Live Kakao PKCE S256, 연결 계정 200·미연결 계정 409, 잘못된 verifier 실패, state 재사용 실패, 실제 Redis Rate Limit과 429, 실제 Redis 장애 시 503 fail-closed, Cloudflare Header chain과 `trusted-proxy-hops`, Gateway·Backend 직접 접근 차단, 민감정보 로그 비노출이 필요하다.

SCG Redis Rate Limiter의 Redis Script 오류가 허용 응답으로 처리될 가능성이 있어 Mock만으로 Redis 장애 503 fail-closed를 증명할 수 없다. 실제 Redis 장애 통합 검증 전 이 Gate의 상태는 **Unverified**다.

## Current Repository Gap

- 개발 Compose의 PostgreSQL·Redis Host port publish는 개발 용도이며 운영 Profile에 그대로 재사용하면 안 된다.
- Gateway·Backend bind address, Redis ACL/Persistence, Mac 자동 시작·배포 방식, Backup/Restore 자동화가 미확정이다.
- 독립 Carelog Auth Service, Shared AI Runtime, Dev Harness Backend, Finance Harness Backend, pgvector Extension/Migration, GPT API 연동, 실제 Cloudflare Header chain, `trusted-proxy-hops`는 미구현 또는 미검증이다.
