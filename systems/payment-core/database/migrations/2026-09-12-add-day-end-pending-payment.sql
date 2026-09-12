-- Historical batches did not collect this fact. Keep them NULL so they cannot
-- be mistaken for a verified zero; a RERUN creates a complete new snapshot.
ALTER TABLE t_payment_day_end_batch
    ADD COLUMN pending_payment_count INT NULL DEFAULT NULL COMMENT '待收口支付数' AFTER payment_success_gap_amount,
    ADD COLUMN pending_payment_amount DECIMAL(18, 2) NULL DEFAULT NULL COMMENT '待收口支付金额' AFTER pending_payment_count;
