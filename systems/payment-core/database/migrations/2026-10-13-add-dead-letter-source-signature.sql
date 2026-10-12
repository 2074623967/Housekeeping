ALTER TABLE t_payment_dead_letter_task
    ADD COLUMN source_signature_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '原始消息来源签名已验证' AFTER header_snapshot;
