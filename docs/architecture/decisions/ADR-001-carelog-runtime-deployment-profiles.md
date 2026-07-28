# ADR-001: Carelog MVP Runtime·Deployment Profile

| 항목 | 내용 |
| --- | --- |
| 상태 | Accepted — Profile 1만 현재 승인 |
| 결정일 | 2026-07-28 |
| 결정 범위 | Carelog MVP의 공개 진입점, Runtime·Data 배치, Redis 경계와 이전 기준 |
| Repository | `care-log/carelog-be` |
| 관련 PR | #34 `feat/kakao-oauth-gateway-publication` (Draft, 구현 변경 없음) |
| 승인 범위 | Development, Local Live E2E, Pilot, Low-SLA Initial Operation |
| 재검토 조건 | Hybrid/AWS Trigger 발생, 실제 SLA·RPO/RTO 합의, Cloudflare Header chain 검증 |

## Context

Carelog는 1인 개발·운영의 초기 MVP다. 서버비와 운영 지점을 최소화하면서도 Kakao OAuth, Gateway JWT 검증·Header 정규화·Rate Limit, PostgreSQL 및 향후 pgvector, GPT API 기반 기능을 준비해야 한다. 이미 보유한 Mac mini M4를 활용할 수 있으나, 현재 목표는 HA나 높은 SLA가 아니다.

현재 Runtime 계약은 Spring Cloud Gateway(SCG)와 Spring Boot가 Redis를 사용한다. Gateway OAuth Rate Limit, OAuth state/PKCE verifier, 로그아웃 Token blacklist는 서로 다른 책임이지만, 현재 `blacklist:*`는 Backend가 쓰고 Gateway가 읽는 공유 계약이다.

Foundation의 미래 Draft는 Runtime 배포 사실을 뜻하지 않는다. 이 결정은 Carelog MVP의 현재 제품별 Runtime 경계만 정의한다.

## Decision

### Profile 1 — Mac mini Baseline (현재 선택)

**Approved for Development, Local Live E2E, Pilot and Low-SLA Initial Operation**

```text
사용자
→ Cloudflare Edge
→ Cloudflare Tunnel
→ Mac mini M4의 cloudflared
→ Spring Cloud Gateway
→ Spring Boot
├─ 단일 Redis
├─ PostgreSQL + pgvector
└─ GPT API 기반 AI 기능
```

- EC2, RDS, ElastiCache, ALB/NLB, Nginx, Tailscale/WireGuard, 공유기 Port Forwarding을 사용하지 않는다.
- 한 Mac mini에서 Application Runtime과 Data Runtime을 운영한다. Cloudflare Tunnel만 외부 요청을 SCG로 전달한다.
- Backend, Redis, PostgreSQL은 인터넷에 직접 노출하지 않는다. GPT API만 Backend가 호출하며 로컬 LLM은 사용하지 않는다.
- 이 Profile은 HA 또는 높은 SLA 설계가 아니다.

### Profile 2 — Hybrid Edge Improvement (Deferred)

```text
사용자 → Cloudflare Tunnel → EC2
                           ├─ cloudflared
                           ├─ Spring Cloud Gateway
                           └─ Gateway Redis
→ Tailscale 또는 WireGuard → Mac mini
                           ├─ Spring Boot
                           ├─ Backend Redis
                           └─ PostgreSQL + pgvector
```

다음이 필요해질 때만 도입한다: Gateway 공격 트래픽의 물리적 격리, 여러 Backend의 공통 Gateway, 안정적인 Edge Host, Mac 재시작과 공개 진입점 장애의 분리, 그리고 EC2·VPN·Redis 이중 운영을 감당할 예산과 역량.

현재 Backend는 `blacklist:*`를 쓰고 Gateway는 같은 Redis Key를 읽는다. Redis를 단순히 둘로 분리하면 로그아웃 직후 Token revocation이 깨질 수 있다. Hybrid 도입 전 Blacklist 소유권, Backend→Gateway Redis 쓰기 또는 revocation 전달, 장애·재시도·정합성 계약을 별도 결정한다.

### Profile 3 — AWS Migration Target (Document Only)

```text
사용자 → Cloudflare Tunnel → 단일 EC2
                           ├─ cloudflared
                           ├─ Spring Cloud Gateway
                           ├─ Spring Boot
                           └─ Redis
→ Private Single-AZ RDS PostgreSQL + pgvector
```

1차 이전의 목표 설계일 뿐 현재 구축하지 않는다. 이후 Managed Redis/Valkey, RDS Multi-AZ, Gateway/Application 다중 인스턴스, Tunnel replica 또는 Load Balancer, 중앙 Monitoring·Deployment Automation, Blue/Green 또는 Rolling Deployment를 검토한다.

AWS 재검토 Trigger는 Mac 전원·ISP·디스크 장애 반복, 합의 RPO/RTO를 로컬 Backup으로 충족하지 못함, 중요한 고객 데이터·유료 사용자·외부 SLA 증가, PostgreSQL/pgvector가 Core API를 압박함, 단일 Mac 자원 한계, 원격 장애 복구 곤란, 다중 Application instance 필요다.

