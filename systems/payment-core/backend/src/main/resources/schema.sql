DROP TABLE IF EXISTS t_dashboard_card;
DROP TABLE IF EXISTS t_payment_event;
DROP TABLE IF EXISTS t_payment_route_record;
DROP TABLE IF EXISTS t_payment_notify_log;
DROP TABLE IF EXISTS t_payment_attempt;
DROP TABLE IF EXISTS t_prepay_order;
DROP TABLE IF EXISTS t_bill;
DROP TABLE IF EXISTS t_worker_settlement_order;
DROP TABLE IF EXISTS t_refund_order;
DROP TABLE IF EXISTS t_payment_order;
DROP TABLE IF EXISTS t_order;

CREATE TABLE t_dashboard_card (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    card_key VARCHAR(64) NOT NULL COMMENT '卡片业务编码',
    title VARCHAR(128) NOT NULL COMMENT '卡片标题',
    value VARCHAR(64) NOT NULL COMMENT '卡片展示值',
    badge_type VARCHAR(32) NOT NULL COMMENT '角标样式类型',
    badge_text VARCHAR(128) NOT NULL COMMENT '角标文案',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_key (card_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台卡片';

CREATE TABLE t_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号',
    customer_name VARCHAR(128) NOT NULL COMMENT '客户名称',
    service_type VARCHAR(64) NOT NULL COMMENT '服务品类',
    worker_name VARCHAR(128) NOT NULL COMMENT '服务者名称',
    order_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '订单应收金额',
    paid_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '订单已支付金额',
    order_status VARCHAR(32) NOT NULL COMMENT '订单状态',
    order_status_type VARCHAR(32) NOT NULL COMMENT '订单状态样式类型',
    fulfillment_status VARCHAR(32) NOT NULL COMMENT '履约状态',
    fulfillment_status_type VARCHAR(32) NOT NULL COMMENT '履约状态样式类型',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE t_payment_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '支付单号',
    order_no VARCHAR(64) NOT NULL COMMENT '关联订单号',
    customer_name VARCHAR(128) NOT NULL COMMENT '客户名称',
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '本次支付金额',
    payment_method VARCHAR(32) NOT NULL COMMENT '支付方式',
    channel_code VARCHAR(64) NOT NULL COMMENT '支付渠道编码',
    channel_transaction_no VARCHAR(128) NOT NULL COMMENT '渠道侧交易号',
    status VARCHAR(32) NOT NULL COMMENT '支付状态',
    status_type VARCHAR(32) NOT NULL COMMENT '支付状态样式类型',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_id (payment_order_id),
    KEY idx_payment_order_no (order_no),
    KEY idx_payment_status_created (status, created_at),
    KEY idx_payment_channel_created (channel_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';

CREATE TABLE t_bill (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    bill_no VARCHAR(64) NOT NULL COMMENT '账单号',
    order_no VARCHAR(64) NOT NULL COMMENT '关联订单号',
    customer_name VARCHAR(128) NOT NULL COMMENT '客户名称',
    bill_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '账单应收金额',
    paid_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '账单已支付金额',
    bill_status VARCHAR(32) NOT NULL COMMENT '账单状态',
    bill_status_type VARCHAR(32) NOT NULL COMMENT '账单状态样式类型',
    due_at DATETIME NOT NULL COMMENT '账单到期时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_no (bill_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单表';

CREATE TABLE t_prepay_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    prepay_order_no VARCHAR(64) NOT NULL COMMENT '预付单号',
    bill_no VARCHAR(64) NOT NULL COMMENT '关联账单号',
    order_no VARCHAR(64) NOT NULL COMMENT '关联订单号',
    customer_name VARCHAR(128) NOT NULL COMMENT '客户名称',
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '预付金额',
    pay_scene VARCHAR(32) NOT NULL COMMENT '支付场景',
    cashier_title VARCHAR(128) NOT NULL COMMENT '收银台标题',
    cashier_status VARCHAR(32) NOT NULL COMMENT '收银台状态',
    cashier_status_type VARCHAR(32) NOT NULL COMMENT '收银台状态样式类型',
    payment_order_id VARCHAR(64) DEFAULT NULL COMMENT '关联支付单号',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    expires_at DATETIME NOT NULL COMMENT '失效时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prepay_order_no (prepay_order_no),
    KEY idx_prepay_expire_payment (expires_at, payment_order_id),
    KEY idx_prepay_order_created (order_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预付单表';

CREATE TABLE t_payment_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    attempt_no VARCHAR(64) NOT NULL COMMENT '支付尝试号',
    prepay_order_no VARCHAR(64) NOT NULL COMMENT '关联预付单号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '关联支付单号',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    payment_method VARCHAR(32) NOT NULL COMMENT '支付方式',
    request_payload VARCHAR(2048) NOT NULL COMMENT '请求报文',
    response_payload VARCHAR(2048) DEFAULT NULL COMMENT '响应报文',
    attempt_status VARCHAR(32) NOT NULL COMMENT '尝试状态',
    attempt_status_type VARCHAR(32) NOT NULL COMMENT '尝试状态样式类型',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt_no (attempt_no),
    KEY idx_attempt_payment_created (payment_order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付尝试表';

CREATE TABLE t_payment_notify_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    notify_no VARCHAR(64) NOT NULL COMMENT '回调日志号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '关联支付单号',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    notify_type VARCHAR(32) NOT NULL COMMENT '回调类型',
    notify_payload VARCHAR(2048) NOT NULL COMMENT '回调报文',
    notify_result VARCHAR(2048) DEFAULT NULL COMMENT '处理结果报文',
    notify_status VARCHAR(32) NOT NULL COMMENT '回调处理状态',
    notify_status_type VARCHAR(32) NOT NULL COMMENT '回调状态样式类型',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notify_no (notify_no),
    KEY idx_notify_payment_created (payment_order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志表';

CREATE TABLE t_payment_route_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    route_no VARCHAR(64) NOT NULL COMMENT '路由记录号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '关联支付单号',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    route_rule VARCHAR(128) NOT NULL COMMENT '路由规则描述',
    route_result VARCHAR(128) NOT NULL COMMENT '路由结果描述',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_no (route_no),
    KEY idx_route_payment_created (payment_order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付路由记录表';

CREATE TABLE t_payment_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    event_no VARCHAR(64) NOT NULL COMMENT '事件号',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '关联支付单号',
    biz_no VARCHAR(64) NOT NULL COMMENT '关联业务单号',
    event_payload VARCHAR(2048) NOT NULL COMMENT '事件报文',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_no (event_no),
    KEY idx_event_payment_created (payment_order_id, created_at),
    KEY idx_event_biz_created (biz_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付事件表';

CREATE TABLE t_refund_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    refund_order_id VARCHAR(64) NOT NULL COMMENT '退款单号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '关联支付单号',
    order_no VARCHAR(64) NOT NULL COMMENT '关联订单号',
    customer_name VARCHAR(128) NOT NULL COMMENT '客户名称',
    refund_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '退款金额',
    refund_method VARCHAR(32) NOT NULL COMMENT '退款方式',
    status VARCHAR(32) NOT NULL COMMENT '退款状态',
    status_type VARCHAR(32) NOT NULL COMMENT '退款状态样式类型',
    applied_at DATETIME NOT NULL COMMENT '申请时间',
    success_at DATETIME DEFAULT NULL COMMENT '退款成功时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_order_id (refund_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

CREATE TABLE t_worker_settlement_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    settlement_order_id VARCHAR(64) NOT NULL COMMENT '服务者结算单号',
    worker_name VARCHAR(128) NOT NULL COMMENT '服务者名称',
    period_start DATE NOT NULL COMMENT '结算开始日期',
    period_end DATE NOT NULL COMMENT '结算结束日期',
    amount_should_settle DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '应结金额',
    deduct_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '扣减金额',
    amount_net_settle DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '实结金额',
    deposit_impact_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '保证金影响金额',
    status VARCHAR(32) NOT NULL COMMENT '结算单状态',
    status_type VARCHAR(32) NOT NULL COMMENT '结算单状态样式类型',
    payout_status VARCHAR(32) NOT NULL COMMENT '出款状态',
    payout_status_type VARCHAR(32) NOT NULL COMMENT '出款状态样式类型',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_order_id (settlement_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务者结算单表';
