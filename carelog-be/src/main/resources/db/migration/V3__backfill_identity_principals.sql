-- 로그인 자격증명이 있는 MANAGER만 Platform Account/Password Credential로 Backfill한다.
-- CUSTOMER는 사업자가 관리하는 CRM 고객이며 Platform Account가 아니므로 제외한다
-- (User 엔티티 불변식상 role=MANAGER만 userId/email/password가 필수이므로 조건은 방어적 필터다).

INSERT INTO platform_accounts (id, status, primary_email, created_at, updated_at)
SELECT
    u.public_id,
    CASE WHEN u.deleted_at IS NULL THEN 'ACTIVE' ELSE 'INACTIVE' END,
    u.email,
    u.created_at,
    u.updated_at
FROM users u
WHERE u.role = 'MANAGER'
  AND u.user_id IS NOT NULL
  AND u.password IS NOT NULL;

INSERT INTO password_credentials (account_id, login_id, password_hash, created_at, updated_at)
SELECT
    u.public_id,
    u.user_id,
    u.password,
    u.created_at,
    u.updated_at
FROM users u
WHERE u.role = 'MANAGER'
  AND u.user_id IS NOT NULL
  AND u.password IS NOT NULL;

UPDATE users u
SET account_id = u.public_id
WHERE u.role = 'MANAGER'
  AND u.user_id IS NOT NULL
  AND u.password IS NOT NULL;
