# 1. 사용자 관리 (User System)

```
Table users {
id bigint [pk, increment]
// 🔒 외부 노출용 UUID - Spring Cloud Gateway, FastAPI 등 외부 시스템에서 순차 ID 노출 방지
public_id uuid [unique, not null, default: `gen_random_uuid()`]
// 🏢 멀티테넌시 식별자 - JWT 클레임에서 주입, 동일 조직 유저들이 같은 값 공유
organization_id uuid [not null]

user_id varchar(50) [unique, not null]
email varchar(255) [unique, not null]
password varchar(255) [not null] // BCrypt Hash
name varchar(100) [not null]

role varchar(20) [not null] // 'MANAGER', 'CUSTOMER'
// MANAGER 필수, CUSTOMER는 NULL
manager_type varchar(30) // 'PHYSICAL_THERAPIST', 'REAL_ESTATE_AGENT' 등

// v1.1 암호화 적용 예정 (JPA Converter)
phone_encrypted text
address_encrypted text

created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]
deleted_at timestamptz
created_by varchar(255)
updated_by varchar(255)

Indexes {
(email) [unique, name: 'uk_user_email']
(user_id) [unique, name: 'uk_user_user_id']
(public_id) [unique, name: 'uk_user_public_id']
(organization_id) [name: 'idx_user_organization']
}

Note: '''
[User 역할 전략]
- MANAGER/CUSTOMER 단일 테이블 관리 (Single Table)
- manager_type: MANAGER일 때만 필수, CUSTOMER는 NULL
- 가입 엔드포인트 분리: POST /users/managers, POST /users/customers
- 서비스 레이어에서 role에 따라 manager_type 필수 여부 검증

[식별자 전략]
- id: 내부 PK (JPA 연관관계, DB 조인용)
- public_id: 외부 노출용 UUID (Gateway 라우팅, FastAPI 연동 시 사용)
- organization_id: 멀티테넌시 격리용 (Hibernate Filter로 자동 WHERE 조건 적용)
  '''
  }

Table tokens {
id bigint [pk, increment]
user_id bigint [ref: > users.id]
refresh_token varchar(512) [not null]
expires_at timestamptz [not null]
created_at timestamptz

Indexes {
(refresh_token) [name: 'idx_token_refresh']
}
}
```

# 2. 관계 관리 (Relation System)

```
Table relations {
id bigint [pk, increment]
organization_id uuid [not null]
manager_id bigint [ref: > users.id]
customer_id bigint [ref: > users.id]
status varchar(20) [not null] // 'ACTIVE', 'TERMINATED'

created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]
deleted_at timestamptz
created_by varchar(255)
updated_by varchar(255)

Indexes {
(manager_id) [name: 'idx_relation_manager']
(customer_id) [name: 'idx_relation_customer']
(organization_id) [name: 'idx_relation_organization']
}

Note: '''
[중복 방지 전략 - 애플리케이션 레벨 제어]
⚠️ DB Unique Constraint 미적용

[Why?]
- Soft Delete 된 관계(과거 이력)와 신규 관계 간의 충돌 방지
- 고객이 탈퇴 후 재가입하거나, 1년 뒤 재방문하는 시나리오 유연 대응

[How?]
- 로직: `deleted_at IS NULL`인 활성 관계가 없을 때만 생성 허용
  '''
  }
```

# 3. 일지 시스템 (Journal System)

```
Table journal_templates {
id bigint [pk, increment]
public_id uuid [unique, not null, default: `gen_random_uuid()`]
organization_id uuid [not null]
name varchar(100) [not null]
fields jsonb [not null] // 양식 필드 스펙 배열
// 예: [{"key":"bodyPart","label":"부위","type":"text"}, ...]
status varchar(20) [not null, default: 'ACTIVE'] // 'ACTIVE', 'INACTIVE'

created_at timestamptz [not null, default: `now()`]
updated_at timestamptz [not null, default: `now()`]
created_by varchar(255)
updated_by varchar(255)

Indexes {
(public_id) [unique, name: 'uk_template_public_id']
(organization_id) [name: 'idx_template_organization']
// MVP 이후, 병목 측정 후 적용
// (fields) [type: gin, name: 'idx_template_fields_gin']
}

Note: '''
[템플릿 설계 전략]
- 삭제 없음. status = INACTIVE로 비활성화만 허용.
- 자유 양식 작성 지원: relation_journals.template_id nullable
- fields JSONB: 양식 필드 스펙 배열 (key, label, type)
  '''
  }



Table relation_journals {
id bigint [pk, increment]
public_id uuid [unique, not null, default: `gen_random_uuid()`]
organization_id uuid [not null]
relation_id bigint [ref: > relations.id]
template_id bigint [ref: > journal_templates.id, null] // 자유 양식 허용

title varchar(255) [not null] // 일지 제목 — 정렬/필터 대상
visit_date date [not null]   // 방문일 — 정렬/필터 대상
case_data jsonb [not null]   // 업무 데이터 — AI 파이프라인 전달용 (익명 임상 데이터)
private_data jsonb           // 개인 식별 정보 (PII) — 내부 전용, AI 파이프라인 진입 불가
status varchar(20) [not null, default: 'ACTIVE'] // 'ACTIVE', 'SUPERSEDED'

// 이력 추적: 자기 참조 FK (이전 버전의 id)
previous_id bigint [ref: > relation_journals.id, null]

created_at timestamptz [not null, default: `now()`]
// updated_at, updated_by 없음 — append-only 설계, 수정 시 새 레코드 INSERT
created_by varchar(255)

Indexes {
(public_id) [unique, name: 'uk_journal_public_id']
(relation_id) [name: 'idx_journal_relation']
(organization_id) [name: 'idx_journal_organization']
(previous_id) [name: 'idx_journal_previous_id'] // 이력 역추적 성능
// MVP 이후, 병목 측정 후 적용
// (content) [type: gin, name: 'idx_journal_content_gin']
}

Note: '''
[SUPERSEDED 패턴 — 의료법 대응]
- 물리 삭제 불가. 삭제 차단 3계층:
  1. DB: 앱 계정 REVOKE DELETE
  2. Repository: delete() override → CustomException
  3. API: 삭제 엔드포인트 미제공

[수정 흐름]
  기존 ACTIVE 레코드 → status = SUPERSEDED
  새 레코드 INSERT (새 public_id, previous_id = 기존.id)

[이력 조회 — Recursive CTE]
  WITH RECURSIVE journal_history AS (
      SELECT * FROM relation_journals WHERE id = :latestId
      UNION ALL
      SELECT j.* FROM relation_journals j
      INNER JOIN journal_history jh ON j.id = jh.previous_id
  )
  SELECT * FROM journal_history ORDER BY created_at DESC;
  '''
  }
```