CREATE TABLE t_wallet_owner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wallet_owner_id VARCHAR(64) NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_name VARCHAR(128) NOT NULL,
    owner_status VARCHAR(32) NOT NULL,
    biz_line_code VARCHAR(64),
    tenant_code VARCHAR(64),
    ext_ref_no VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_wallet_owner_id ON t_wallet_owner(wallet_owner_id);

CREATE TABLE t_wallet_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wallet_account_no VARCHAR(64) NOT NULL,
    wallet_owner_id VARCHAR(64) NOT NULL,
    owner_type VARCHAR(32) NOT NULL,
    owner_name VARCHAR(128) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    account_scene VARCHAR(32) NOT NULL,
    currency_code VARCHAR(16) NOT NULL,
    account_status VARCHAR(32) NOT NULL,
    allow_credit TINYINT NOT NULL DEFAULT 0,
    risk_level VARCHAR(32) NOT NULL,
    total_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    available_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    pending_in_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    pending_out_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_wallet_account_no ON t_wallet_account(wallet_account_no);
CREATE UNIQUE INDEX uk_wallet_account_owner_type_scene ON t_wallet_account(wallet_owner_id, account_type, account_scene);

CREATE TABLE t_wallet_flow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flow_no VARCHAR(64) NOT NULL,
    wallet_account_no VARCHAR(64) NOT NULL,
    flow_type VARCHAR(32) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_biz_no VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    change_amount DECIMAL(18, 2) NOT NULL,
    before_available_balance DECIMAL(18, 2) NOT NULL,
    after_available_balance DECIMAL(18, 2) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    operation_reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_wallet_flow_no ON t_wallet_flow(flow_no);
CREATE INDEX idx_wallet_flow_account_created ON t_wallet_flow(wallet_account_no, created_at DESC);
