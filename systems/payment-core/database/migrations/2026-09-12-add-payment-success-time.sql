ALTER TABLE t_payment_order
    ADD COLUMN success_at DATETIME NULL DEFAULT NULL COMMENT '支付成功时间' AFTER status_type,
    ADD INDEX idx_payment_status_success (status, success_at);

-- Only backfill from local facts that explicitly represent payment success.
UPDATE t_payment_order payment_order
JOIN (
    SELECT payment_order_id, MIN(fact_at) AS success_at
    FROM (
        SELECT payment_order_id, created_at AS fact_at
        FROM t_payment_notify_log
        WHERE notify_type = 'SUCCESS'
          AND notify_status IN ('已收口', 'SUCCESS')
        UNION ALL
        SELECT payment_order_id, created_at AS fact_at
        FROM t_payment_event
        WHERE event_type = 'PAYMENT_SUCCESS'
    ) success_fact
    GROUP BY payment_order_id
) evidence ON evidence.payment_order_id = payment_order.payment_order_id
SET payment_order.success_at = evidence.success_at
WHERE payment_order.status = 'SUCCESS'
  AND payment_order.success_at IS NULL;

-- Historical day-end batches did not collect this completeness fact.
ALTER TABLE t_payment_day_end_batch
    ADD COLUMN missing_payment_success_time_count INT NULL DEFAULT NULL COMMENT '支付成功时间缺失数' AFTER pending_payment_amount;
