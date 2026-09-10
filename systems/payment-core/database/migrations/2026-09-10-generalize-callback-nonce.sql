ALTER TABLE t_payment_callback_nonce
    CHANGE COLUMN payment_order_id biz_no VARCHAR(64) NOT NULL COMMENT '支付单号或退款单号',
    ADD COLUMN biz_type VARCHAR(16) NOT NULL DEFAULT 'PAYMENT' COMMENT '业务类型：PAYMENT/REFUND' AFTER nonce,
    ADD KEY idx_callback_nonce_biz (biz_type, biz_no);

ALTER TABLE t_payment_callback_nonce
    COMMENT = '支付与退款回调防重放随机串表';
