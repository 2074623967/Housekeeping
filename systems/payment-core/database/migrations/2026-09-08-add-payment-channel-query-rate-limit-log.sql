USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_channel_query_rate_limit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rejection_no VARCHAR(64) NOT NULL COMMENT '限流拒绝流水号',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '支付单号',
    window_seconds INT NOT NULL COMMENT '限流窗口秒数',
    max_queries INT NOT NULL COMMENT '渠道窗口最大查单次数',
    rejected_at DATETIME NOT NULL COMMENT '拒绝时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_query_rate_limit_rejection_no (rejection_no),
    KEY idx_query_rate_limit_rejected_at (rejected_at),
    KEY idx_query_rate_limit_channel_time (channel_code, rejected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付渠道查单限流拒绝日志';
