USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_issue_alert_delivery_rate_bucket (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    provider_code VARCHAR(64) NOT NULL COMMENT '告警供应商编码',
    channel_code VARCHAR(64) NOT NULL COMMENT '告警通道编码',
    window_started_at DATETIME NOT NULL COMMENT '当前固定时间窗开始时间',
    used_count INT NOT NULL DEFAULT 0 COMMENT '当前时间窗已占用派发次数',
    updated_at DATETIME NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_alert_delivery_rate_bucket (provider_code, channel_code),
    KEY idx_issue_alert_delivery_rate_window (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警供应商派发共享限流桶';
