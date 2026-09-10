-- payment-core 退款渠道受理事实
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

ALTER TABLE t_refund_order
    ADD COLUMN channel_code VARCHAR(64) NULL COMMENT '实际受理退款的渠道编码' AFTER refund_reason,
    ADD COLUMN channel_refund_no VARCHAR(128) NULL COMMENT '渠道侧退款单号' AFTER channel_code,
    ADD COLUMN channel_submitted_at DATETIME NULL COMMENT '首次获得渠道受理号时间' AFTER channel_refund_no,
    ADD UNIQUE KEY uk_refund_channel_no (channel_code, channel_refund_no);
