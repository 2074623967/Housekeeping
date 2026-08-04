CREATE TABLE IF NOT EXISTS t_deposit_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    account_no VARCHAR(64) NOT NULL COMMENT '保证金账户号',
    owner_id VARCHAR(64) NOT NULL COMMENT '资金主体编号',
    owner_type VARCHAR(32) NOT NULL COMMENT '资金主体类型',
    required_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '应缴金额',
    balance DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '保证金余额',
    frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '冻结金额',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '账户状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE (account_no),
    UNIQUE (owner_id, owner_type)
);

CREATE TABLE IF NOT EXISTS t_deposit_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    flow_no VARCHAR(64) NOT NULL COMMENT '保证金流水号',
    account_no VARCHAR(64) NOT NULL COMMENT '保证金账户号',
    flow_type VARCHAR(32) NOT NULL COMMENT '流水类型',
    amount DECIMAL(18,2) NOT NULL COMMENT '发生金额',
    before_balance DECIMAL(18,2) NOT NULL COMMENT '变更前余额',
    after_balance DECIMAL(18,2) NOT NULL COMMENT '变更后余额',
    before_frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '变更前冻结金额',
    after_frozen_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '变更后冻结金额',
    reference_no VARCHAR(128) COMMENT '业务关联号',
    remark VARCHAR(500) COMMENT '备注',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE (flow_no),
    INDEX idx_deposit_flow_account (account_no)
);
