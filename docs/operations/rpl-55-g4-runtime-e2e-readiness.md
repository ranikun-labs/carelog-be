# RPL-55 G4 — 실제 Gateway→Carelog 회귀 준비 (검증 계획)

> 상태: **READINESS / REVIEW DRAFT**, 실행·승인 증거가 아님. Source Merge, Production Cutover, DB·Secrets 변경 승인 아님.
> Jira: [RPL-55](https://ranikunlabs.atlassian.net/browse/RPL-55) / 설계: ADR-0017, ADR-0019
> 조사 기준: `carelog-be/dev@0b8a6b6a06bdc961cf6274795fbe4ab9618b5ced`, `platform-services/main@9f04f44e5dfc88272fcf380f96524f5bfdc92b75`.
> 관련 미병합 변경: `carelog-be#52` (테스트 전용, `fafa932...`), `platform-services#30/#33/#32` (별도 HOLD). 해당 PR Merge 완료라고 주장하지 않는다.

## 1. 어디까지 구현되어 있나?

| 경계 | 이미 존재하는 구현·증거 | 미확인·미완료 |
| --- | --- | --- |
| Gateway → Carelog | `JwtGlobalFilter`의 서명 검증, 외부 `X-User-Id`·`X-Organization-Id`·`X-Role`·`X-Public-Id`·`X-Gateway-Secret`·`X-Service-*` 제거 및 검증 컨텍스트 재주입; `GatewayRoutingHttpIntegrationTest`의 Stub Upstream 전달 테스트 | **실제 Carelog Backend**까지 연결한 비운영 통합 회귀 및 최종 auth-wire 호환성 |
| Carelog 인증 | `GatewayHeaderAuthFilter`의 shared secret/UUID 검증과 `UserPrincipal` 구성; `TenantFilter`의 조직 범위; 기존 Customer 회귀 | Gateway→실제 Product 경로에서 missing/invalid/revoked token·타 조직 접근 E2E |
| Identity → Carelog CRM projection | `GET /api/v1/internal/identity/claims/{accountId}`, ADR-0019 전용 `X-Service-Id`·`X-Service-Secret` 계약, Platform Identity HTTP 어댑터 | 실제 Core→Carelog 연결에서 `200 / 404 / 401·403 / 5xx` 의미·격리 검증 |
| Owner Gate | RPL-54 등 Jira 선행 Gate 완료 표시, `carelog-be#52`의 기존 9 PASS·독립 리뷰 PASS 보고 | G4 전체 회귀·Cutover·Rollback 증거. 기존 PR 및 Jira 상태를 완료로 승격하지 않음 |

**중복 금지:** 이미 수행된 MockMvc Controller 테스트와 Gateway의 fake-upstream routing 테스트를 복제하지 않는다. `#52`는 Customer Controller 부정 테스트만 추가한다.

## 2. 다음 최소 실행 Slice — 비운영 Runtime E2E

**실행 조건:** 독립 worktree 및 테스트 전용 환경. Gateway·Core Identity·Carelog Backend는 **실제 애플리케이션 프로세스**여야 한다. 인증 토큰·서비스 시크릿·DB 자격 증명은 로그/문서에 저장하지 않는다. 실서비스 DNS/DB/계정/클라우드 전환 금지.

| ID | 입력/행동 | 필수 검증 |
| --- | --- | --- |
| G4-01 | 유효한 테스트 사용자 JWT로 Gateway 경유 Customer 조회 | Identity accountId UUID, organizationId, publicId, role이 Carelog Principal까지 전달; 정상 CRM 응답 |
| G4-02 | 외부 호출이 신뢰 헤더를 위조 | Gateway가 위조값을 모두 제거하고 검증된 값만 재삽입; `X-Service-*`는 Product에 전달되지 않음 |
| G4-03 | 무인증·만료·서명 위조·폐기 토큰 | Gateway에서 401 계열로 거부, Carelog upstream 요청 **0건**. 정확한 외부 에러 형식은 현행 구현 계약을 기준으로 기록 |
| G4-04 | 유효 JWT이나 organizationId/publicId 누락·잘못된 UUID | Product에서 Fail-closed, CRM handler 호출 없음. 현재 테스트된 403/401 경계를 구분 |
| G4-05 | 조직 A 사용자로 조직 B CRM 데이터 접근 | 타 조직 데이터 유출 없음; Product 자체 권한이 최종 권위 |
| G4-06 | Platform Core Identity → Carelog 내부 projection 정상 조회 | ADR-0019 단일 `GET` 경로·전용 서비스 자격·필드 3개만 사용 |
| G4-07 | 내부 projection 미존재/자격 오류/장애 | `404`만 진짜 미존재로 처리; `401/403`을 미존재로 오인하지 않음; `5xx`·네트워크 장애는 dependency failure |
| G4-08 | public Gateway로 `/api/v1/internal/**` 직접 접근 | JWT 유무와 무관하게 거부, 내부 서비스 경로 및 시크릿 노출 없음 |

## 3. 증거·완료 조건

- 각 테스트에 **실행한 Git HEAD, 이미지 Digest, Profile, 실제 프로세스 구성, 명령, Exit Code, PASS/FAIL/SKIP**를 남긴다.
- Gateway의 Stub-Upstream 통과와 **실제 Gateway→Carelog 프로세스 E2E**를 별개의 증거로 관리한다.
- `carelog-be#52`와 `platform-services#30/#33/#32`의 Merge 여부·최종 Tree를 실행 직전 재조회한다. 기존 PASS를 다른 Tree의 PASS로 재라벨링하지 않는다.
- 호환 JWT의 필수 `iss/aud` 및 최종 사용자-auth transport는 현행 Draft 계약을 암묵적으로 승인하지 않는다.
- 결과는 `G4_NONPROD_REGRESSION_PASS` 또는 `BLOCKED`까지. G4 Source Cutover·Legacy Auth 제거·DB migration·Production rollout·rollback switch는 **별도 Owner Gate**.

## 4. 가장 짧은 실행 순서

1. 기존 Gateway routing/headers·Carelog Controller/Customer 테스트 로그를 재사용하고 미검증 시나리오만 표시한다.
2. 비운영 테스트용 Gateway/Core/Carelog 세 프로세스를 기동해 **G4-01~08만** 실행한다.
3. 실제 Evidence로 FAIL만 수리하고 동일 범위 독립 검수한다. Runtime이 없으면 테스트를 PASS로 표시하지 말고 `BLOCKED_ENV`로 종료한다.
4. PASS 후 RPL-55 전체 G4 Gate(소유권·프로필·데이터 이동·Rollback·실제 배포)를 Owner에게 별도로 제출한다.

_이번 문서는 분석/실행 계약 정리만 수행했다. E2E 실행: NOT_RUN._


## 5. 비운영 HTTP 스모크 검사기 (Draft 구현)

**추가 코드:** \`scripts/rpl55_g4_nonprod_smoke.py\`, \`scripts/tests/test_rpl55_g4_nonprod_smoke.py\`.

\`python3 scripts/rpl55_g4_nonprod_smoke.py\`는 **읽기 전용 GET** 요청만 수행하며 Python 표준 라이브러리만 쓴다. localhost/127.0.0.1/::1의 HTTP URL만 허용하고 외부 호스트·HTTPS URL·경로·쿼리는 거부한다. 반드시 일회성 로컬/비운영 Gateway와 합성 MANAGER 토큰을 사용해야 한다.

~~~bash
# 자체 테스트는 Product/Platform Runtime 없이 실행할 수 있다.
python3 -m unittest discover -s scripts/tests -p 'test_rpl55_g4_*.py' -v

# 비운영 Gateway와 실제 Carelog Backend를 별도로 기동한 이후에만 실행.
export G4_NONPROD_ACK=YES_NONPROD_ONLY
export G4_GATEWAY_URL=http://127.0.0.1:8080
# 아래 토큰은 비운영 테스트 계정으로 직접 발급하고 값은 파일·채팅·로그에 저장하지 않는다.
export G4_MANAGER_ACCESS_TOKEN='(set a disposable synthetic access token)'
python3 scripts/rpl55_g4_nonprod_smoke.py
unset G4_MANAGER_ACCESS_TOKEN

# 선택: 이미 폐기/만료된 합성 토큰과 다른 조직에 속한 Customer fixture ID
# G4_EXPIRED_ACCESS_TOKEN, G4_REVOKED_ACCESS_TOKEN,
# G4_FOREIGN_CUSTOMER_PUBLIC_ID
~~~

- 필수 범위: G4-01 유효 MANAGER JWT의 Customer Collection 조회(200), G4-02 외부 신뢰 헤더 위조 시 동일 조회 결과, G4-03 토큰 누락·형식 오류(401), G4-08 내부 경로 접근(403).
- 선택 범위: 실제 만료/폐기 토큰 401, 시드된 타 조직 Customer 404.
- 프로그램 결과 \`EDGE_SMOKE_PASS_ONLY\`는 **G4 완료가 아니다**. G4-04/06/07, 실제 Platform Core→Carelog 호출, Upstream 호출 횟수, 데이터 소유권/DB migration, rollback은 NOT_RUN이다.
- 본 스크립트에 연동된 고정 Stub 서버의 Python 자체 테스트는 **검사기 동작 검증**일 뿐 실제 Product E2E가 아니다.
- 로그에 사용자 토큰, 응답 본문 및 시크릿을 출력하지 않는다. 환경변수는 스모크 완료 후 정리한다.
- **실행 상태: NOT_RUN.** 이번 PR에는 비운영 환경과 테스트용 JWT가 없으므로 어떠한 PASS도 새로 주장하지 않는다.
