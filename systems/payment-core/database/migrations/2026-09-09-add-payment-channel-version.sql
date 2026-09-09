USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_channel_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '支付渠道编码',
    version_no INT NOT NULL COMMENT '不可变版本号',
    channel_name VARCHAR(128) NOT NULL COMMENT '支付渠道名称快照',
    payment_method VARCHAR(64) NOT NULL COMMENT '支付方式快照',
    merchant_no VARCHAR(128) NOT NULL COMMENT '渠道商户号快照',
    merchant_app_id VARCHAR(128) NOT NULL COMMENT '渠道商户应用标识快照',
    callback_secret VARCHAR(256) NOT NULL COMMENT '回调验签密钥快照，不对管理查询返回',
    callback_sign_algorithm VARCHAR(32) NOT NULL COMMENT '回调签名算法快照',
    callback_public_key TEXT NULL COMMENT '回调验签公钥快照，不对管理查询返回',
    callback_notify_url VARCHAR(256) NOT NULL COMMENT '回调通知地址快照',
    certificate_profile VARCHAR(128) NOT NULL COMMENT '证书档案快照',
    notify_sign_window_sec INT NOT NULL COMMENT '回调验签时间窗快照',
    refund_window_days INT NOT NULL COMMENT '原路退款时效快照',
    risk_control_tag VARCHAR(128) NOT NULL COMMENT '风控标签快照',
    scene_scope VARCHAR(128) NOT NULL COMMENT '适用场景快照',
    status VARCHAR(32) NOT NULL COMMENT '渠道状态快照',
    status_type VARCHAR(32) NOT NULL COMMENT '渠道状态样式快照',
    daily_limit DECIMAL(18, 2) NOT NULL COMMENT '单日限额快照',
    priority INT NOT NULL COMMENT '优先级快照',
    route_weight INT NOT NULL COMMENT '路由权重快照',
    change_type VARCHAR(32) NOT NULL COMMENT '版本变更类型',
    source_version_no INT DEFAULT NULL COMMENT '回滚来源版本号',
    change_reason VARCHAR(255) NOT NULL COMMENT '变更原因',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_version (channel_code, version_no),
    KEY idx_channel_version_created (channel_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付渠道不可变版本表';

SET @add_channel_base_version_no_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_channel_review ADD COLUMN base_version_no INT DEFAULT NULL COMMENT ''提交时当前版本号，新增时为空'' AFTER channel_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_channel_review'
      AND COLUMN_NAME = 'base_version_no'
);
PREPARE add_channel_base_version_no_stmt FROM @add_channel_base_version_no_sql;
EXECUTE add_channel_base_version_no_stmt;
DEALLOCATE PREPARE add_channel_base_version_no_stmt;

SET @add_channel_source_version_no_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_channel_review ADD COLUMN source_version_no INT DEFAULT NULL COMMENT ''回滚目标历史版本号'' AFTER base_version_no',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_channel_review'
      AND COLUMN_NAME = 'source_version_no'
);
PREPARE add_channel_source_version_no_stmt FROM @add_channel_source_version_no_sql;
EXECUTE add_channel_source_version_no_stmt;
DEALLOCATE PREPARE add_channel_source_version_no_stmt;

ALTER TABLE t_payment_channel_review
    MODIFY COLUMN operation_type VARCHAR(32) NOT NULL
        COMMENT 'CREATE/UPDATE/STATUS_CHANGE/ROLLBACK';

INSERT INTO t_payment_channel_version (
    channel_code, version_no, channel_name, payment_method, merchant_no, merchant_app_id,
    callback_secret, callback_sign_algorithm, callback_public_key, callback_notify_url,
    certificate_profile, notify_sign_window_sec, refund_window_days, risk_control_tag,
    scene_scope, status, status_type, daily_limit, priority, route_weight,
    change_type, source_version_no, change_reason, created_at
)
SELECT channel.channel_code, 1, channel.channel_name, channel.payment_method,
       channel.merchant_no, channel.merchant_app_id, channel.callback_secret,
       channel.callback_sign_algorithm, channel.callback_public_key, channel.callback_notify_url,
       channel.certificate_profile, channel.notify_sign_window_sec, channel.refund_window_days,
       channel.risk_control_tag, channel.scene_scope, channel.status, channel.status_type,
       channel.daily_limit, channel.priority, channel.route_weight,
       'BASELINE', NULL, '存量支付渠道初始化基线', channel.updated_at
FROM t_payment_channel_config channel
WHERE NOT EXISTS (
    SELECT 1
    FROM t_payment_channel_version version
    WHERE version.channel_code = channel.channel_code
);

UPDATE t_payment_channel_review review
SET review.base_version_no = (
    SELECT MAX(version.version_no)
    FROM t_payment_channel_version version
    WHERE version.channel_code = review.channel_code
)
WHERE review.review_status = 'PENDING_REVIEW'
  AND review.operation_type <> 'CREATE'
  AND review.base_version_no IS NULL;
