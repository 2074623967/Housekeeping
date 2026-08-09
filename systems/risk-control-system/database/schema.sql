CREATE DATABASE IF NOT EXISTS housekeeping_risk_control DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE housekeeping_risk_control;

CREATE TABLE IF NOT EXISTS t_risk_policy (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    policy_code VARCHAR(64) NOT NULL COMMENT '策略编码',
    policy_name VARCHAR(128) NOT NULL COMMENT '策略名称',
    risk_dimension VARCHAR(64) NOT NULL COMMENT '风险维度',
    hit_action VARCHAR(64) NOT NULL COMMENT '命中动作',
    risk_level VARCHAR(64) NOT NULL COMMENT '风险等级',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_policy_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险策略表';

CREATE TABLE IF NOT EXISTS t_risk_limit_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    target_type VARCHAR(64) NOT NULL COMMENT '适用对象',
    scene_code VARCHAR(64) NOT NULL COMMENT '场景编码',
    limit_value VARCHAR(64) NOT NULL COMMENT '限额值',
    time_window VARCHAR(64) NOT NULL COMMENT '时间窗',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_limit_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限额规则表';

CREATE TABLE IF NOT EXISTS t_risk_blocklist (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    block_code VARCHAR(64) NOT NULL COMMENT '名单编码',
    subject_value VARCHAR(128) NOT NULL COMMENT '主体值',
    subject_type VARCHAR(64) NOT NULL COMMENT '主体类型',
    reason VARCHAR(255) NOT NULL COMMENT '命中原因',
    action_type VARCHAR(64) NOT NULL COMMENT '处置动作',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_block_code (block_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黑名单表';

CREATE TABLE IF NOT EXISTS t_risk_intercept_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_no VARCHAR(64) NOT NULL COMMENT '事件编号',
    payment_order_id VARCHAR(64) NOT NULL COMMENT '支付单号',
    hit_policy VARCHAR(64) NOT NULL COMMENT '命中策略',
    risk_level VARCHAR(64) NOT NULL COMMENT '风险等级',
    decision_result VARCHAR(64) NOT NULL COMMENT '处置结果',
    source_system VARCHAR(64) NOT NULL COMMENT '来源系统',
    happened_at DATETIME NOT NULL COMMENT '发生时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_event_no (event_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险拦截事件表';

CREATE TABLE IF NOT EXISTS t_risk_review_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    review_no VARCHAR(64) NOT NULL COMMENT '复核单号',
    business_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    risk_tag VARCHAR(128) NOT NULL COMMENT '风险标签',
    review_item VARCHAR(255) NOT NULL COMMENT '待审事项',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    reviewer VARCHAR(64) NULL COMMENT '审核人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_review_no (review_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工复核单表';

CREATE TABLE IF NOT EXISTS t_risk_monitor_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    monitor_code VARCHAR(64) NOT NULL COMMENT '规则编码',
    monitor_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    monitor_target VARCHAR(128) NOT NULL COMMENT '监控对象',
    alert_threshold VARCHAR(64) NOT NULL COMMENT '告警阈值',
    notify_policy VARCHAR(128) NOT NULL COMMENT '通知策略',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_monitor_code (monitor_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控规则表';

