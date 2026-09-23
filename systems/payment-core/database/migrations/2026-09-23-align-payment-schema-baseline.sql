-- payment-core 支付单基线 schema 对齐
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_expiry_close_retry (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '支付单号',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '连续关单失败次数',
    retry_interval_seconds INT NOT NULL COMMENT '当前退避秒数',
    next_retry_at DATETIME NOT NULL COMMENT '下次允许重试时间',
    last_error_message VARCHAR(512) NOT NULL COMMENT '最近失败摘要',
    last_failed_at DATETIME NOT NULL COMMENT '最近失败时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_expiry_close_retry_payment (payment_order_id),
    KEY idx_expiry_close_retry_due (next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付超时关单失败退避表';

ALTER TABLE t_payment_order
    MODIFY COLUMN channel_transaction_no VARCHAR(128) NULL DEFAULT NULL COMMENT '渠道侧交易号';
