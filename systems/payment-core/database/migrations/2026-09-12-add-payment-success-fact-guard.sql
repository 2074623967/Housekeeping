ALTER TABLE t_payment_day_end_batch
    ADD COLUMN payment_success_fact_mismatch_count INT NULL DEFAULT NULL COMMENT '支付成功事实不一致数'
        AFTER missing_payment_success_time_count;
