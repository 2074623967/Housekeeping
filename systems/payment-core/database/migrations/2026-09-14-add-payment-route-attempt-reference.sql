ALTER TABLE t_payment_route_record
    ADD COLUMN attempt_no VARCHAR(64) DEFAULT NULL COMMENT '关联支付尝试号，存量路由可为空' AFTER route_no,
    ADD KEY idx_route_attempt_no (attempt_no);

UPDATE t_payment_route_record prr
SET prr.attempt_no = (
    SELECT pa.attempt_no
    FROM t_payment_attempt pa
    WHERE pa.payment_order_id = prr.payment_order_id
      AND pa.created_at <= prr.created_at
    ORDER BY pa.created_at DESC, pa.id DESC
    LIMIT 1
)
WHERE prr.attempt_no IS NULL;
