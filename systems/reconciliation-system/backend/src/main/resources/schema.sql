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

