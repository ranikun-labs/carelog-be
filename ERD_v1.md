> 이 ERD는 `gemini.md`에 정의된 **1단계: Lean MVP (v1.0)** 의 데이터 모델입니다.

---

# **1. 사용자 및 관계 (User & Relation Core)**

Table users {
  id uuid [pk]
  email varchar [unique, not null]
  password varchar [not null] // BCrypt hashed
  name varchar
  role varchar [not null] // 'THERAPIST', 'CLIENT' 등 직접 저장
  phone_encrypted text // Encrypted using Jasypt/AES
  address_encrypted text // Encrypted using Jasypt/AES
  created_at timestamptz
}

Table relations {
  id uuid [pk]
  therapist_id uuid [ref: > users.id] // actor -> therapist로 명확화
  client_id uuid [ref: > users.id] // target -> client로 명확화
  status varchar // active, ended
  created_at timestamptz

  Indexes {
    (therapist_id, client_id) [unique]
  }
}

# **2. 핵심 기능 (Core Functionality)**

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

# **3. 필수 기반 (Essentials)**

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