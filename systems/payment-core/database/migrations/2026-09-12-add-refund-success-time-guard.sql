ALTER TABLE t_refund_order
    ADD INDEX idx_refund_status_applied (status, applied_at),
    ADD INDEX idx_refund_status_success (status, success_at);

-- Historical batches did not collect refund success-time completeness.
ALTER TABLE t_payment_day_end_batch
    ADD COLUMN missing_refund_success_time_count INT NULL DEFAULT NULL COMMENT '退款成功时间缺失数' AFTER refund_success_amount;
