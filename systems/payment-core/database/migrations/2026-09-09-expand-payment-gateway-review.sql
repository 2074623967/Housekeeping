USE housekeeping_payment_core;

ALTER TABLE t_payment_gateway_review
    ADD COLUMN operation_type VARCHAR(32) NOT NULL DEFAULT 'STATUS_CHANGE'
        COMMENT 'CREATE/UPDATE/STATUS_CHANGE' AFTER gateway_code,
    ADD COLUMN channel_scope VARCHAR(256) NULL COMMENT '适用渠道范围候选' AFTER access_mode,
    ADD COLUMN environment_scope VARCHAR(128) NULL COMMENT '环境范围候选' AFTER channel_scope,
    ADD COLUMN api_base_url VARCHAR(256) NULL COMMENT '网关基础地址候选' AFTER environment_scope,
    ADD COLUMN protocol_type VARCHAR(64) NULL COMMENT '报文协议候选' AFTER api_base_url,
    ADD COLUMN sign_algorithm VARCHAR(64) NULL COMMENT '签名算法候选' AFTER protocol_type,
    ADD COLUMN certificate_alias VARCHAR(128) NULL COMMENT '证书别名候选' AFTER sign_algorithm,
    ADD COLUMN certificate_status VARCHAR(32) NULL COMMENT '证书状态候选' AFTER certificate_alias,
    ADD COLUMN certificate_status_type VARCHAR(32) NULL COMMENT '证书状态样式候选'
        AFTER certificate_status,
    ADD COLUMN release_stage VARCHAR(64) NULL COMMENT '发布阶段候选' AFTER certificate_status_type,
    ADD COLUMN gray_strategy VARCHAR(256) NULL COMMENT '灰度策略候选' AFTER release_stage,
    ADD COLUMN callback_whitelist VARCHAR(256) NULL COMMENT '回调白名单候选' AFTER gray_strategy,
    ADD COLUMN adapter_registry VARCHAR(256) NULL COMMENT '适配器编排候选' AFTER callback_whitelist,
    ADD COLUMN timeout_ms INT NULL COMMENT '超时时间候选' AFTER adapter_registry,
    ADD COLUMN retry_policy VARCHAR(128) NULL COMMENT '重试策略候选' AFTER timeout_ms,
    ADD COLUMN priority INT NULL COMMENT '优先级候选' AFTER retry_policy,
    MODIFY COLUMN base_fingerprint VARCHAR(64) NULL
        COMMENT '提交时完整网关配置基线指纹，新增时为空';

UPDATE t_payment_gateway_review review
JOIN t_payment_gateway_config gateway ON gateway.gateway_code = review.gateway_code
SET review.channel_scope = gateway.channel_scope,
    review.environment_scope = gateway.environment_scope,
    review.api_base_url = gateway.api_base_url,
    review.protocol_type = gateway.protocol_type,
    review.sign_algorithm = gateway.sign_algorithm,
    review.certificate_alias = gateway.certificate_alias,
    review.certificate_status = gateway.certificate_status,
    review.certificate_status_type = gateway.certificate_status_type,
    review.release_stage = gateway.release_stage,
    review.gray_strategy = gateway.gray_strategy,
    review.callback_whitelist = gateway.callback_whitelist,
    review.adapter_registry = gateway.adapter_registry,
    review.timeout_ms = gateway.timeout_ms,
    review.retry_policy = gateway.retry_policy,
    review.priority = gateway.priority
WHERE review.operation_type = 'STATUS_CHANGE'
  AND review.channel_scope IS NULL;

ALTER TABLE t_payment_gateway_review
    MODIFY COLUMN operation_type VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/STATUS_CHANGE',
    MODIFY COLUMN channel_scope VARCHAR(256) NOT NULL COMMENT '适用渠道范围候选',
    MODIFY COLUMN environment_scope VARCHAR(128) NOT NULL COMMENT '环境范围候选',
    MODIFY COLUMN api_base_url VARCHAR(256) NOT NULL COMMENT '网关基础地址候选',
    MODIFY COLUMN protocol_type VARCHAR(64) NOT NULL COMMENT '报文协议候选',
    MODIFY COLUMN sign_algorithm VARCHAR(64) NOT NULL COMMENT '签名算法候选',
    MODIFY COLUMN certificate_alias VARCHAR(128) NOT NULL COMMENT '证书别名候选',
    MODIFY COLUMN certificate_status VARCHAR(32) NOT NULL COMMENT '证书状态候选',
    MODIFY COLUMN certificate_status_type VARCHAR(32) NOT NULL COMMENT '证书状态样式候选',
    MODIFY COLUMN release_stage VARCHAR(64) NOT NULL COMMENT '发布阶段候选',
    MODIFY COLUMN gray_strategy VARCHAR(256) NOT NULL COMMENT '灰度策略候选',
    MODIFY COLUMN callback_whitelist VARCHAR(256) NOT NULL COMMENT '回调白名单候选',
    MODIFY COLUMN adapter_registry VARCHAR(256) NOT NULL COMMENT '适配器编排候选',
    MODIFY COLUMN timeout_ms INT NOT NULL COMMENT '超时时间候选',
    MODIFY COLUMN retry_policy VARCHAR(128) NOT NULL COMMENT '重试策略候选',
    MODIFY COLUMN priority INT NOT NULL COMMENT '优先级候选';

ALTER TABLE t_payment_gateway_review COMMENT = '支付网关配置变更复核申请表';
