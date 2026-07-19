DROP TABLE IF EXISTS t_dashboard_card;
DROP TABLE IF EXISTS t_worker_settlement_order;
DROP TABLE IF EXISTS t_refund_order;
DROP TABLE IF EXISTS t_payment_order;
DROP TABLE IF EXISTS t_order;

CREATE TABLE t_dashboard_card (
    id BIGINT NOT NULL AUTO_INCREMENT,
    card_key VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    value VARCHAR(64) NOT NULL,
    badge_type VARCHAR(32) NOT NULL,
    badge_text VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_key (card_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台卡片';

CREATE TABLE t_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    worker_name VARCHAR(128) NOT NULL,
    order_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    paid_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    order_status VARCHAR(32) NOT NULL,
    order_status_type VARCHAR(32) NOT NULL,
    fulfillment_status VARCHAR(32) NOT NULL,
    fulfillment_status_type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE t_payment_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_order_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(32) NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    channel_transaction_no VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    status_type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_id (payment_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';

CREATE TABLE t_refund_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    refund_order_id VARCHAR(64) NOT NULL,
    payment_order_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    refund_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    refund_method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    status_type VARCHAR(32) NOT NULL,
    applied_at DATETIME NOT NULL,
    success_at DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_order_id (refund_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

CREATE TABLE t_worker_settlement_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_order_id VARCHAR(64) NOT NULL,
    worker_name VARCHAR(128) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    amount_should_settle DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    deduct_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    amount_net_settle DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    deposit_impact_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(32) NOT NULL,
    status_type VARCHAR(32) NOT NULL,
    payout_status VARCHAR(32) NOT NULL,
    payout_status_type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_order_id (settlement_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务者结算单表';
