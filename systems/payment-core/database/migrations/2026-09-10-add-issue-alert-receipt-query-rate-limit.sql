USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_issue_alert_receipt_query_rate_bucket (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '告警通道编码',
    window_started_at DATETIME NOT NULL COMMENT '当前固定时间窗开始时间',
    used_count INT NOT NULL DEFAULT 0 COMMENT '当前时间窗已占用回执查询次数',
    updated_at DATETIME NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_alert_receipt_query_rate_bucket (channel_code),
    KEY idx_issue_alert_receipt_query_rate_window (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警供应商回执查询共享限流桶';

CREATE TABLE IF NOT EXISTS t_payment_issue_alert_receipt_query_rate_limit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rejection_no VARCHAR(64) NOT NULL COMMENT '限流拒绝流水号',
    channel_code VARCHAR(64) NOT NULL COMMENT '告警通道编码',
    alert_no VARCHAR(64) NOT NULL COMMENT '告警投递日志编号',
    window_seconds INT NOT NULL COMMENT '限流窗口秒数',
    max_queries INT NOT NULL COMMENT '通道窗口最大回执查询次数',
    rejected_at DATETIME NOT NULL COMMENT '拒绝时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_alert_receipt_query_rejection_no (rejection_no),
    KEY idx_issue_alert_receipt_query_rejected_at (rejected_at),
    KEY idx_issue_alert_receipt_query_channel_time (channel_code, rejected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警供应商回执查询限流拒绝日志';
