ALTER TABLE t_payment_attempt
    ADD COLUMN gateway_code VARCHAR(64) NULL DEFAULT NULL COMMENT '渠道受理时的网关编码；历史及模拟支付为空' AFTER channel_code;
