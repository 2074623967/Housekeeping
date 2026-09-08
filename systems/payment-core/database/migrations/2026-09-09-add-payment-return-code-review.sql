USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_return_code_review (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_no VARCHAR(64) NOT NULL COMMENT '复核申请编号',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码快照',
    channel_return_code VARCHAR(64) NOT NULL COMMENT '渠道返回码快照',
    standardized_code VARCHAR(64) NOT NULL COMMENT '标准错误码快照',
    standardized_message VARCHAR(256) NOT NULL COMMENT '标准错误文案快照',
    mapping_version VARCHAR(32) NOT NULL COMMENT '映射版本快照',
    pending_mapping_key VARCHAR(64) DEFAULT NULL COMMENT '映射唯一待办摘要，完成后置空',
    base_fingerprint VARCHAR(64) NOT NULL COMMENT '提交时完整映射基线指纹',
    candidate_status VARCHAR(32) NOT NULL COMMENT '候选 ENABLED/DISABLED 状态',
    candidate_status_type VARCHAR(32) NOT NULL COMMENT '候选状态样式',
    requester VARCHAR(64) NOT NULL COMMENT '申请人自声明',
    request_reason VARCHAR(255) NOT NULL COMMENT '申请原因',
    review_status VARCHAR(32) NOT NULL COMMENT 'PENDING_REVIEW/APPROVED/REJECTED',
    review_status_type VARCHAR(32) NOT NULL COMMENT '复核状态样式',
    reviewer VARCHAR(64) DEFAULT NULL COMMENT '复核人自声明',
    review_comment VARCHAR(255) DEFAULT NULL COMMENT '复核意见或驳回原因',
    created_at DATETIME NOT NULL COMMENT '申请时间',
    reviewed_at DATETIME DEFAULT NULL COMMENT '复核时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_return_code_review_request (request_no),
    UNIQUE KEY uk_return_code_review_pending (pending_mapping_key),
    KEY idx_return_code_review_status (review_status, created_at),
    KEY idx_return_code_review_mapping (channel_code, channel_return_code, review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道返回码单条变更复核申请表';
