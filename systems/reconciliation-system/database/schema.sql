CREATE DATABASE IF NOT EXISTS housekeeping_reconciliation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE housekeeping_reconciliation;

CREATE TABLE IF NOT EXISTS t_reconciliation_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_no VARCHAR(64) NOT NULL COMMENT '对账批次号',
    business_date DATE NOT NULL COMMENT '业务日期',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    bill_source VARCHAR(64) NOT NULL COMMENT '账单来源',
    status VARCHAR(32) NOT NULL COMMENT '批次状态',
    channel_count INT NOT NULL DEFAULT 0 COMMENT '渠道记录数',
    internal_count INT NOT NULL DEFAULT 0 COMMENT '平台记录数',
    matched_count INT NOT NULL DEFAULT 0 COMMENT '已匹配记录数',
    difference_count INT NOT NULL DEFAULT 0 COMMENT '差异记录数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_batch_no (batch_no),
    KEY idx_recon_batch_business_date (business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账批次表';

CREATE TABLE IF NOT EXISTS t_reconciliation_channel_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_no VARCHAR(64) NOT NULL COMMENT '对账批次号',
    channel_trade_no VARCHAR(128) NOT NULL COMMENT '渠道交易流水号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '平台支付单号',
    amount DECIMAL(18,2) NOT NULL COMMENT '渠道金额',
    trade_status VARCHAR(32) NOT NULL COMMENT '渠道交易状态',
    trade_time DATETIME NOT NULL COMMENT '渠道交易时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_channel_trade (batch_no, channel_trade_no),
    KEY idx_recon_channel_payment (batch_no, payment_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道账单明细表';

CREATE TABLE IF NOT EXISTS t_reconciliation_internal_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_no VARCHAR(64) NOT NULL COMMENT '对账批次号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '平台支付单号',
    amount DECIMAL(18,2) NOT NULL COMMENT '平台金额',
    internal_status VARCHAR(32) NOT NULL COMMENT '平台支付状态',
    source_system VARCHAR(64) NOT NULL COMMENT '来源系统',
    paid_time DATETIME NOT NULL COMMENT '支付成功时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_internal_payment (batch_no, payment_order_id),
    KEY idx_recon_internal_payment (batch_no, payment_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台支付事实表';

CREATE TABLE IF NOT EXISTS t_reconciliation_difference (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    difference_no VARCHAR(64) NOT NULL COMMENT '差异编号',
    batch_no VARCHAR(64) NOT NULL COMMENT '对账批次号',
    difference_type VARCHAR(32) NOT NULL COMMENT '差异类型',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '平台支付单号',
    channel_amount DECIMAL(18,2) NULL COMMENT '渠道金额',
    internal_amount DECIMAL(18,2) NULL COMMENT '平台金额',
    status VARCHAR(32) NOT NULL COMMENT '差异状态',
    resolution VARCHAR(128) NULL COMMENT '处置结论',
    remark VARCHAR(500) NULL COMMENT '处置备注',
    detected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
    resolved_at DATETIME NULL COMMENT '结案时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_difference_no (difference_no),
    KEY idx_recon_difference_batch (batch_no),
    KEY idx_recon_difference_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账差异表';

