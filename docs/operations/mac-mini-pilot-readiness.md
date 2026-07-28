# Mac mini Pilot Readiness

이 문서는 [ADR-001](../architecture/decisions/ADR-001-carelog-runtime-deployment-profiles.md)의 Profile 1을 위한 공개 가능한 파일럿 준비 체크리스트다. 구현 방식과 실제 운영값은 Private Runbook에서 확정한다.

## Host

- [ ] 유선 Ethernet을 사용한다.
- [ ] 자동 Sleep을 비활성화하고, 정전 후 자동 시작을 확인한다.
- [ ] UPS 사용을 권장한다.
- [ ] 임의 macOS Update·Reboot를 피하고 유지보수 시간을 정의한다.
- [ ] FileVault가 켜진 상태에서 무인 재부팅 후 복구 가능한지 검증한다.
- [ ] 개발 사용자와 운영 프로세스의 분리 필요성을 검토한다.

## Process

- [ ] cloudflared, Redis, PostgreSQL, Backend, Gateway의 자동 시작을 구성한다.
- [ ] 각 프로세스의 비정상 종료 자동 재시작, readiness, 의존 서비스 재시도를 확인한다.
- [ ] 로그 회전과 디스크 사용량 Alert를 구성한다.
- [ ] Docker Desktop 로그인 세션에 Runtime이 의존하지 않게 한다.

`launchd`, container, native process 중 어떤 방식으로 운영할지는 이 문서에서 확정하지 않는다.

## Network

- [ ] 공유기 Port Forwarding을 만들지 않는다.
- [ ] Cloudflare Tunnel Origin은 SCG만 대상으로 한다.
- [ ] Backend·Redis·PostgreSQL의 별도 Public Tunnel Route를 만들지 않는다.
- [ ] SCG·Backend의 실제 bind address와 Redis·PostgreSQL의 외부 Interface 미노출을 검증한다.
- [ ] Mac Firewall 규칙과 Gateway·Backend 직접 접근 차단을 검증한다.
- [ ] Staging에서 `remoteAddress`, `X-Forwarded-For`, `CF-Connecting-IP`를 관찰한 뒤 `trusted-proxy-hops`를 확정한다.

## Backup 후보 (RPO/RTO 미확정)

- [ ] 매일 `pg_dump` custom format을 생성하고 암호화한 off-host 저장소로 보관한다.
- [ ] 보존 후보는 7 daily / 4 weekly / 12 monthly다.
- [ ] 매월 별도 PostgreSQL에 Restore Drill을 수행한다.
- [ ] Schema, Row count, 주요 Query, pgvector Extension 및 검색 결과를 검증한다.

이는 초기 후보이며 승인된 RPO/RTO 또는 실제 Backup 저장소 정보를 의미하지 않는다.

## Redis

- [ ] AOF와 `appendfsync everysec`, 영속 Volume을 구성한다.
- [ ] 명시적 `maxmemory`와 `noeviction`을 설정한다.
- [ ] 메모리 80% 이전 Alert를 구성하고 `blacklist:*` Key 값을 노출하지 않는다.
- [ ] Redis 전체 유실 시 JWT 전체 무효화 또는 Signing Key rotation을 실행할 수 있도록 절차를 결정한다.
- [ ] Gateway Rate Limit, OAuth state/PKCE, Token blacklist의 TTL·ACL·Persistence를 실제 Runtime에서 검증한다.

## AI

- [ ] GPT API 장애가 Core CRUD를 중단시키지 않게 한다.
- [ ] Timeout, 제한된 Retry, Concurrency 제한, 비용 한도를 둔다.
- [ ] Prompt·Response를 로그에 남기지 않는다.
- [ ] 개인정보를 최소화·비식별화하며, 실제 민감정보 전송은 별도 정책 승인 전 금지한다.

## PR #34 Runtime Verification Gate

- [ ] Frontend Callback 및 Live Kakao PKCE S256을 검증한다.
- [ ] 연결 Account 200, 미연결 Account 409, 잘못된 verifier 실패, state 재사용 실패를 확인한다.
- [ ] 실제 Redis 기반 Rate Limit 429를 확인한다.
- [ ] 실제 Redis 장애에서 OAuth 요청이 503 fail-closed인지 통합 검증한다. **현재 Unverified**.
- [ ] Cloudflare Header chain, `trusted-proxy-hops`, Gateway·Backend 직접 접근 차단, 민감정보 로그 비노출을 확인한다.
