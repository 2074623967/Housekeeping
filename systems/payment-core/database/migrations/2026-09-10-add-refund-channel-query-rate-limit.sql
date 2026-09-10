USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_refund_channel_query_rate_bucket (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    window_started_at DATETIME NOT NULL COMMENT '当前时间窗开始时间',
    used_count INT NOT NULL DEFAULT 0 COMMENT '当前时间窗已占用查单次数',
    updated_at DATETIME NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_query_rate_bucket (channel_code),
    KEY idx_refund_query_rate_window (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款渠道查单共享限流桶';

CREATE TABLE IF NOT EXISTS t_refund_channel_query_rate_limit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rejection_no VARCHAR(64) NOT NULL COMMENT '限流拒绝流水号',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    refund_order_id VARCHAR(64) NOT NULL COMMENT '退款单号',
    window_seconds INT NOT NULL COMMENT '限流窗口秒数',
    max_queries INT NOT NULL COMMENT '渠道窗口最大查单次数',
    rejected_at DATETIME NOT NULL COMMENT '拒绝时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_query_rate_rejection_no (rejection_no),
    KEY idx_refund_query_rate_rejected_at (rejected_at),
    KEY idx_refund_query_rate_channel_time (channel_code, rejected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款渠道查单限流拒绝日志';
