CREATE DATABASE IF NOT EXISTS housekeeping_deposit DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE housekeeping_deposit;

CREATE TABLE IF NOT EXISTS t_deposit_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_no VARCHAR(64) NOT NULL COMMENT '保证金账户号',
    owner_id VARCHAR(64) NOT NULL COMMENT '资金主体编号',
    owner_type VARCHAR(32) NOT NULL COMMENT '资金主体类型：WORKER/MERCHANT/PLATFORM',
    required_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '应缴金额',
    balance DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '保证金余额',
    frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '冻结金额',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '账户状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_deposit_account_no (account_no),
    UNIQUE KEY uk_deposit_owner (owner_id, owner_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保证金账户表';

CREATE TABLE IF NOT EXISTS t_deposit_flow (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    flow_no VARCHAR(64) NOT NULL COMMENT '保证金流水号',
    account_no VARCHAR(64) NOT NULL COMMENT '保证金账户号',
    flow_type VARCHAR(32) NOT NULL COMMENT '流水类型：COLLECT/FREEZE/UNFREEZE/DEDUCT/REFUND/OFFSET_DEBT',
    amount DECIMAL(18,2) NOT NULL COMMENT '发生金额',
    before_balance DECIMAL(18,2) NOT NULL COMMENT '变更前余额',
    after_balance DECIMAL(18,2) NOT NULL COMMENT '变更后余额',
    before_frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '变更前冻结金额',
    after_frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '变更后冻结金额',
    reference_no VARCHAR(128) NULL COMMENT '业务关联号',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_deposit_flow_no (flow_no),
    KEY idx_deposit_flow_account_time (account_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保证金流水表';
