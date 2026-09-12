ALTER TABLE t_payment_day_end_batch
    ADD COLUMN refund_terminal_fact_mismatch_count INT NULL DEFAULT NULL COMMENT '退款终态事实不一致数'
        AFTER missing_refund_success_time_count;
