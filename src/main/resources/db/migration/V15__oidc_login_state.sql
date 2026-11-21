-- OIDC RP 一次性登录 state：state 只存哈希，含 PKCE verifier 与 nonce，短 TTL。
CREATE TABLE oidc_login_states (
    state_hash    VARCHAR(64) PRIMARY KEY,
    nonce         VARCHAR(128) NOT NULL,
    code_verifier VARCHAR(128) NOT NULL,
    return_to     VARCHAR(512) NULL,
    expires_at    TIMESTAMP   NOT NULL,
    consumed_at   TIMESTAMP   NULL
);
