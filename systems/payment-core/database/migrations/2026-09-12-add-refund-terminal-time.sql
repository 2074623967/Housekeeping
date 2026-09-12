-- payment-core 退款终态发生时间
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

ALTER TABLE t_refund_order
    ADD COLUMN terminal_at DATETIME NULL DEFAULT NULL COMMENT '退款终态时间' AFTER success_at,
    ADD INDEX idx_refund_status_terminal (status, terminal_at);

UPDATE t_refund_order
SET terminal_at = success_at
WHERE status = 'SUCCESS'
  AND terminal_at IS NULL
  AND success_at IS NOT NULL;

UPDATE t_refund_order refund_order
    INNER JOIN t_refund_attempt latest_attempt
        ON latest_attempt.refund_order_id = refund_order.refund_order_id
       AND latest_attempt.id = (
           SELECT candidate.id
           FROM t_refund_attempt candidate
           WHERE candidate.refund_order_id = refund_order.refund_order_id
           ORDER BY candidate.created_at DESC, candidate.id DESC
           LIMIT 1
       )
SET refund_order.terminal_at = latest_attempt.completed_at
WHERE refund_order.status IN ('SUCCESS', 'FAIL')
  AND refund_order.terminal_at IS NULL
  AND latest_attempt.attempt_status = refund_order.status
  AND latest_attempt.completed_at IS NOT NULL;
