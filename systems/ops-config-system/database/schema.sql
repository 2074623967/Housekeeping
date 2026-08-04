CREATE DATABASE IF NOT EXISTS housekeeping_ops_config DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE housekeeping_ops_config;

CREATE TABLE IF NOT EXISTS t_ops_agreement_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    subject_type VARCHAR(64) NOT NULL COMMENT '适用主体',
    sign_fields VARCHAR(255) NOT NULL COMMENT '签约要素',
    esign_provider VARCHAR(128) NOT NULL COMMENT '电子签章服务商',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_agreement_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协议模板表';

CREATE TABLE IF NOT EXISTS t_ops_business_line (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    business_code VARCHAR(64) NOT NULL COMMENT '业务线编码',
    business_name VARCHAR(128) NOT NULL COMMENT '业务线名称',
    default_scene VARCHAR(128) NOT NULL COMMENT '默认支付场景',
    owner VARCHAR(128) NOT NULL COMMENT '负责人',
    settlement_policy VARCHAR(128) NOT NULL COMMENT '清结算策略',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_business_code (business_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务线表';

CREATE TABLE IF NOT EXISTS t_ops_payment_type (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    type_code VARCHAR(64) NOT NULL COMMENT '支付类型编码',
    type_name VARCHAR(128) NOT NULL COMMENT '支付类型名称',
    transaction_category VARCHAR(64) NOT NULL COMMENT '交易大类',
    fee_policy VARCHAR(128) NOT NULL COMMENT '计费口径',
    refund_capability VARCHAR(128) NOT NULL COMMENT '退款能力',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_payment_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付类型表';

CREATE TABLE IF NOT EXISTS t_ops_cashier_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    terminal_type VARCHAR(64) NOT NULL COMMENT '适用终端',
    default_pay_method VARCHAR(64) NOT NULL COMMENT '默认支付方式',
    display_policy VARCHAR(128) NOT NULL COMMENT '展示策略',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_cashier_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收银台模板表';

CREATE TABLE IF NOT EXISTS t_ops_channel_profile (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    channel_name VARCHAR(128) NOT NULL COMMENT '渠道名称',
    channel_type VARCHAR(64) NOT NULL COMMENT '渠道类型',
    merchant_profile VARCHAR(128) NOT NULL COMMENT '商户号模板',
    refund_sla VARCHAR(128) NOT NULL COMMENT '退款时效',
    risk_tag VARCHAR(128) NOT NULL COMMENT '风控标签',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_channel_profile_code (channel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道档案表';

CREATE TABLE IF NOT EXISTS t_ops_routing_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    route_code VARCHAR(64) NOT NULL COMMENT '路由编码',
    business_code VARCHAR(64) NOT NULL COMMENT '业务线编码',
    pay_type VARCHAR(64) NOT NULL COMMENT '支付类型',
    primary_channel VARCHAR(64) NOT NULL COMMENT '优先渠道',
    backup_channel VARCHAR(64) NOT NULL COMMENT '备选渠道',
    match_policy VARCHAR(128) NOT NULL COMMENT '命中策略',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_route_code (route_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路由规则表';

CREATE TABLE IF NOT EXISTS t_ops_system_control (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    control_code VARCHAR(64) NOT NULL COMMENT '控制编码',
    control_name VARCHAR(128) NOT NULL COMMENT '控制名称',
    control_scope VARCHAR(128) NOT NULL COMMENT '控制范围',
    control_value VARCHAR(128) NOT NULL COMMENT '控制值',
    risk_level VARCHAR(64) NOT NULL COMMENT '风险级别',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_control_code (control_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统控制表';
