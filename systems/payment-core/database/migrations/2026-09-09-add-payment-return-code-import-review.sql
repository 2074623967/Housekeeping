USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_return_code_import_review (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_no VARCHAR(64) NOT NULL COMMENT '复核申请编号',
    pending_import_key VARCHAR(64) DEFAULT NULL COMMENT '唯一待办占位，完成后置空',
    csv_content MEDIUMTEXT NOT NULL COMMENT '规范化后的候选 CSV',
    source_sha256 VARCHAR(64) NOT NULL COMMENT '候选 CSV SHA-256',
    baseline_fingerprint VARCHAR(64) NOT NULL COMMENT '提交时配置基线指纹',
    total_count INT NOT NULL DEFAULT 0 COMMENT 'CSV 数据行数',
    create_count INT NOT NULL DEFAULT 0 COMMENT '新增行数',
    update_count INT NOT NULL DEFAULT 0 COMMENT '更新行数',
    unchanged_count INT NOT NULL DEFAULT 0 COMMENT '未变化行数',
    requester VARCHAR(64) NOT NULL COMMENT '申请人自声明',
    request_reason VARCHAR(256) NOT NULL COMMENT '申请原因',
    review_status VARCHAR(32) NOT NULL COMMENT 'PENDING_REVIEW/APPROVED/REJECTED',
    review_status_type VARCHAR(32) NOT NULL COMMENT '复核状态样式',
    reviewer VARCHAR(64) DEFAULT NULL COMMENT '复核人自声明',
    review_comment VARCHAR(256) DEFAULT NULL COMMENT '复核意见或驳回原因',
    applied_batch_no VARCHAR(64) DEFAULT NULL COMMENT '批准后生成的导入审计批次号',
    reviewed_at DATETIME NULL COMMENT '复核时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_return_code_import_review_request (request_no),
    UNIQUE KEY uk_return_code_import_review_pending (pending_import_key),
    KEY idx_return_code_import_review_status (review_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道返回码导入复核申请表';
