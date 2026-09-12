-- payment-core 日终退款失败事实快照
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

ALTER TABLE t_payment_day_end_batch
    ADD COLUMN refund_fail_count INT NULL DEFAULT NULL COMMENT '退款失败单量'
        AFTER refund_success_amount,
    ADD COLUMN refund_fail_amount DECIMAL(18, 2) NULL DEFAULT NULL COMMENT '退款失败金额'
        AFTER refund_fail_count;
