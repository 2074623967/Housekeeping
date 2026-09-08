USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_route_rule_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '路由规则编码',
    version_no INT NOT NULL COMMENT '规则版本号',
    rule_name VARCHAR(128) NOT NULL COMMENT '路由规则名称快照',
    match_scene VARCHAR(128) NOT NULL COMMENT '匹配业务场景快照',
    match_expression VARCHAR(512) NOT NULL COMMENT '匹配表达式快照',
    target_channel_code VARCHAR(64) NOT NULL COMMENT '目标渠道编码快照',
    fallback_channel_code VARCHAR(64) NOT NULL COMMENT '兜底渠道编码快照',
    status VARCHAR(32) NOT NULL COMMENT '规则状态快照',
    status_type VARCHAR(32) NOT NULL COMMENT '规则状态样式类型快照',
    priority INT NOT NULL COMMENT '规则优先级快照',
    change_type VARCHAR(32) NOT NULL COMMENT '变更类型',
    source_version_no INT NULL COMMENT '回滚来源版本号',
    change_reason VARCHAR(255) NOT NULL COMMENT '变更原因',
    created_at DATETIME NOT NULL COMMENT '版本创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_rule_version (rule_code, version_no),
    KEY idx_route_rule_version_created (rule_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付路由规则版本快照表';

INSERT INTO t_payment_route_rule_version (
    rule_code,
    version_no,
    rule_name,
    match_scene,
    match_expression,
    target_channel_code,
    fallback_channel_code,
    status,
    status_type,
    priority,
    change_type,
    source_version_no,
    change_reason,
    created_at
)
SELECT config.rule_code,
       1,
       config.rule_name,
       config.match_scene,
       config.match_expression,
       config.target_channel_code,
       config.fallback_channel_code,
       config.status,
       config.status_type,
       config.priority,
       'BASELINE',
       NULL,
       '迁移存量规则基线',
       config.updated_at
FROM t_payment_route_rule_config config
WHERE NOT EXISTS (
    SELECT 1
    FROM t_payment_route_rule_version version
    WHERE version.rule_code = config.rule_code
);
