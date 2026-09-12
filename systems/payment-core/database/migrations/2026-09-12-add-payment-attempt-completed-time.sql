ALTER TABLE t_payment_attempt
    ADD COLUMN completed_at DATETIME NULL DEFAULT NULL COMMENT '支付尝试终态时间'
        AFTER attempt_status_type;
