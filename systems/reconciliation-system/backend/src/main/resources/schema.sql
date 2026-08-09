CREATE TABLE IF NOT EXISTS t_reconciliation_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    batch_no VARCHAR(64) NOT NULL COMMENT '对账批次号',
    business_date DATE NOT NULL COMMENT '业务日期',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    bill_source VARCHAR(64) NOT NULL COMMENT '账单来源',
    status VARCHAR(32) NOT NULL COMMENT '批次状态',
    channel_count INT NOT NULL DEFAULT 0 COMMENT '渠道记录数',
    internal_count INT NOT NULL DEFAULT 0 COMMENT '平台记录数',
    matched_count INT NOT NULL DEFAULT 0 COMMENT '已匹配记录数',
    difference_count INT NOT NULL DEFAULT 0 COMMENT '差异记录数',
    readiness_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' COMMENT '对账准入状态',
    readiness_summary VARCHAR(500) NULL COMMENT '对账准入结论',
    readiness_suggested_action VARCHAR(500) NULL COMMENT '对账准入建议动作',
    readiness_blocking_owner VARCHAR(64) NULL COMMENT '对账准入阻断责任方',
    readiness_chain_count INT NOT NULL DEFAULT 0 COMMENT '纳入准入判断的业务链路数',
    readiness_completed_chain_count INT NOT NULL DEFAULT 0 COMMENT '已完成关键事实链路数',
    readiness_abnormal_chain_count INT NOT NULL DEFAULT 0 COMMENT '存在异常事实的链路数',
    readiness_pending_chain_count INT NOT NULL DEFAULT 0 COMMENT '尚未完成关键事实的链路数',
    readiness_checked_at TIMESTAMP NULL COMMENT '最近一次准入检查时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE (batch_no)
);

CREATE TABLE IF NOT EXISTS t_reconciliation_channel_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    batch_no VARCHAR(64) NOT NULL COMMENT '对账批次号',
    channel_trade_no VARCHAR(128) NOT NULL COMMENT '渠道交易流水号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '平台支付单号',
    amount DECIMAL(18,2) NOT NULL COMMENT '渠道金额',
    trade_status VARCHAR(32) NOT NULL COMMENT '渠道交易状态',
    trade_time TIMESTAMP NOT NULL COMMENT '渠道交易时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE (batch_no, channel_trade_no),
    INDEX idx_recon_channel_payment (batch_no, payment_order_id)
);

CREATE TABLE IF NOT EXISTS t_reconciliation_internal_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    batch_no VARCHAR(64) NOT NULL COMMENT '对账批次号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '平台支付单号',
    amount DECIMAL(18,2) NOT NULL COMMENT '平台金额',
    internal_status VARCHAR(32) NOT NULL COMMENT '平台支付状态',
    source_system VARCHAR(64) NOT NULL COMMENT '来源系统',
    paid_time TIMESTAMP NOT NULL COMMENT '支付成功时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE (batch_no, payment_order_id),
    INDEX idx_recon_internal_payment (batch_no, payment_order_id)
);

CREATE TABLE IF NOT EXISTS t_reconciliation_difference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    difference_no VARCHAR(64) NOT NULL COMMENT '差异编号',
    batch_no VARCHAR(64) NOT NULL COMMENT '对账批次号',
    difference_type VARCHAR(32) NOT NULL COMMENT '差异类型',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '平台支付单号',
    channel_amount DECIMAL(18,2) NULL COMMENT '渠道金额',
    internal_amount DECIMAL(18,2) NULL COMMENT '平台金额',
    status VARCHAR(32) NOT NULL COMMENT '差异状态',
    resolution VARCHAR(128) NULL COMMENT '处置结论',
    remark VARCHAR(500) NULL COMMENT '处置备注',
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
    resolved_at TIMESTAMP NULL COMMENT '结案时间',
    UNIQUE (difference_no),
    INDEX idx_recon_difference_batch (batch_no),
    INDEX idx_recon_difference_status (status)
);

CREATE TABLE IF NOT EXISTS t_cross_system_fact_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    business_chain_id VARCHAR(96) NOT NULL COMMENT '跨系统业务链路ID',
    system_code VARCHAR(64) NOT NULL COMMENT '来源系统编码',
    fact_type VARCHAR(64) NOT NULL COMMENT '事实类型',
    fact_no VARCHAR(128) NOT NULL COMMENT '来源事实编号',
    business_no VARCHAR(128) NOT NULL COMMENT '来源业务编号',
    fact_status VARCHAR(64) NOT NULL COMMENT '事实状态',
    amount DECIMAL(18,2) NULL COMMENT '事实金额',
    snapshot_version BIGINT NULL COMMENT '来源配置快照版本',
    occurred_at TIMESTAMP NOT NULL COMMENT '事实发生时间',
    payload CLOB NULL COMMENT '事实摘要或脱敏载荷',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    UNIQUE (business_chain_id, system_code, fact_type, fact_no),
    INDEX idx_cross_system_chain (business_chain_id),
    INDEX idx_cross_system_system (system_code, fact_type)
);
