ALTER TABLE t_refund_order
    ADD COLUMN channel_code VARCHAR(64) NULL COMMENT '原支付渠道快照' AFTER payment_order_id;

ALTER TABLE t_refund_order
    ADD UNIQUE KEY uk_refund_channel_identity (channel_code, channel_refund_id);
