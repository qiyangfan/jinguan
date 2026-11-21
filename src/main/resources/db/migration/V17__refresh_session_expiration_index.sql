-- refresh 会话按 expires_at 周期清理;无索引会随表增长退化为全表扫描。
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_expires ON refresh_sessions(expires_at);
