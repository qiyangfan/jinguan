-- break-glass 审计：只增；记录签发主体、来源 IP 与有效期。
CREATE TABLE break_glass_audit (
    id         BIGSERIAL PRIMARY KEY,
    subject    VARCHAR(64) NOT NULL,
    source_ip  VARCHAR(64) NOT NULL,
    issued_at  TIMESTAMP   NOT NULL,
    expires_at TIMESTAMP   NOT NULL
);
