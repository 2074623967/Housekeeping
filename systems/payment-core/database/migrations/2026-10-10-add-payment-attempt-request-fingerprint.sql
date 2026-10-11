ALTER TABLE t_payment_attempt
    ADD COLUMN request_fingerprint VARCHAR(64) DEFAULT NULL COMMENT '规范提交请求摘要；历史支付尝试为空' AFTER idempotency_key;
