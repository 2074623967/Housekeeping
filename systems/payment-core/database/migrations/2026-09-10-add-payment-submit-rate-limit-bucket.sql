-- payment-core 支付提交共享限流桶增量脚本
-- 目标库：MySQL 8.x

USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_submit_rate_limit_bucket (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scope_type VARCHAR(32) NOT NULL COMMENT '限流范围类型',
    scope_key CHAR(64) NOT NULL COMMENT '限流维度SHA-256摘要',
    window_started_at DATETIME NOT NULL COMMENT '当前固定时间窗开始时间',
    used_count INT NOT NULL DEFAULT 0 COMMENT '当前时间窗已占用提交次数',
    updated_at DATETIME NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_submit_rate_limit_scope (scope_type, scope_key),
    KEY idx_submit_rate_limit_window (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付提交共享限流桶';
