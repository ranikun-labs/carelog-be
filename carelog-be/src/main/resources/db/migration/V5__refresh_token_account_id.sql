-- Refresh Token 세션의 공식 조회 키를 loginId(user_id) 에서 accountId 로 전환하기 위한 additive 컬럼.
-- 기존 user_id 컬럼은 제거하지 않는다(과거 데이터 보존). 다만 더 이상 조회 키로 쓰이지 않고,
-- 향후 OAuth 계정(로그인 loginId 자체가 없음)도 이 테이블에 세션을 만들어야 하므로 NOT NULL을 완화한다.

ALTER TABLE refresh_token ADD COLUMN account_id uuid;

-- password_credentials.login_id(가입 시 저장된 loginId)로 refresh_token.user_id를 역추적해 account_id를 백필한다.
UPDATE refresh_token rt
SET account_id = pc.account_id
FROM password_credentials pc
WHERE rt.user_id = pc.login_id;

-- B0 cut-over 후 구 loginId subject Token은 모두 거부된다. accountId로 복구할 수 없는 과거 세션은
-- 재로그인 대상이므로 삭제하고, 이후 신규 세션의 accountId 필수 불변식을 DB에도 강제한다.
DELETE FROM refresh_token WHERE account_id IS NULL;

ALTER TABLE refresh_token ALTER COLUMN account_id SET NOT NULL;

ALTER TABLE refresh_token
    ADD CONSTRAINT fk_refresh_token_account
    FOREIGN KEY (account_id) REFERENCES platform_accounts(id);

CREATE INDEX idx_refresh_token_account_id ON refresh_token(account_id);

-- OAuth 계정 등 loginId가 없는 Principal도 세션을 가질 수 있도록 레거시 컬럼의 NOT NULL을 해제한다.
ALTER TABLE refresh_token ALTER COLUMN user_id DROP NOT NULL;
