USE housekeeping_payment_core;

CREATE TABLE IF NOT EXISTS t_payment_return_code_import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '导入审计批次号',
    operator_name VARCHAR(128) NOT NULL COMMENT '操作人自声明',
    change_reason VARCHAR(256) NOT NULL COMMENT '导入原因',
    source_sha256 VARCHAR(64) NOT NULL COMMENT 'CSV 原文 SHA-256',
    baseline_fingerprint VARCHAR(64) NOT NULL COMMENT '应用前配置基线指纹',
    result_fingerprint VARCHAR(64) NOT NULL COMMENT '应用后配置指纹',
    total_count INT NOT NULL DEFAULT 0 COMMENT 'CSV 数据行数',
    create_count INT NOT NULL DEFAULT 0 COMMENT '新增行数',
    update_count INT NOT NULL DEFAULT 0 COMMENT '更新行数',
    unchanged_count INT NOT NULL DEFAULT 0 COMMENT '未变化行数',
    applied_count INT NOT NULL DEFAULT 0 COMMENT '实际写入行数',
    batch_status VARCHAR(32) NOT NULL COMMENT 'APPLIED 或 NO_CHANGE',
    batch_status_type VARCHAR(32) NOT NULL COMMENT '批次状态样式',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_return_code_import_batch_no (batch_no),
    KEY idx_return_code_import_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道返回码批量导入审计批次';
