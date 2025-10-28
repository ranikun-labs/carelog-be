> 이 ERD는 `gemini.md`에 정의된 **1단계: Lean MVP (v1.0)** 의 데이터 모델입니다.

---

# **1. 사용자 및 관계 (User & Relation Core)**
````
Table users {
  id int [pk]
  user_id varchar [unique, not null]
  email varchar [unique, not null]
  password varchar [not null] // BCrypt hashed
  name varchar
  role varchar [not null] // [변경] 'MANAGER', 'CUSTOMER'로 역할 통일 제안
  phone_encrypted text // Encrypted using Jasypt/AES
  address_encrypted text // Encrypted using Jasypt/AES
  created_at timestamptz
}


// [신규] MANAGER 역할의 상세 정보를 저장하는 테이블
Table manager_profiles {
  user_id uuid [pk, ref: > users.id]
  license_number varchar // 예: 자격증 번호
  // ... 기타 MANAGER 관련 정보
}

// [신규] CUSTOMER 역할의 상세 정보를 저장하는 테이블
Table customer_profiles {
  user_id uuid [pk, ref: > users.id]
  phone_encrypted text // users 테이블에서 이동
  address_encrypted text // users 테이블에서 이동
  // ... 기타 CUSTOMER 관련 정보
}

// [변경] 'relations' 테이블의 컬럼명을 역할에 맞춰 명확하게 변경합니다.
Table relations {
  id int [pk]
  manager_id uuid [ref: > users.id] // [변경] therapist_id -> manager_id
  customer_id uuid [ref: > users.id] // [변경] client_id -> customer_id
  status varchar // active, ended
  created_at timestamptz

  Indexes {
    (manager_id, customer_id) [unique]
  }
}
````
# **2. 핵심 기능 (Core Functionality)**
````
Table relation_entries {
  id uuid [pk]
  relation_id uuid [ref: > relations.id]
  author_id uuid [ref: > users.id]
  template_id uuid [ref: > entry_templates.id]
  content json
  created_at timestamptz
}

Table entry_templates {
  id uuid [pk]
  name varchar
  fields json
  created_by uuid [ref: > users.id]
  created_at timestamptz
}

Table reservations {
  id uuid [pk]
  relation_id uuid [ref: > relations.id]
  start_time timestamptz
  end_time timestamptz
  status varchar
  created_at timestamptz

  Indexes {
    (relation_id, start_time) [unique]
  }
}
````

# **3. 필수 기반 (Essentials)**
````
Table tokens {
  id uuid [pk]
  user_id uuid [ref: > users.id]
  refresh_token varchar
  expires_at timestamptz
  created_at timestamptz
}

Table consent_records {
  id uuid [pk]
  user_id uuid [ref: > users.id]
  consent_type varchar [not null]
  version varchar [not null]
  status varchar [not null] // 'granted', 'revoked'
  granted_at timestamptz
}
````