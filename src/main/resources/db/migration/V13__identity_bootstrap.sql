-- 身份引导护栏:单行行锁序列化首注册,消除 count()==0 的并发双 ADMIN。
CREATE TABLE identity_bootstrap (
    id             SMALLINT PRIMARY KEY,
    admin_assigned BOOLEAN  NOT NULL DEFAULT FALSE
);

-- 存量库已有用户即视为管理员席位已占用;空库从 FALSE 起步,由首个注册原子占用。
INSERT INTO identity_bootstrap (id, admin_assigned)
SELECT 1, EXISTS (SELECT 1 FROM users);
