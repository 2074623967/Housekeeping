-- payment-core 退款渠道多尝试事实
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

CREATE TABLE t_refund_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    refund_attempt_no VARCHAR(64) NOT NULL COMMENT '退款尝试号及渠道侧商户退款请求号',
    refund_order_id VARCHAR(64) NOT NULL COMMENT '关联退款单号',
    channel_code VARCHAR(64) NOT NULL COMMENT '本次尝试渠道编码',
    channel_refund_no VARCHAR(128) DEFAULT NULL COMMENT '本次尝试渠道侧退款单号',
    attempt_status VARCHAR(32) NOT NULL COMMENT '尝试状态：PENDING/FAILED/ACCEPTED/UNKNOWN/SUCCESS/FAIL',
    attempt_status_type VARCHAR(32) NOT NULL COMMENT '尝试状态样式类型',
    submitted_at DATETIME DEFAULT NULL COMMENT '首次进入渠道调用时间',
    completed_at DATETIME DEFAULT NULL COMMENT '尝试终态时间',
    created_at DATETIME NOT NULL COMMENT '尝试创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_attempt_no (refund_attempt_no),
    UNIQUE KEY uk_refund_attempt_channel_no (channel_code, channel_refund_no),
    KEY idx_refund_attempt_order_created (refund_order_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款渠道尝试事实表';

INSERT INTO t_refund_attempt(
    refund_attempt_no,
    refund_order_id,
    channel_code,
    channel_refund_no,
    attempt_status,
    attempt_status_type,
    submitted_at,
    completed_at,
    created_at
)
SELECT CONCAT('RFA', UPPER(MD5(refund.refund_order_id))),
       refund.refund_order_id,
       COALESCE(refund.channel_code, payment.channel_code, 'legacy_unknown'),
       refund.channel_refund_no,
       CASE
           WHEN refund.status = 'SUCCESS' THEN 'SUCCESS'
           WHEN refund.status = 'FAIL' THEN 'FAIL'
           WHEN refund.channel_refund_no IS NOT NULL THEN 'ACCEPTED'
           WHEN refund_submit.publish_status = 'UNKNOWN' THEN 'UNKNOWN'
           ELSE 'PENDING'
       END,
       CASE
           WHEN refund.status = 'SUCCESS' THEN 'success'
           WHEN refund.status = 'FAIL' THEN 'danger'
           ELSE 'warn'
       END,
       COALESCE(refund.channel_submitted_at, refund_submit.last_published_at),
       CASE WHEN refund.status IN ('SUCCESS', 'FAIL')
            THEN COALESCE(refund.success_at, refund.channel_submitted_at, refund.applied_at)
            ELSE NULL END,
       refund.applied_at
FROM t_refund_order refund
         LEFT JOIN t_payment_order payment
            ON payment.payment_order_id = refund.payment_order_id
         LEFT JOIN t_payment_event refund_submit ON refund_submit.id = (
            SELECT event.id
            FROM t_payment_event event
            WHERE event.event_type = 'REFUND_SUBMIT'
              AND event.biz_no = refund.refund_order_id
            ORDER BY event.id DESC
            LIMIT 1
         )
WHERE refund.status IN ('PROCESSING', 'SUCCESS', 'FAIL');

UPDATE t_payment_event event
    INNER JOIN t_refund_attempt attempt
        ON event.event_type = 'REFUND_SUBMIT'
       AND event.biz_no = attempt.refund_order_id
SET event.biz_no = attempt.refund_attempt_no;
