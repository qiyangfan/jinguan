-- 锦官 V6:金额基座对齐 muniu credit 制(ADR-0006/metering.md:1 credit = 本币 1e-9)——
-- 字段名(amount_micros→amount_credits 等)与数值(μ=1e-6 → credit=1e-9,×1000)同时切换。
-- 依据:跨仓计量契约 muniu store.UsageRow.CostCredits 为 1e-9 本币;μ 基座对廉价档
-- (如 $0.003/百万Token = 3 credit/请求)无法整表达,逐段 ceil 产生 1000× 量级失真。
-- 一次性单位换算迁移(V2 同型);此后 ledger 数值与 muniu cost_credits 同刻度 1:1。

ALTER TABLE ledger_entries RENAME COLUMN amount_micros TO amount_credits;
UPDATE ledger_entries SET amount_credits = amount_credits * 1000;

ALTER TABLE mock_payment_orders RENAME COLUMN amount_micros TO amount_credits;
UPDATE mock_payment_orders SET amount_credits = amount_credits * 1000;

ALTER TABLE pricing_entries RENAME COLUMN input_micros TO input_credits;
UPDATE pricing_entries SET input_credits = input_credits * 1000;
ALTER TABLE pricing_entries RENAME COLUMN cached_input_micros TO cached_input_credits;
UPDATE pricing_entries SET cached_input_credits = cached_input_credits * 1000;
ALTER TABLE pricing_entries RENAME COLUMN output_micros TO output_credits;
UPDATE pricing_entries SET output_credits = output_credits * 1000;

ALTER TABLE usage_records RENAME COLUMN cost_micros TO cost_credits;
UPDATE usage_records SET cost_credits = cost_credits * 1000;
