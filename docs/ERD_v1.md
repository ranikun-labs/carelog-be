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
profession_type varchar(30) [not null]
name varchar(100) [not null]
version int [not null, default: 1] // 🌟 템플릿 버전 관리
description text
schema jsonb [not null] // Form Builder용 JSON 스키마
is_active boolean [default: true]

created_at timestamptz
updated_at timestamptz
deleted_at timestamptz

Indexes {
// [필수] 템플릿 버전별 유일성 보장
(profession_type, name, version) [unique, name: 'uk_template_version']
}

Note: '''
[템플릿 버전 관리 전략]
- 템플릿 수정 시 기존 레코드를 수정하지 않고, 새 버전(version++)을 Insert
- 목적: 과거에 작성된 일지가 템플릿 변경으로 인해 깨지는 현상(Data Corruption) 방지
  '''
  }



Table relation_journals {
id bigint [pk, increment]
relation_id bigint [ref: > relations.id]
author_id bigint [ref: > users.id]

template_id bigint [ref: > journal_templates.id]
// 작성 당시의 템플릿 버전 스냅샷 (조인 없이 버전 확인 가능)
template_version int [not null]

title varchar(200) [not null]
content jsonb [not null] // NoSQL-like 유연한 데이터 저장
journal_date date [not null]

created_at timestamptz
updated_at timestamptz
deleted_at timestamptz

Indexes {
// ✅ [Minimum] 조인 성능 방어를 위한 최소한의 인덱스
(relation_id) [name: 'idx_journal_relation']
}

Note: '''
[인덱스 전략: Minimum & YAGNI]
1. 필수(Minimum): `relation_id` 인덱스는 조인 성능 및 JPA N+1 방지를 위해 선제적 적용.
2. 지양(YAGNI): 정렬/필터링을 위한 복합 인덱스는 초기 단계에서 배제 (Write 오버헤드 최소화).

[성능 튜닝 로드맵 - Portfolio]
- 예상 병목: 데이터 적재 후 `ORDER BY journal_date DESC` 조회 시 File Sort 발생
- 해결 계획: 슬로우 쿼리 포착 시 (relation_id, journal_date DESC) 복합 인덱스로 교체하여 최적화 (Sort 연산 제거)
  '''
  }
```