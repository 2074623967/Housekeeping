-- Apply once to an existing MySQL 8 clearing database before enabling AMQP dispatch.
ALTER TABLE t_clearing_event
    ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '出站投递状态' AFTER event_status,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '投递重试次数' AFTER publish_status,
    ADD COLUMN last_published_at DATETIME DEFAULT NULL COMMENT '最近投递时间' AFTER retry_count,
    ADD COLUMN next_retry_at DATETIME DEFAULT NULL COMMENT '下次重试时间' AFTER last_published_at,
    ADD KEY idx_publish_status (publish_status);
