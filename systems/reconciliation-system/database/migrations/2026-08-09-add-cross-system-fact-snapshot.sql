CREATE TABLE IF NOT EXISTS t_cross_system_fact_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    business_chain_id VARCHAR(96) NOT NULL COMMENT '跨系统业务链路ID',
    system_code VARCHAR(64) NOT NULL COMMENT '来源系统编码',
    fact_type VARCHAR(64) NOT NULL COMMENT '事实类型',
    fact_no VARCHAR(128) NOT NULL COMMENT '来源事实编号',
    business_no VARCHAR(128) NOT NULL COMMENT '来源业务编号',
    fact_status VARCHAR(64) NOT NULL COMMENT '事实状态',
    amount DECIMAL(18,2) NULL COMMENT '事实金额',
    snapshot_version BIGINT NULL COMMENT '来源配置快照版本',
    occurred_at DATETIME NOT NULL COMMENT '事实发生时间',
    payload TEXT NULL COMMENT '事实摘要或脱敏载荷',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cross_system_fact (business_chain_id, system_code, fact_type, fact_no),
    KEY idx_cross_system_chain (business_chain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨系统业务事实快照表';
