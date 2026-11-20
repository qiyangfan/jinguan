-- refresh 会话:jti 落库以支持轮换、重用检测与全量吊销。
CREATE TABLE refresh_sessions (
    jti        VARCHAR(64) PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP   NOT NULL,
    consumed   BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_sessions_user ON refresh_sessions(user_id);
