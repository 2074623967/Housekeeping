DROP TABLE IF EXISTS t_dashboard_card;
DROP TABLE IF EXISTS t_payment_event;
DROP TABLE IF EXISTS t_payment_callback_nonce;
DROP TABLE IF EXISTS t_payment_route_record;
DROP TABLE IF EXISTS t_payment_notify_log;
DROP TABLE IF EXISTS t_payment_attempt;
DROP TABLE IF EXISTS t_prepay_order;
DROP TABLE IF EXISTS t_bill;
DROP TABLE IF EXISTS t_worker_settlement_order;
DROP TABLE IF EXISTS t_refund_order;
DROP TABLE IF EXISTS t_payment_day_end_batch;
DROP TABLE IF EXISTS t_payment_task_run_log;
DROP TABLE IF EXISTS t_payment_order;
DROP TABLE IF EXISTS t_payment_channel_return_code_map;
DROP TABLE IF EXISTS t_payment_gateway_config;
DROP TABLE IF EXISTS t_payment_protocol_config;
DROP TABLE IF EXISTS t_payment_route_rule_config;
DROP TABLE IF EXISTS t_payment_channel_config;
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

CREATE TABLE t_payment_channel_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '支付渠道编码',
    channel_name VARCHAR(128) NOT NULL COMMENT '支付渠道名称',
    payment_method VARCHAR(64) NOT NULL COMMENT '支付方式',
    merchant_no VARCHAR(128) NOT NULL COMMENT '渠道商户号',
    callback_secret VARCHAR(256) NOT NULL COMMENT '渠道回调验签密钥',
    callback_notify_url VARCHAR(256) NOT NULL COMMENT '渠道回调通知地址',
    scene_scope VARCHAR(128) NOT NULL COMMENT '适用场景范围',
    status VARCHAR(32) NOT NULL COMMENT '渠道状态',
    status_type VARCHAR(32) NOT NULL COMMENT '渠道状态样式类型',
    daily_limit DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '单日限额',
    priority INT NOT NULL DEFAULT 0 COMMENT '渠道优先级，数字越小优先级越高',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_code (channel_code),
    KEY idx_channel_method_status (payment_method, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付渠道配置表';

CREATE TABLE t_payment_callback_nonce (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    nonce VARCHAR(128) NOT NULL COMMENT '回调随机串',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '支付单号',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_nonce (channel_code, nonce),
    KEY idx_nonce_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调防重放随机串表';

CREATE TABLE t_payment_route_rule_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '路由规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '路由规则名称',
    match_scene VARCHAR(128) NOT NULL COMMENT '匹配业务场景',
    match_expression VARCHAR(512) NOT NULL COMMENT '匹配表达式',
    target_channel_code VARCHAR(64) NOT NULL COMMENT '目标渠道编码',
    fallback_channel_code VARCHAR(64) NOT NULL COMMENT '兜底渠道编码',
    status VARCHAR(32) NOT NULL COMMENT '规则状态',
    status_type VARCHAR(32) NOT NULL COMMENT '规则状态样式类型',
    priority INT NOT NULL DEFAULT 0 COMMENT '规则优先级，数字越小优先级越高',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_code (rule_code),
    KEY idx_route_scene_status (match_scene, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付路由规则配置表';

CREATE TABLE t_payment_protocol_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    protocol_code VARCHAR(64) NOT NULL COMMENT '支付协议编码',
    protocol_name VARCHAR(128) NOT NULL COMMENT '支付协议名称',
    protocol_type VARCHAR(64) NOT NULL COMMENT '协议类型',
    protocol_type_name VARCHAR(64) NOT NULL COMMENT '协议类型名称',
    template_code VARCHAR(64) NOT NULL COMMENT '协议模板编码',
    template_name VARCHAR(128) NOT NULL COMMENT '协议模板名称',
    template_version VARCHAR(32) NOT NULL COMMENT '模板版本',
    sign_mode VARCHAR(64) NOT NULL COMMENT '签约模式',
    sign_element_spec VARCHAR(255) NOT NULL COMMENT '签约要素配置',
    e_signature_provider VARCHAR(64) NOT NULL COMMENT '电子签章服务商',
    scene_scope VARCHAR(128) NOT NULL COMMENT '适用场景',
    channel_scope VARCHAR(128) NOT NULL COMMENT '适用渠道范围',
    merchant_ack_required VARCHAR(32) NOT NULL COMMENT '是否要求商户确认',
    risk_control_tag VARCHAR(128) NOT NULL COMMENT '风控标签',
    protocol_body TEXT NOT NULL COMMENT '协议正文内容',
    status VARCHAR(32) NOT NULL COMMENT '协议状态',
    status_type VARCHAR(32) NOT NULL COMMENT '协议状态样式类型',
    priority INT NOT NULL DEFAULT 0 COMMENT '协议优先级，数字越小优先级越高',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_protocol_code (protocol_code),
    KEY idx_protocol_type_status (protocol_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付协议配置表';

CREATE TABLE t_payment_channel_return_code_map (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    channel_return_code VARCHAR(64) NOT NULL COMMENT '渠道返回码',
    standardized_code VARCHAR(64) NOT NULL COMMENT '统一标准错误码',
    standardized_message VARCHAR(256) NOT NULL COMMENT '统一标准错误文案',
    handling_suggestion VARCHAR(256) NOT NULL COMMENT '处理建议',
    retryable VARCHAR(16) NOT NULL COMMENT '是否可重试',
    status VARCHAR(32) NOT NULL COMMENT '映射状态',
    status_type VARCHAR(32) NOT NULL COMMENT '映射状态样式类型',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级，数字越小越优先',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_return_code (channel_code, channel_return_code),
    KEY idx_standardized_code_status (standardized_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道返回码映射配置表';

CREATE TABLE t_payment_gateway_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    gateway_code VARCHAR(64) NOT NULL COMMENT '支付网关编码',
    gateway_name VARCHAR(128) NOT NULL COMMENT '支付网关名称',
    access_mode VARCHAR(64) NOT NULL COMMENT '接入模式，如直连或服务商模式',
    channel_scope VARCHAR(256) NOT NULL COMMENT '适用渠道范围',
    api_base_url VARCHAR(256) NOT NULL COMMENT '网关基础地址',
    protocol_type VARCHAR(64) NOT NULL COMMENT '报文协议类型',
    sign_algorithm VARCHAR(64) NOT NULL COMMENT '签名算法',
    timeout_ms INT NOT NULL DEFAULT 3000 COMMENT '超时时间毫秒值',
    retry_policy VARCHAR(128) NOT NULL COMMENT '失败重试策略',
    status VARCHAR(32) NOT NULL COMMENT '网关状态',
    status_type VARCHAR(32) NOT NULL COMMENT '网关状态样式类型',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级，数字越小越优先',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gateway_code (gateway_code),
    KEY idx_gateway_status_priority (status, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付网关接入配置表';

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
    terminal VARCHAR(32) NOT NULL COMMENT '发起终端',
    client_ip VARCHAR(64) NOT NULL COMMENT '客户端IP',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    request_payload VARCHAR(2048) NOT NULL COMMENT '请求报文',
    response_payload VARCHAR(2048) DEFAULT NULL COMMENT '响应报文',
    attempt_status VARCHAR(32) NOT NULL COMMENT '尝试状态',
    attempt_status_type VARCHAR(32) NOT NULL COMMENT '尝试状态样式类型',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt_no (attempt_no),
    UNIQUE KEY uk_attempt_idempotency (idempotency_key),
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
    downstream_system VARCHAR(256) NOT NULL COMMENT '下游系统范围',
    event_topic VARCHAR(128) NOT NULL COMMENT '事件主题',
    publish_status VARCHAR(32) NOT NULL COMMENT '事件发布状态',
    publish_status_type VARCHAR(32) NOT NULL COMMENT '事件发布状态样式类型',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '事件重试次数',
    last_published_at DATETIME DEFAULT NULL COMMENT '最近发布时间',
    next_retry_at DATETIME DEFAULT NULL COMMENT '下次重试时间',
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
    refund_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '退款原因',
    status VARCHAR(32) NOT NULL COMMENT '退款状态',
    status_type VARCHAR(32) NOT NULL COMMENT '退款状态样式类型',
    applied_at DATETIME NOT NULL COMMENT '申请时间',
    success_at DATETIME DEFAULT NULL COMMENT '退款成功时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_order_id (refund_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

CREATE TABLE t_refund_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    log_no VARCHAR(64) NOT NULL COMMENT '退款操作日志号',
    refund_order_id VARCHAR(64) NOT NULL COMMENT '关联退款单号',
    action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
    action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
    from_status VARCHAR(32) NOT NULL COMMENT '原状态',
    to_status VARCHAR(32) NOT NULL COMMENT '目标状态',
    operator_name VARCHAR(64) NOT NULL COMMENT '操作人',
    operation_remark VARCHAR(255) NOT NULL DEFAULT '' COMMENT '操作备注',
    created_at DATETIME NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_operation_log_no (log_no),
    KEY idx_refund_operation_refund_created (refund_order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款操作日志表';

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

CREATE TABLE t_payment_day_end_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '日终批次号',
    biz_date DATE NOT NULL COMMENT '业务日期',
    run_mode VARCHAR(32) NOT NULL COMMENT '运行方式',
    batch_status VARCHAR(32) NOT NULL COMMENT '批次状态',
    batch_status_type VARCHAR(32) NOT NULL COMMENT '批次状态样式类型',
    payment_total_count INT NOT NULL DEFAULT 0 COMMENT '支付总单量',
    payment_success_count INT NOT NULL DEFAULT 0 COMMENT '支付成功单量',
    payment_success_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '支付成功金额',
    channel_success_count INT NOT NULL DEFAULT 0 COMMENT '渠道成功收口单量',
    channel_success_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '渠道成功收口金额',
    internal_success_count INT NOT NULL DEFAULT 0 COMMENT '内部事件成功发布单量',
    internal_success_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '内部事件成功发布金额',
    payment_success_gap_count INT NOT NULL DEFAULT 0 COMMENT '支付成功差异单量',
    payment_success_gap_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '支付成功差异金额',
    refund_success_count INT NOT NULL DEFAULT 0 COMMENT '退款成功单量',
    refund_success_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '退款成功金额',
    channel_abnormal_count INT NOT NULL DEFAULT 0 COMMENT '渠道异常数',
    internal_abnormal_count INT NOT NULL DEFAULT 0 COMMENT '内部异常数',
    pending_refund_count INT NOT NULL DEFAULT 0 COMMENT '待收口退款数',
    pending_refund_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '待收口退款金额',
    summary_comment VARCHAR(512) NOT NULL COMMENT '执行备注',
    triggered_by VARCHAR(64) NOT NULL COMMENT '触发人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    completed_at DATETIME NOT NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_day_end_batch_no (batch_no),
    KEY idx_day_end_biz_date_status (biz_date, batch_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付日终批次表';

CREATE TABLE t_payment_task_run_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    task_log_no VARCHAR(64) NOT NULL COMMENT '任务日志号',
    task_code VARCHAR(64) NOT NULL COMMENT '任务编码',
    task_name VARCHAR(128) NOT NULL COMMENT '任务名称',
    run_mode VARCHAR(32) NOT NULL COMMENT '运行方式',
    task_status VARCHAR(32) NOT NULL COMMENT '任务状态',
    task_status_type VARCHAR(32) NOT NULL COMMENT '任务状态样式类型',
    severity_level VARCHAR(32) NOT NULL DEFAULT 'P3' COMMENT '任务严重等级',
    severity_level_type VARCHAR(32) NOT NULL DEFAULT 'success' COMMENT '任务严重等级样式',
    escalation_status VARCHAR(32) NOT NULL DEFAULT '正常' COMMENT '任务升级状态',
    escalation_status_type VARCHAR(32) NOT NULL DEFAULT 'success' COMMENT '任务升级状态样式',
    processed_count INT NOT NULL DEFAULT 0 COMMENT '处理总数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功数',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
    summary_comment VARCHAR(512) NOT NULL COMMENT '执行摘要',
    suggested_action VARCHAR(255) NOT NULL DEFAULT '' COMMENT '建议动作',
    recommended_route VARCHAR(255) NOT NULL DEFAULT '' COMMENT '推荐跳转路由',
    triggered_by VARCHAR(64) NOT NULL COMMENT '触发人',
    started_at DATETIME NOT NULL COMMENT '开始时间',
    completed_at DATETIME NOT NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_log_no (task_log_no),
    KEY idx_task_code_started_at (task_code, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付任务执行日志表';