## Redis Decision

현재는 Redis **1개**를 쓴다. 물리 분리보다 Key prefix와 가능한 경우 역할별 ACL user로 책임을 분리한다.

| Key 범위 | 소유·용도 | TTL/보존 판단 |
| --- | --- | --- |
| `request_rate_limiter.*` | Gateway OAuth Rate Limit | 단기 조절 상태; 장기 Backup 대상 아님 |
| `oauth:state:*` | Backend OAuth state와 PKCE verifier | 5분 단기 상태; 재사용 시 소비됨 |
| `blacklist:*` | Backend 로그아웃 기록, Gateway 인증 조회 | Access Token 잔여 TTL; 보안 상태이므로 Persistence 필요 |

ACL은 최소 권한으로 Gateway와 Backend를 분리하되, 현재 Gateway의 `blacklist:*` 읽기와 Backend의 쓰기 계약은 보존한다. Redis 전체 유실 시 blacklist 효력이 사라질 수 있으므로 JWT 전체 무효화 또는 Signing Key rotation 절차를 후속으로 결정한다.

## Security Boundary

- 공유기 Port Forwarding을 사용하지 않는다. Tunnel Origin은 SCG만 가리키며 Backend·Redis·PostgreSQL용 별도 Public Tunnel Route를 만들지 않는다.
- SCG와 Backend는 loopback 또는 격리된 private bridge에 bind하고, Redis·PostgreSQL은 외부 Interface에 bind하지 않는다. 실제 bind address와 Mac Firewall은 배포 전 검증한다.
- `X-Gateway-Secret` 및 Gateway의 외부 `X-User-*`, `X-Gateway-Secret` 제거·재주입 계약을 유지한다.
- OAuth code, state, verifier, Token, 환자·고객 개인정보를 로그에 남기지 않는다. Secret은 Repository, Compose, Image, 로그에 저장하지 않는다.
- GPT API Key는 Backend만 소유한다. Browser나 Gateway로 전달하지 않으며, 민감정보를 GPT API에 전송하려면 별도 정책 승인이 필요하다.

Cloudflare 사용만으로 Origin이 안전해지는 것은 아니다. Origin의 Public Listener·Tunnel route·Firewall·직접 접근을 모두 함께 제한해야 한다.

## Availability Boundary

Mac mini, 가정 전원, ISP, cloudflared, SCG, Backend, Redis, PostgreSQL은 각각 단일 장애점이다. 현재 Profile은 낮은 SLA 파일럿까지만 허용한다. 실제 SLA를 요구하면 Hybrid 또는 AWS Profile을 재검토한다.

## Deferred Options

| 옵션 | 현재 제외 이유 |
| --- | --- |
| 즉시 EC2/RDS | 비용과 관리 지점이 MVP 가치보다 큼 |
| Gateway 전용 EC2 | 단일 Backend MVP에는 추가 경계·장애점만 늘림 |
| 온프레미스 Nginx | SCG와 Tunnel 앞에 명확한 고유 책임이 없음 |
| EC2↔Mac VPN | Hybrid를 위한 복잡도이며 현재 불필요 |
| ALB/NLB, ElastiCache, Kubernetes, Multi-AZ, Service Mesh | HA·수평 확장 요구가 없는 MVP에는 과설계 |

## Consequences

장점은 서버비와 관리 지점 최소화, Redis 하나로 현재 revocation 계약 보존, Local Live E2E·파일럿 배포 단순화, 기존 SCG와 PR #34 재사용이다. 단점은 단일 Host·ISP·전원 장애, 개발 Host와 운영 Host 충돌 가능성, 사용자에게 남는 Backup/Restore 책임, 높은 SLA 불가, pgvector 부하와 Core Transaction의 자원 경쟁이다.

## PR #34 Merge Gate

PR #34는 Draft를 유지한다. Merge 전 다음이 필요하다: Frontend Callback, Live Kakao PKCE S256, 연결 계정 200·미연결 계정 409, 잘못된 verifier 실패, state 재사용 실패, 실제 Redis Rate Limit과 429, 실제 Redis 장애 시 503 fail-closed, Cloudflare Header chain 관찰과 `trusted-proxy-hops` 확정, Gateway·Backend 직접 접근 차단, 민감정보 로그 비노출.

특히 SCG Redis Rate Limiter의 Redis Script 오류가 허용 응답으로 처리될 가능성이 있어, Mock만으로 Redis 장애 503 fail-closed를 증명할 수 없다. 실제 Redis 장애 통합 검증 전 이 Gate의 상태는 **Unverified**다.

## Current Repository Gap

- 개발 Compose는 PostgreSQL과 Redis Port를 Host에 publish한다. 개발 용도 자체를 취약점으로 단정하지 않지만, 운영 Profile에 그대로 재사용하면 안 된다.
- Gateway·Backend `server.address`, Redis ACL/Persistence 운영값, Mac 자동 시작·배포 방식, Backup/Restore 자동화가 미확정이다.
- pgvector Extension·Migration, GPT API 연동, 실제 Cloudflare Header chain, `trusted-proxy-hops`가 미구현 또는 미검증이다.
