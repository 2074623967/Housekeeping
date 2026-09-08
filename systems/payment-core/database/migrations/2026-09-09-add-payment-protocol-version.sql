USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_protocol_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    protocol_code VARCHAR(64) NOT NULL COMMENT '支付协议编码',
    version_no INT NOT NULL COMMENT '协议版本号',
    protocol_name VARCHAR(128) NOT NULL COMMENT '支付协议名称快照',
    protocol_type VARCHAR(64) NOT NULL COMMENT '协议类型快照',
    protocol_type_name VARCHAR(64) NOT NULL COMMENT '协议类型名称快照',
    template_code VARCHAR(64) NOT NULL COMMENT '协议模板编码快照',
    template_name VARCHAR(128) NOT NULL COMMENT '协议模板名称快照',
    template_version VARCHAR(32) NOT NULL COMMENT '模板版本快照',
    sign_mode VARCHAR(64) NOT NULL COMMENT '签约模式快照',
    sign_element_spec VARCHAR(255) NOT NULL COMMENT '签约要素快照',
    e_signature_provider VARCHAR(64) NOT NULL COMMENT '电子签章服务商快照',
    scene_scope VARCHAR(128) NOT NULL COMMENT '适用场景快照',
    channel_scope VARCHAR(128) NOT NULL COMMENT '适用渠道范围快照',
    merchant_ack_required VARCHAR(32) NOT NULL COMMENT '商户确认要求快照',
    risk_control_tag VARCHAR(128) NOT NULL COMMENT '风控标签快照',
    protocol_body TEXT NOT NULL COMMENT '协议正文快照',
    status VARCHAR(32) NOT NULL COMMENT '协议状态快照',
    status_type VARCHAR(32) NOT NULL COMMENT '协议状态样式快照',
    priority INT NOT NULL COMMENT '协议优先级快照',
    change_type VARCHAR(32) NOT NULL COMMENT '变更类型',
    source_version_no INT NULL COMMENT '回滚来源版本号',
    change_reason VARCHAR(255) NOT NULL COMMENT '变更原因',
    created_at DATETIME NOT NULL COMMENT '版本创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_protocol_version (protocol_code, version_no),
    KEY idx_protocol_version_created (protocol_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付协议不可变版本快照表';

INSERT INTO t_payment_protocol_version (
    protocol_code, version_no, protocol_name, protocol_type, protocol_type_name,
    template_code, template_name, template_version, sign_mode, sign_element_spec,
    e_signature_provider, scene_scope, channel_scope, merchant_ack_required,
    risk_control_tag, protocol_body, status, status_type, priority, change_type,
    source_version_no, change_reason, created_at
)
SELECT config.protocol_code, 1, config.protocol_name, config.protocol_type, config.protocol_type_name,
       config.template_code, config.template_name, config.template_version, config.sign_mode, config.sign_element_spec,
       config.e_signature_provider, config.scene_scope, config.channel_scope, config.merchant_ack_required,
       config.risk_control_tag, config.protocol_body, config.status, config.status_type, config.priority, 'BASELINE',
       NULL, '存量协议初始化基线', config.updated_at
FROM t_payment_protocol_config config
WHERE NOT EXISTS (
    SELECT 1
    FROM t_payment_protocol_version version
    WHERE version.protocol_code = config.protocol_code
);
