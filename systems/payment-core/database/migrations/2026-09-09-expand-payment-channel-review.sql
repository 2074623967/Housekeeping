USE housekeeping_payment_core;

ALTER TABLE t_payment_channel_review
    ADD COLUMN operation_type VARCHAR(32) NOT NULL DEFAULT 'STATUS_CHANGE' COMMENT 'CREATE/UPDATE/STATUS_CHANGE'
        AFTER channel_code,
    ADD COLUMN merchant_no VARCHAR(128) NULL COMMENT '渠道商户号候选' AFTER payment_method,
    ADD COLUMN merchant_app_id VARCHAR(128) NULL COMMENT '商户应用标识候选' AFTER merchant_no,
    ADD COLUMN callback_secret VARCHAR(256) NULL COMMENT '回调验签密钥候选，仅服务端复核使用'
        AFTER merchant_app_id,
    ADD COLUMN callback_sign_algorithm VARCHAR(32) NULL COMMENT '回调签名算法候选' AFTER callback_secret,
    ADD COLUMN callback_public_key TEXT NULL COMMENT '回调验签公钥候选，仅服务端复核使用'
        AFTER callback_sign_algorithm,
    ADD COLUMN callback_notify_url VARCHAR(256) NULL COMMENT '回调通知地址候选' AFTER callback_public_key,
    ADD COLUMN certificate_profile VARCHAR(128) NULL COMMENT '证书档案候选' AFTER callback_notify_url,
    ADD COLUMN notify_sign_window_sec INT NULL COMMENT '回调验签时间窗候选' AFTER certificate_profile,
    ADD COLUMN refund_window_days INT NULL COMMENT '原路退款时效候选' AFTER notify_sign_window_sec,
    ADD COLUMN risk_control_tag VARCHAR(128) NULL COMMENT '风控标签候选' AFTER refund_window_days,
    ADD COLUMN scene_scope VARCHAR(128) NULL COMMENT '适用场景候选' AFTER risk_control_tag,
    ADD COLUMN daily_limit DECIMAL(18, 2) NULL COMMENT '单日限额候选' AFTER scene_scope,
    ADD COLUMN priority INT NULL COMMENT '优先级候选' AFTER daily_limit,
    ADD COLUMN route_weight INT NULL COMMENT '路由权重候选' AFTER priority,
    MODIFY COLUMN base_fingerprint VARCHAR(64) NULL COMMENT '提交时完整渠道配置基线指纹，新增时为空';

UPDATE t_payment_channel_review review
JOIN t_payment_channel_config channel ON channel.channel_code = review.channel_code
SET review.merchant_no = channel.merchant_no,
    review.merchant_app_id = channel.merchant_app_id,
    review.callback_secret = channel.callback_secret,
    review.callback_sign_algorithm = channel.callback_sign_algorithm,
    review.callback_public_key = channel.callback_public_key,
    review.callback_notify_url = channel.callback_notify_url,
    review.certificate_profile = channel.certificate_profile,
    review.notify_sign_window_sec = channel.notify_sign_window_sec,
    review.refund_window_days = channel.refund_window_days,
    review.risk_control_tag = channel.risk_control_tag,
    review.scene_scope = channel.scene_scope,
    review.daily_limit = channel.daily_limit,
    review.priority = channel.priority,
    review.route_weight = channel.route_weight
WHERE review.operation_type = 'STATUS_CHANGE'
  AND review.merchant_no IS NULL;

ALTER TABLE t_payment_channel_review
    MODIFY COLUMN operation_type VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/STATUS_CHANGE',
    MODIFY COLUMN merchant_no VARCHAR(128) NOT NULL COMMENT '渠道商户号候选',
    MODIFY COLUMN merchant_app_id VARCHAR(128) NOT NULL COMMENT '商户应用标识候选',
    MODIFY COLUMN callback_secret VARCHAR(256) NOT NULL COMMENT '回调验签密钥候选，仅服务端复核使用',
    MODIFY COLUMN callback_sign_algorithm VARCHAR(32) NOT NULL COMMENT '回调签名算法候选',
    MODIFY COLUMN callback_notify_url VARCHAR(256) NOT NULL COMMENT '回调通知地址候选',
    MODIFY COLUMN certificate_profile VARCHAR(128) NOT NULL COMMENT '证书档案候选',
    MODIFY COLUMN notify_sign_window_sec INT NOT NULL COMMENT '回调验签时间窗候选',
    MODIFY COLUMN refund_window_days INT NOT NULL COMMENT '原路退款时效候选',
    MODIFY COLUMN risk_control_tag VARCHAR(128) NOT NULL COMMENT '风控标签候选',
    MODIFY COLUMN scene_scope VARCHAR(128) NOT NULL COMMENT '适用场景候选',
    MODIFY COLUMN daily_limit DECIMAL(18, 2) NOT NULL COMMENT '单日限额候选',
    MODIFY COLUMN priority INT NOT NULL COMMENT '优先级候选',
    MODIFY COLUMN route_weight INT NOT NULL COMMENT '路由权重候选';
