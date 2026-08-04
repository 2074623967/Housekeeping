CREATE TABLE IF NOT EXISTS t_refund_payment_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '原支付单号',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    customer_name VARCHAR(128) COMMENT '客户名称',
    paid_amount DECIMAL(18,2) NOT NULL COMMENT '已支付金额',
    channel_code VARCHAR(64) COMMENT '支付渠道',
    paid_at TIMESTAMP NOT NULL COMMENT '支付成功时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_refund_payment_source_payment UNIQUE (payment_order_id)
);

CREATE TABLE IF NOT EXISTS t_refund_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    refund_order_id VARCHAR(64) NOT NULL COMMENT '退款单号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '原支付单号',
    order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    customer_name VARCHAR(128) COMMENT '客户名称',
    paid_amount DECIMAL(18,2) NOT NULL COMMENT '原支付金额',
    refund_amount DECIMAL(18,2) NOT NULL COMMENT '退款金额',
    refund_method VARCHAR(32) NOT NULL COMMENT '退款方式',
    refund_reason VARCHAR(255) NOT NULL COMMENT '退款原因',
    status VARCHAR(32) NOT NULL COMMENT '退款状态',
    channel_refund_id VARCHAR(128) COMMENT '渠道退款流水号',
    failure_code VARCHAR(64) COMMENT '渠道失败码',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    approved_at TIMESTAMP NULL COMMENT '审核时间',
    submitted_at TIMESTAMP NULL COMMENT '提交渠道时间',
    success_at TIMESTAMP NULL COMMENT '成功时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_refund_order_id UNIQUE (refund_order_id),
    CONSTRAINT uk_refund_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_refund_order_payment ON t_refund_order(payment_order_id);
CREATE INDEX idx_refund_order_status ON t_refund_order(status);

CREATE TABLE IF NOT EXISTS t_refund_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    log_no VARCHAR(64) NOT NULL COMMENT '操作流水号',
    refund_order_id VARCHAR(64) NOT NULL COMMENT '退款单号',
    action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
    action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
    from_status VARCHAR(32) COMMENT '原状态',
    to_status VARCHAR(32) NOT NULL COMMENT '新状态',
    operator_name VARCHAR(128) NOT NULL COMMENT '操作人',
    operation_remark VARCHAR(500) COMMENT '操作备注',
    operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    CONSTRAINT uk_refund_log_no UNIQUE (log_no)
);

CREATE TABLE IF NOT EXISTS t_refund_outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件编号',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合编号',
    payload_json CLOB NOT NULL COMMENT '事件载荷',
    status VARCHAR(32) NOT NULL COMMENT '发送状态',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_refund_outbox_event_id UNIQUE (event_id)
);

