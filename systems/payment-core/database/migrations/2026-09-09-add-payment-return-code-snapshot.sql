USE housekeeping_payment_core;

ALTER TABLE t_payment_order
    ADD COLUMN last_channel_return_code VARCHAR(64) NULL COMMENT '最近渠道原始返回码' AFTER channel_transaction_no,
    ADD COLUMN last_standardized_code VARCHAR(64) NULL COMMENT '最近平台标准错误码' AFTER last_channel_return_code,
    ADD COLUMN last_standardized_message VARCHAR(256) NULL COMMENT '最近平台标准错误文案' AFTER last_standardized_code,
    ADD COLUMN last_handling_suggestion VARCHAR(256) NULL COMMENT '最近运营处理建议' AFTER last_standardized_message,
    ADD COLUMN last_retryable VARCHAR(16) NULL COMMENT '最近结果是否可重试' AFTER last_handling_suggestion,
    ADD COLUMN last_manual_intervention_required VARCHAR(16) NULL COMMENT '最近结果是否需要人工介入' AFTER last_retryable,
    ADD COLUMN last_mapping_version VARCHAR(32) NULL COMMENT '最近命中的返回码映射版本' AFTER last_manual_intervention_required,
    ADD COLUMN last_mapping_matched TINYINT(1) NULL COMMENT '最近结果是否命中启用未归档映射' AFTER last_mapping_version;
