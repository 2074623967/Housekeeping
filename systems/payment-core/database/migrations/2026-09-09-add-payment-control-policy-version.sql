USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_control_policy_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    source_app_id VARCHAR(64) NOT NULL COMMENT '来源应用标识',
    version_no INT NOT NULL COMMENT '不可变版本号',
    source_app_name VARCHAR(128) NOT NULL COMMENT '来源应用名称快照',
    allowed_payment_methods VARCHAR(255) NOT NULL COMMENT '允许支付方式快照',
    allowed_channel_codes VARCHAR(255) NOT NULL COMMENT '允许渠道快照',
    allowed_merchant_nos VARCHAR(255) NOT NULL COMMENT '允许商户号快照',
    minute_submit_limit INT NOT NULL COMMENT '分钟级提交限流快照',
    interface_minute_submit_limit INT NOT NULL COMMENT '接口级分钟限流快照',
    token_auth_required VARCHAR(32) NOT NULL COMMENT '令牌鉴权开关快照',
    access_token_value VARCHAR(128) NOT NULL COMMENT '接口访问令牌快照，不对管理查询返回',
    strict_mode VARCHAR(32) NOT NULL COMMENT '严格模式快照',
    status VARCHAR(32) NOT NULL COMMENT '策略状态快照',
    status_type VARCHAR(32) NOT NULL COMMENT '策略状态样式快照',
    change_type VARCHAR(32) NOT NULL COMMENT '版本变更类型',
    source_version_no INT DEFAULT NULL COMMENT '回滚来源版本号',
    change_reason VARCHAR(255) NOT NULL COMMENT '变更原因',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_control_policy_version (source_app_id, version_no),
    KEY idx_control_policy_version_created (source_app_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付控制策略不可变版本表';

SET @add_base_version_no_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_control_policy_review ADD COLUMN base_version_no INT DEFAULT NULL COMMENT ''提交时当前版本号，新增时为空'' AFTER source_app_id',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_control_policy_review'
      AND COLUMN_NAME = 'base_version_no'
);
PREPARE add_base_version_no_stmt FROM @add_base_version_no_sql;
EXECUTE add_base_version_no_stmt;
DEALLOCATE PREPARE add_base_version_no_stmt;

SET @add_source_version_no_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE t_payment_control_policy_review ADD COLUMN source_version_no INT DEFAULT NULL COMMENT ''回滚目标历史版本号'' AFTER base_version_no',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_payment_control_policy_review'
      AND COLUMN_NAME = 'source_version_no'
);
PREPARE add_source_version_no_stmt FROM @add_source_version_no_sql;
EXECUTE add_source_version_no_stmt;
DEALLOCATE PREPARE add_source_version_no_stmt;

ALTER TABLE t_payment_control_policy_review
    MODIFY COLUMN operation_type VARCHAR(32) NOT NULL
        COMMENT 'CREATE/UPDATE/STATUS_CHANGE/ROLLBACK';

INSERT INTO t_payment_control_policy_version (
    source_app_id, version_no, source_app_name, allowed_payment_methods,
    allowed_channel_codes, allowed_merchant_nos, minute_submit_limit,
    interface_minute_submit_limit, token_auth_required, access_token_value,
    strict_mode, status, status_type, change_type, source_version_no,
    change_reason, created_at
)
SELECT policy.source_app_id, 1, policy.source_app_name, policy.allowed_payment_methods,
       policy.allowed_channel_codes, policy.allowed_merchant_nos, policy.minute_submit_limit,
       policy.interface_minute_submit_limit, policy.token_auth_required, policy.access_token_value,
       policy.strict_mode, policy.status, policy.status_type, 'BASELINE', NULL,
       '存量支付控制策略初始化基线', policy.updated_at
FROM t_payment_control_policy policy
WHERE NOT EXISTS (
    SELECT 1
    FROM t_payment_control_policy_version version
    WHERE version.source_app_id = policy.source_app_id
);

UPDATE t_payment_control_policy_review review
SET review.base_version_no = (
    SELECT MAX(version.version_no)
    FROM t_payment_control_policy_version version
    WHERE version.source_app_id = review.source_app_id
)
WHERE review.review_status = 'PENDING_REVIEW'
  AND review.operation_type <> 'CREATE'
  AND review.base_version_no IS NULL;
