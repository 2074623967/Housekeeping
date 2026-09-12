-- payment-core 退款查单日志关联退款尝试
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

ALTER TABLE t_refund_operation_log
    ADD COLUMN refund_attempt_no VARCHAR(64) DEFAULT NULL
        COMMENT '关联退款渠道尝试号，非渠道动作为空' AFTER refund_order_id,
    ADD KEY idx_refund_operation_attempt_action_created (
        refund_attempt_no,
        action_code,
        created_at
    );

-- 仅回填只有一轮尝试的历史退款，避免为多轮尝试伪造查单日志归属。
UPDATE t_refund_operation_log operation_log
    INNER JOIN (
        SELECT refund_order_id,
               MIN(refund_attempt_no) AS refund_attempt_no
        FROM t_refund_attempt
        GROUP BY refund_order_id
        HAVING COUNT(*) = 1
    ) sole_attempt
        ON sole_attempt.refund_order_id = operation_log.refund_order_id
SET operation_log.refund_attempt_no = sole_attempt.refund_attempt_no
WHERE operation_log.action_code IN (
    'CHANNEL_SUBMIT',
    'CHANNEL_SUBMIT_FAILED',
    'CHANNEL_SUBMIT_UNKNOWN',
    'CHANNEL_QUERY',
    'CHANNEL_QUERY_SUCCESS',
    'CHANNEL_QUERY_FAIL',
    'CHANNEL_QUERY_FAILED',
    'CHANNEL_ACCEPTANCE_RECOVERED',
    'SUCCESS',
    'FAIL'
)
  AND operation_log.refund_attempt_no IS NULL;
