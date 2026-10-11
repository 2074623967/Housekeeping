-- 支付记录的更新时间：存量支付单使用创建时间作为可核验的历史下界，后续更新自动刷新。
-- 此脚本仅用于尚无 updated_at 的旧库；受控停写、备份并检查表结构后执行一次。
ALTER TABLE t_payment_order
    ADD COLUMN updated_at DATETIME NULL DEFAULT NULL COMMENT '更新时间' AFTER created_at;

UPDATE t_payment_order
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE t_payment_order
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER created_at;
