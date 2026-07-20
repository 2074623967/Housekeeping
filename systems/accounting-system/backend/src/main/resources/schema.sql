DROP TABLE IF EXISTS t_account_event;
DROP TABLE IF EXISTS t_account_adjustment;
DROP TABLE IF EXISTS t_account_freeze;
DROP TABLE IF EXISTS t_account_ledger;
DROP TABLE IF EXISTS t_account_balance;
DROP TABLE IF EXISTS t_account;
DROP TABLE IF EXISTS t_account_subject;

CREATE TABLE t_account_subject (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    subject_id VARCHAR(64) NOT NULL COMMENT '主体ID',
    subject_type VARCHAR(32) NOT NULL COMMENT '主体类型',
    subject_name VARCHAR(128) NOT NULL COMMENT '主体名称',
    owner_name VARCHAR(64) NOT NULL COMMENT '归属人',
    status VARCHAR(32) NOT NULL COMMENT '主体状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_id (subject_id)
);

CREATE TABLE t_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    account_no VARCHAR(64) NOT NULL COMMENT '账户号',
    subject_id VARCHAR(64) NOT NULL COMMENT '主体ID',
    subject_name VARCHAR(128) NOT NULL COMMENT '主体名称',
    account_type VARCHAR(64) NOT NULL COMMENT '账户类型',
    account_status VARCHAR(32) NOT NULL COMMENT '账户状态',
    currency VARCHAR(16) NOT NULL COMMENT '币种',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    last_change_at DATETIME NOT NULL COMMENT '最近变更时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_no (account_no)
);

CREATE TABLE t_account_balance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    account_no VARCHAR(64) NOT NULL COMMENT '账户号',
    available_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
    frozen_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '冻结余额',
    in_transit_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '在途余额',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_balance_account_no (account_no)
);

CREATE TABLE t_account_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    ledger_no VARCHAR(64) NOT NULL COMMENT '流水号',
    account_no VARCHAR(64) NOT NULL COMMENT '账户号',
    biz_type VARCHAR(64) NOT NULL COMMENT '业务类型',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    direction VARCHAR(16) NOT NULL COMMENT '借贷方向',
    amount DECIMAL(18, 2) NOT NULL COMMENT '变动金额',
    before_balance DECIMAL(18, 2) NOT NULL COMMENT '变动前余额',
    after_balance DECIMAL(18, 2) NOT NULL COMMENT '变动后余额',
    ledger_status VARCHAR(32) NOT NULL COMMENT '流水状态',
    operator_name VARCHAR(64) NOT NULL COMMENT '操作人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_no (ledger_no)
);

CREATE TABLE t_account_freeze (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    freeze_no VARCHAR(64) NOT NULL COMMENT '冻结单号',
    account_no VARCHAR(64) NOT NULL COMMENT '账户号',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    freeze_type VARCHAR(32) NOT NULL COMMENT '冻结类型',
    freeze_reason VARCHAR(256) NOT NULL COMMENT '冻结原因',
    freeze_amount DECIMAL(18, 2) NOT NULL COMMENT '冻结金额',
    freeze_status VARCHAR(32) NOT NULL COMMENT '冻结状态',
    operator_name VARCHAR(64) NOT NULL COMMENT '操作人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    unfrozen_at DATETIME NULL COMMENT '解冻时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_freeze_no (freeze_no)
);

CREATE TABLE t_account_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    adjust_no VARCHAR(64) NOT NULL COMMENT '调账单号',
    account_no VARCHAR(64) NOT NULL COMMENT '账户号',
    adjust_direction VARCHAR(16) NOT NULL COMMENT '调账方向',
    adjust_amount DECIMAL(18, 2) NOT NULL COMMENT '调账金额',
    adjust_reason VARCHAR(256) NOT NULL COMMENT '调账原因',
    adjust_status VARCHAR(32) NOT NULL COMMENT '调账状态',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    approved_by VARCHAR(64) NULL COMMENT '审批人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    approved_at DATETIME NULL COMMENT '审批时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_adjust_no (adjust_no)
);

CREATE TABLE t_account_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_no VARCHAR(64) NOT NULL COMMENT '事件号',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    summary VARCHAR(256) NOT NULL COMMENT '事件摘要',
    payload TEXT NOT NULL COMMENT '事件载荷',
    event_status VARCHAR(32) NOT NULL COMMENT '事件状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_no (event_no)
);
