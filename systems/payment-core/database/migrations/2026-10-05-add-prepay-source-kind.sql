USE housekeeping_payment_core;

ALTER TABLE t_prepay_order
    ADD COLUMN source_kind VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '创建时订单来源类型' AFTER payment_order_id;
