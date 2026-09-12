-- V2:mock 支付订单超时关闭(懒过期守门 + 定时扫描收敛)
-- 超时裁决在回调守门(expires_at 比对)与定时扫描两处共用同一列,调度延迟不影响资金正确性
ALTER TABLE mock_payment_orders ADD COLUMN expires_at TIMESTAMP NULL;
CREATE INDEX idx_mock_orders_expiry ON mock_payment_orders (status, expires_at);
