ALTER TABLE t_payment_event
    ADD COLUMN payment_success_http_stage VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT '支付成功事件HTTP投递阶段'
        AFTER publish_status_type;
