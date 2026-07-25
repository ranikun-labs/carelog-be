-- Identity Foundation 제약조건 확정.
-- 이 단계에서 실패하면 (예: 중복 login_id) Flyway가 전체 Migration을 중단시킨다 — 의도된 동작이다.

-- password_credentials.account_id는 이미 PK(V2)라 uniqueness가 보장된다.
ALTER TABLE password_credentials
    ADD CONSTRAINT password_credentials_login_id_key UNIQUE (login_id);

ALTER TABLE password_credentials
    ADD CONSTRAINT fk_password_credentials_account
    FOREIGN KEY (account_id) REFERENCES platform_accounts(id);

ALTER TABLE external_identities
    ADD CONSTRAINT external_identities_provider_subject_key UNIQUE (provider, provider_subject);

ALTER TABLE external_identities
    ADD CONSTRAINT fk_external_identities_account
    FOREIGN KEY (account_id) REFERENCES platform_accounts(id);

CREATE INDEX idx_external_identities_account_id ON external_identities(account_id);

-- CUSTOMER는 account_id가 null이므로 nullable FK로 둔다 (전역 NOT NULL 금지).
ALTER TABLE users
    ADD CONSTRAINT fk_users_account
    FOREIGN KEY (account_id) REFERENCES platform_accounts(id);

CREATE INDEX idx_users_account_id ON users(account_id);
