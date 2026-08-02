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
COMMENT ON TABLE t_wallet_owner IS '钱包主体表';
COMMENT ON COLUMN t_wallet_owner.wallet_owner_id IS '钱包主体编号';
COMMENT ON COLUMN t_wallet_owner.owner_type IS '主体类型';
COMMENT ON COLUMN t_wallet_owner.owner_name IS '主体名称';
COMMENT ON COLUMN t_wallet_owner.owner_status IS '主体状态';
COMMENT ON COLUMN t_wallet_owner.biz_line_code IS '业务线编码';
COMMENT ON COLUMN t_wallet_owner.tenant_code IS '租户编码';
COMMENT ON COLUMN t_wallet_owner.ext_ref_no IS '外部主体编号';

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
COMMENT ON TABLE t_wallet_account IS '钱包账户表';
COMMENT ON COLUMN t_wallet_account.wallet_account_no IS '钱包账户编号';
COMMENT ON COLUMN t_wallet_account.wallet_owner_id IS '钱包主体编号';
COMMENT ON COLUMN t_wallet_account.account_type IS '账户类型';
COMMENT ON COLUMN t_wallet_account.account_scene IS '账户场景';
COMMENT ON COLUMN t_wallet_account.account_status IS '账户状态';
COMMENT ON COLUMN t_wallet_account.total_balance IS '总余额';
COMMENT ON COLUMN t_wallet_account.available_balance IS '可用余额';
COMMENT ON COLUMN t_wallet_account.frozen_balance IS '冻结余额';
COMMENT ON COLUMN t_wallet_account.pending_in_balance IS '在途入账金额';
COMMENT ON COLUMN t_wallet_account.pending_out_balance IS '在途出账金额';

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
CREATE UNIQUE INDEX uk_wallet_flow_idempotency_key ON t_wallet_flow(idempotency_key);
CREATE INDEX idx_wallet_flow_account_created ON t_wallet_flow(wallet_account_no, created_at DESC);
COMMENT ON TABLE t_wallet_flow IS '钱包流水表';
COMMENT ON COLUMN t_wallet_flow.flow_no IS '钱包流水编号';
COMMENT ON COLUMN t_wallet_flow.wallet_account_no IS '钱包账户编号';
COMMENT ON COLUMN t_wallet_flow.flow_type IS '流水类型';
COMMENT ON COLUMN t_wallet_flow.source_system IS '来源系统';
COMMENT ON COLUMN t_wallet_flow.source_biz_no IS '来源业务单号';
COMMENT ON COLUMN t_wallet_flow.idempotency_key IS '幂等键';
COMMENT ON COLUMN t_wallet_flow.change_amount IS '变更金额';
COMMENT ON COLUMN t_wallet_flow.before_available_balance IS '变更前可用余额';
COMMENT ON COLUMN t_wallet_flow.after_available_balance IS '变更后可用余额';
COMMENT ON COLUMN t_wallet_flow.operator_name IS '操作人名称';
COMMENT ON COLUMN t_wallet_flow.operation_reason IS '操作原因';

CREATE TABLE t_wallet_balance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wallet_account_no VARCHAR(64) NOT NULL,
    total_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    available_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    pending_in_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    pending_out_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    version_no BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_wallet_balance_account_no ON t_wallet_balance(wallet_account_no);
COMMENT ON TABLE t_wallet_balance IS '钱包余额表';
COMMENT ON COLUMN t_wallet_balance.wallet_account_no IS '钱包账户编号';
COMMENT ON COLUMN t_wallet_balance.total_balance IS '总余额';
COMMENT ON COLUMN t_wallet_balance.available_balance IS '可用余额';
COMMENT ON COLUMN t_wallet_balance.frozen_balance IS '冻结余额';
COMMENT ON COLUMN t_wallet_balance.pending_in_balance IS '在途入账金额';
COMMENT ON COLUMN t_wallet_balance.pending_out_balance IS '在途出账金额';
COMMENT ON COLUMN t_wallet_balance.version_no IS '乐观锁版本号';

CREATE TABLE t_wallet_account_status_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wallet_account_no VARCHAR(64) NOT NULL,
    before_status VARCHAR(32),
    after_status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64),
    reason_desc VARCHAR(255),
    operator_id VARCHAR(64),
    operator_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_status_log_account_created ON t_wallet_account_status_log(wallet_account_no, created_at DESC);
COMMENT ON TABLE t_wallet_account_status_log IS '钱包账户状态变更日志表';
COMMENT ON COLUMN t_wallet_account_status_log.wallet_account_no IS '钱包账户编号';
COMMENT ON COLUMN t_wallet_account_status_log.before_status IS '变更前状态';
COMMENT ON COLUMN t_wallet_account_status_log.after_status IS '变更后状态';
COMMENT ON COLUMN t_wallet_account_status_log.reason_code IS '变更原因编码';
COMMENT ON COLUMN t_wallet_account_status_log.reason_desc IS '变更原因说明';
COMMENT ON COLUMN t_wallet_account_status_log.operator_id IS '操作人编号';
COMMENT ON COLUMN t_wallet_account_status_log.operator_name IS '操作人名称';

CREATE TABLE t_wallet_flow_export_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    export_task_no VARCHAR(64) NOT NULL,
    wallet_account_no VARCHAR(64),
    source_system VARCHAR(64),
    source_biz_no VARCHAR(64),
    operator_id VARCHAR(64),
    operator_name VARCHAR(64) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_wallet_flow_export_task_no ON t_wallet_flow_export_task(export_task_no);
COMMENT ON TABLE t_wallet_flow_export_task IS '钱包流水导出任务表';
COMMENT ON COLUMN t_wallet_flow_export_task.export_task_no IS '导出任务编号';
COMMENT ON COLUMN t_wallet_flow_export_task.wallet_account_no IS '钱包账户编号筛选条件';
COMMENT ON COLUMN t_wallet_flow_export_task.source_system IS '来源系统筛选条件';
COMMENT ON COLUMN t_wallet_flow_export_task.source_biz_no IS '来源业务单号筛选条件';
COMMENT ON COLUMN t_wallet_flow_export_task.operator_id IS '操作人编号';
COMMENT ON COLUMN t_wallet_flow_export_task.operator_name IS '操作人名称';
COMMENT ON COLUMN t_wallet_flow_export_task.task_status IS '任务状态';

CREATE TABLE t_wallet_idempotent_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    biz_type VARCHAR(64) NOT NULL,
    idempotent_key VARCHAR(128) NOT NULL,
    result_ref_no VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_wallet_idempotent_request_no ON t_wallet_idempotent_record(request_no);
CREATE UNIQUE INDEX uk_wallet_idempotent_key ON t_wallet_idempotent_record(idempotent_key);
COMMENT ON TABLE t_wallet_idempotent_record IS '钱包幂等记录表';
COMMENT ON COLUMN t_wallet_idempotent_record.request_no IS '请求号';
COMMENT ON COLUMN t_wallet_idempotent_record.biz_type IS '业务类型';
COMMENT ON COLUMN t_wallet_idempotent_record.idempotent_key IS '幂等键';
COMMENT ON COLUMN t_wallet_idempotent_record.result_ref_no IS '结果引用号';
COMMENT ON COLUMN t_wallet_idempotent_record.status IS '处理状态';
