CREATE TABLE IF NOT EXISTS t_wallet_owner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    wallet_owner_id VARCHAR(64) NOT NULL COMMENT '钱包主体编号',
    owner_type VARCHAR(32) NOT NULL COMMENT '主体类型',
    owner_name VARCHAR(128) NOT NULL COMMENT '主体名称',
    owner_status VARCHAR(32) NOT NULL COMMENT '主体状态',
    biz_line_code VARCHAR(64) COMMENT '业务线编码',
    tenant_code VARCHAR(64) COMMENT '租户编码',
    ext_ref_no VARCHAR(64) COMMENT '外部主体编号',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wallet_owner_id ON t_wallet_owner(wallet_owner_id);

CREATE TABLE IF NOT EXISTS t_wallet_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    wallet_account_no VARCHAR(64) NOT NULL COMMENT '钱包账户编号',
    wallet_owner_id VARCHAR(64) NOT NULL COMMENT '钱包主体编号',
    owner_type VARCHAR(32) NOT NULL COMMENT '主体类型',
    owner_name VARCHAR(128) NOT NULL COMMENT '主体名称',
    account_type VARCHAR(32) NOT NULL COMMENT '账户类型',
    account_scene VARCHAR(32) NOT NULL COMMENT '账户场景',
    currency_code VARCHAR(16) NOT NULL COMMENT '币种',
    account_status VARCHAR(32) NOT NULL COMMENT '账户状态',
    allow_credit TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许透支',
    risk_level VARCHAR(32) NOT NULL COMMENT '风险等级',
    total_balance DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '总余额',
    available_balance DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '可用余额',
    frozen_balance DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '冻结余额',
    pending_in_balance DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '在途入账余额',
    pending_out_balance DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '在途出账余额',
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开户时间',
    closed_at TIMESTAMP NULL COMMENT '销户时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wallet_account_no ON t_wallet_account(wallet_account_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wallet_account_owner_type_scene
    ON t_wallet_account(wallet_owner_id, account_type, account_scene);

CREATE TABLE IF NOT EXISTS t_wallet_flow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    flow_no VARCHAR(64) NOT NULL COMMENT '流水编号',
    wallet_account_no VARCHAR(64) NOT NULL COMMENT '钱包账户编号',
    flow_type VARCHAR(32) NOT NULL COMMENT '流水类型',
    source_system VARCHAR(64) NOT NULL COMMENT '来源系统',
    source_biz_no VARCHAR(64) NOT NULL COMMENT '来源业务单号',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    change_amount DECIMAL(18, 2) NOT NULL COMMENT '变更金额',
    before_available_balance DECIMAL(18, 2) NOT NULL COMMENT '变更前可用余额',
    after_available_balance DECIMAL(18, 2) NOT NULL COMMENT '变更后可用余额',
    operator_name VARCHAR(64) NOT NULL COMMENT '操作人',
    operation_reason VARCHAR(255) NOT NULL COMMENT '操作原因',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wallet_flow_no ON t_wallet_flow(flow_no);
CREATE INDEX IF NOT EXISTS idx_wallet_flow_account_created ON t_wallet_flow(wallet_account_no, created_at DESC);
