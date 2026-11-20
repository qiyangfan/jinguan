-- 账号禁用状态:独立于欠费停服(suspended_at);禁用后拒绝登录与续期。
ALTER TABLE users ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT FALSE;
