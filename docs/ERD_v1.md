# 1. 사용자 관리 (User System)

```
Table users {
id bigint [pk, increment]
// 🔒 외부 노출용 식별자 (Sequential ID 노출 방지)
public_id uuid [unique, not null, default: `gen_random_uuid()`]

user_id varchar(50) [unique, not null]
email varchar(255) [unique, not null]
password varchar(255) [not null] // BCrypt Hash
name varchar(100) [not null]

role varchar(20) [not null] // 'MANAGER', 'CUSTOMER'
// MANAGER 필수, CUSTOMER는 NULL (도메인 확장 대비)
profession_type varchar(30) // 'PHYSICAL_THERAPY', 'REAL_ESTATE'

// v1.1 암호화 적용 예정 (JPA Converter)
phone_encrypted text
address_encrypted text

created_at timestamptz [default: `now()`]
updated_at timestamptz [default: `now()`]
deleted_at timestamptz

Indexes {
// [필수] 데이터 무결성 및 로그인 성능용
(email) [unique, name: 'uk_user_email']
(user_id) [unique, name: 'uk_user_user_id']
(public_id) [unique, name: 'uk_user_public_id']
}

Note: '''
[확장성 설계]
- public_id: MSA 전환 및 외부 API 연동 시 내부 PK 은닉을 위해 도입
- profession_type: 단일 플랫폼에서 여러 직군(물리치료, 부동산 등)을 수용하기 위한 Discriminator Column
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
manager_id bigint [ref: > users.id]
customer_id bigint [ref: > users.id]
status varchar(20) [not null] // 'ACTIVE', 'TERMINATED'

created_at timestamptz
updated_at timestamptz
deleted_at timestamptz

Indexes {
// [필수] FK 조인 성능 방어
(manager_id) [name: 'idx_relation_manager']
(customer_id) [name: 'idx_relation_customer']
}

Note: '''
[중복 방지 전략 - 애플리케이션 레벨 제어]
⚠️ DB Unique Constraint 미적용

[Why?]
- Soft Delete 된 관계(과거 이력)와 신규 관계 간의 충돌 방지
- 고객이 탈퇴 후 재가입하거나, 1년 뒤 재방문하는 시나리오 유연 대응

[How?]
- Service Layer: 비관적 락(Pessimistic Lock)으로 동시성 제어
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