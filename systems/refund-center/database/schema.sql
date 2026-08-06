CREATE DATABASE IF NOT EXISTS housekeeping_refund DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE housekeeping_refund;

CREATE TABLE IF NOT EXISTS t_refund_payment_source (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '原支付单号',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    customer_name VARCHAR(128) NULL COMMENT '客户名称',
    paid_amount DECIMAL(18,2) NOT NULL COMMENT '已支付金额',
    channel_code VARCHAR(64) NULL COMMENT '支付渠道',
    paid_at DATETIME NOT NULL COMMENT '支付成功时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_payment_source_payment (payment_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款中心支付成功事实投影表';

CREATE TABLE IF NOT EXISTS t_refund_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    refund_order_id VARCHAR(64) NOT NULL COMMENT '退款单号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '原支付单号',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    customer_name VARCHAR(128) NULL COMMENT '客户名称',
    paid_amount DECIMAL(18,2) NOT NULL COMMENT '原支付金额',
    refund_amount DECIMAL(18,2) NOT NULL COMMENT '退款金额',
    refund_method VARCHAR(32) NOT NULL COMMENT '退款方式',
    refund_reason VARCHAR(255) NOT NULL COMMENT '退款原因',
    status VARCHAR(32) NOT NULL COMMENT '退款状态',
    channel_refund_id VARCHAR(128) NULL COMMENT '渠道退款流水号',
    failure_code VARCHAR(64) NULL COMMENT '渠道失败码',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    approved_at DATETIME NULL COMMENT '审核时间',
    submitted_at DATETIME NULL COMMENT '提交渠道时间',
    success_at DATETIME NULL COMMENT '成功时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_order_id (refund_order_id),
    UNIQUE KEY uk_refund_idempotency (idempotency_key),
    KEY idx_refund_order_payment (payment_order_id),
    KEY idx_refund_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单主表';

CREATE TABLE IF NOT EXISTS t_refund_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    log_no VARCHAR(64) NOT NULL COMMENT '操作流水号',
    refund_order_id VARCHAR(64) NOT NULL COMMENT '退款单号',
    action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
    action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
    from_status VARCHAR(32) NULL COMMENT '原状态',
    to_status VARCHAR(32) NOT NULL COMMENT '新状态',
    operator_name VARCHAR(128) NOT NULL COMMENT '操作人',
    operation_remark VARCHAR(500) NULL COMMENT '操作备注',
    operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_log_no (log_no),
    KEY idx_refund_log_order (refund_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款操作审计日志表';

CREATE TABLE IF NOT EXISTS t_refund_outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件编号',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合编号',
    payload_json JSON NOT NULL COMMENT '事件载荷',
    status VARCHAR(32) NOT NULL COMMENT '发送状态',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error_message VARCHAR(255) NULL COMMENT '最近一次错误信息',
    last_relay_at DATETIME NULL COMMENT '最近一次派发时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_outbox_event_id (event_id),
    KEY idx_refund_outbox_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款成功事件出站表';
