-- payment-core 统一支付记录成功退款聚合索引
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

CREATE INDEX idx_refund_payment_status
    ON t_refund_order (payment_order_id, status);
