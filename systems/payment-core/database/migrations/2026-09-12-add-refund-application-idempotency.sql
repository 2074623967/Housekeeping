-- payment-core 退款申请客户端幂等增量脚本
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

ALTER TABLE t_refund_order
    ADD COLUMN idempotency_key VARCHAR(128) NULL COMMENT '客户端退款申请幂等键' AFTER payment_order_id;

UPDATE t_refund_order
SET idempotency_key = CONCAT('LEGACY:', refund_order_id)
WHERE idempotency_key IS NULL;

ALTER TABLE t_refund_order
    MODIFY COLUMN idempotency_key VARCHAR(128) NOT NULL COMMENT '客户端退款申请幂等键',
    ADD UNIQUE KEY uk_refund_apply_idempotency (payment_order_id, idempotency_key);
