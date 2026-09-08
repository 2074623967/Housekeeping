USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_channel_query_rate_bucket (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    window_started_at DATETIME NOT NULL COMMENT '当前时间窗开始时间',
    used_count INT NOT NULL DEFAULT 0 COMMENT '当前时间窗已占用查单次数',
    updated_at DATETIME NOT NULL COMMENT '最近更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_query_rate_bucket (channel_code),
    KEY idx_channel_query_rate_window (window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付渠道查单共享限流桶';
