-- 외부 Identity 계정은 로컬 계정 FK와 별도로 연결한다. 삭제된 행도 재사용하지 않는다.
ALTER TABLE users ADD COLUMN external_account_id uuid;
ALTER TABLE users ADD CONSTRAINT users_external_account_id_key UNIQUE (external_account_id);

-- 최초 등록 입력은 이후 사용자 프로필 변경과 분리하여 재시도 충돌을 판정한다.
CREATE TABLE external_manager_projection_receipts (
    external_account_id uuid PRIMARY KEY REFERENCES users(external_account_id),
    initial_user_id varchar(255) NOT NULL,
    initial_email varchar(255) NOT NULL,
    initial_name varchar(255) NOT NULL
);

ALTER TABLE users ADD CONSTRAINT users_external_manager_fields_check CHECK (
    external_account_id IS NULL OR
    (role = 'MANAGER' AND manager_type IS NOT NULL AND manager_type = 'PHYSICAL_THERAPIST' AND account_id IS NULL AND password IS NULL)
);
