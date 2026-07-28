DROP TABLE IF EXISTS t_wallet_recharge_order;
DROP TABLE IF EXISTS t_wallet_transfer_order;
DROP TABLE IF EXISTS t_wallet_withdraw_order;
DROP TABLE IF EXISTS t_wallet_balance_payment_order;
DROP TABLE IF EXISTS t_wallet_ledger;
DROP TABLE IF EXISTS t_wallet_account;

CREATE TABLE t_wallet_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    account_no VARCHAR(64) NOT NULL COMMENT '钱包账户号',
    owner_name VARCHAR(128) NOT NULL COMMENT '用户名称',
    wallet_type VARCHAR(32) NOT NULL COMMENT '钱包类型',
    status VARCHAR(32) NOT NULL COMMENT '账户状态',
    available_amount DECIMAL(18, 2) NOT NULL COMMENT '可用余额',
    frozen_amount DECIMAL(18, 2) NOT NULL COMMENT '冻结余额',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_account_no (account_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包账户表';

CREATE TABLE t_wallet_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    ledger_no VARCHAR(64) NOT NULL COMMENT '流水号',
    account_no VARCHAR(64) NOT NULL COMMENT '钱包账户号',
    biz_type VARCHAR(64) NOT NULL COMMENT '业务类型',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    amount DECIMAL(18, 2) NOT NULL COMMENT '金额',
    direction VARCHAR(16) NOT NULL COMMENT '方向',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_ledger_no (ledger_no),
    KEY idx_wallet_ledger_account (account_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水表';

CREATE TABLE t_wallet_recharge_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    recharge_no VARCHAR(64) NOT NULL COMMENT '充值单号',
    account_no VARCHAR(64) NOT NULL COMMENT '钱包账户号',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    amount DECIMAL(18, 2) NOT NULL COMMENT '充值金额',
    status VARCHAR(32) NOT NULL COMMENT '处理状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_recharge_no (recharge_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包充值单表';

CREATE TABLE t_wallet_withdraw_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    withdraw_no VARCHAR(64) NOT NULL COMMENT '提现单号',
    account_no VARCHAR(64) NOT NULL COMMENT '钱包账户号',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    amount DECIMAL(18, 2) NOT NULL COMMENT '提现金额',
    status VARCHAR(32) NOT NULL COMMENT '处理状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_withdraw_no (withdraw_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包提现单表';

CREATE TABLE t_wallet_transfer_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    transfer_no VARCHAR(64) NOT NULL COMMENT '转账单号',
    source_account_no VARCHAR(64) NOT NULL COMMENT '转出账户号',
    target_account_no VARCHAR(64) NOT NULL COMMENT '转入账户号',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    amount DECIMAL(18, 2) NOT NULL COMMENT '转账金额',
    status VARCHAR(32) NOT NULL COMMENT '处理状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_transfer_no (transfer_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包转账单表';

CREATE TABLE t_wallet_balance_payment_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    balance_payment_no VARCHAR(64) NOT NULL COMMENT '余额支付单号',
    account_no VARCHAR(64) NOT NULL COMMENT '钱包账户号',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    amount DECIMAL(18, 2) NOT NULL COMMENT '支付金额',
    status VARCHAR(32) NOT NULL COMMENT '处理状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_balance_payment_no (balance_payment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包余额支付单表';
