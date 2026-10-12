ALTER TABLE t_refund_order
    ADD COLUMN terminal_at DATETIME NULL COMMENT '终态时间' AFTER success_at;
