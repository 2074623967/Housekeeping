USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_gateway_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    gateway_code VARCHAR(64) NOT NULL COMMENT '支付网关编码',
    version_no INT NOT NULL COMMENT '不可变版本号',
    gateway_name VARCHAR(128) NOT NULL COMMENT '支付网关名称快照',
    access_mode VARCHAR(64) NOT NULL COMMENT '接入模式快照',
    channel_scope VARCHAR(256) NOT NULL COMMENT '适用渠道范围快照',
    environment_scope VARCHAR(128) NOT NULL COMMENT '环境范围快照',
    api_base_url VARCHAR(256) NOT NULL COMMENT '网关基础地址快照',
    protocol_type VARCHAR(64) NOT NULL COMMENT '报文协议快照',
    sign_algorithm VARCHAR(64) NOT NULL COMMENT '签名算法快照',
    certificate_alias VARCHAR(128) NOT NULL COMMENT '证书别名快照',
    certificate_status VARCHAR(32) NOT NULL COMMENT '证书状态快照',
    certificate_status_type VARCHAR(32) NOT NULL COMMENT '证书状态样式快照',
    release_stage VARCHAR(64) NOT NULL COMMENT '发布阶段快照',
    gray_strategy VARCHAR(256) NOT NULL COMMENT '灰度策略快照',
    callback_whitelist VARCHAR(256) NOT NULL COMMENT '回调白名单快照',
    adapter_registry VARCHAR(256) NOT NULL COMMENT '适配器编排快照',
    timeout_ms INT NOT NULL COMMENT '超时时间快照',
    retry_policy VARCHAR(128) NOT NULL COMMENT '重试策略快照',
    status VARCHAR(32) NOT NULL COMMENT '网关状态快照',
    status_type VARCHAR(32) NOT NULL COMMENT '网关状态样式快照',
    priority INT NOT NULL COMMENT '优先级快照',
    change_type VARCHAR(32) NOT NULL COMMENT '版本变更类型',
    source_version_no INT DEFAULT NULL COMMENT '回滚来源版本号',
    change_reason VARCHAR(255) NOT NULL COMMENT '变更原因',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gateway_version (gateway_code, version_no),
    KEY idx_gateway_version_created (gateway_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付网关不可变版本表';

SET @add_gateway_base_version_no_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_gateway_review ADD COLUMN base_version_no INT DEFAULT NULL COMMENT ''提交时当前版本号，新增时为空'' AFTER gateway_code',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_gateway_review'
      AND COLUMN_NAME = 'base_version_no'
);
PREPARE add_gateway_base_version_no_stmt FROM @add_gateway_base_version_no_sql;
EXECUTE add_gateway_base_version_no_stmt;
DEALLOCATE PREPARE add_gateway_base_version_no_stmt;

SET @add_gateway_source_version_no_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_gateway_review ADD COLUMN source_version_no INT DEFAULT NULL COMMENT ''回滚目标历史版本号'' AFTER base_version_no',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_gateway_review'
      AND COLUMN_NAME = 'source_version_no'
);
PREPARE add_gateway_source_version_no_stmt FROM @add_gateway_source_version_no_sql;
EXECUTE add_gateway_source_version_no_stmt;
DEALLOCATE PREPARE add_gateway_source_version_no_stmt;

ALTER TABLE t_payment_gateway_review
    MODIFY COLUMN operation_type VARCHAR(32) NOT NULL
        COMMENT 'CREATE/UPDATE/STATUS_CHANGE/ROLLBACK';

INSERT INTO t_payment_gateway_version (
    gateway_code, version_no, gateway_name, access_mode, channel_scope, environment_scope,
    api_base_url, protocol_type, sign_algorithm, certificate_alias, certificate_status,
    certificate_status_type, release_stage, gray_strategy, callback_whitelist,
    adapter_registry, timeout_ms, retry_policy, status, status_type, priority,
    change_type, source_version_no, change_reason, created_at
)
SELECT gateway.gateway_code, 1, gateway.gateway_name, gateway.access_mode,
       gateway.channel_scope, gateway.environment_scope, gateway.api_base_url,
       gateway.protocol_type, gateway.sign_algorithm, gateway.certificate_alias,
       gateway.certificate_status, gateway.certificate_status_type, gateway.release_stage,
       gateway.gray_strategy, gateway.callback_whitelist, gateway.adapter_registry,
       gateway.timeout_ms, gateway.retry_policy, gateway.status, gateway.status_type,
       gateway.priority, 'BASELINE', NULL, '存量支付网关初始化基线', gateway.updated_at
FROM t_payment_gateway_config gateway
WHERE NOT EXISTS (
    SELECT 1
    FROM t_payment_gateway_version version
    WHERE version.gateway_code = gateway.gateway_code
);

UPDATE t_payment_gateway_review review
SET review.base_version_no = (
    SELECT MAX(version.version_no)
    FROM t_payment_gateway_version version
    WHERE version.gateway_code = review.gateway_code
)
WHERE review.review_status = 'PENDING_REVIEW'
  AND review.operation_type <> 'CREATE'
  AND review.base_version_no IS NULL;
