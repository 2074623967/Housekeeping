DROP TABLE IF EXISTS t_gateway_permission;
DROP TABLE IF EXISTS t_gateway_certificate;
DROP TABLE IF EXISTS t_gateway_channel;
DROP TABLE IF EXISTS t_gateway_app;

CREATE TABLE t_gateway_app (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_code VARCHAR(64) NOT NULL COMMENT '应用编码',
    app_name VARCHAR(128) NOT NULL COMMENT '应用名称',
    source_system VARCHAR(128) NOT NULL COMMENT '来源系统',
    owner VARCHAR(128) NOT NULL COMMENT '负责人',
    ip_whitelist VARCHAR(256) NOT NULL COMMENT 'IP白名单',
    permission_scope VARCHAR(256) NOT NULL COMMENT '权限范围',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_code (app_code)
);

CREATE TABLE t_gateway_channel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    gateway_code VARCHAR(64) NOT NULL COMMENT '网关编码',
    gateway_name VARCHAR(128) NOT NULL COMMENT '网关名称',
    channel_type VARCHAR(64) NOT NULL COMMENT '渠道类型',
    protocol_type VARCHAR(64) NOT NULL COMMENT '协议类型',
    sign_algorithm VARCHAR(64) NOT NULL COMMENT '签名算法',
    endpoint VARCHAR(256) NOT NULL COMMENT '接入地址',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gateway_code (gateway_code)
);

CREATE TABLE t_gateway_certificate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    certificate_code VARCHAR(64) NOT NULL COMMENT '证书编码',
    gateway_code VARCHAR(64) NOT NULL COMMENT '网关编码',
    certificate_version VARCHAR(64) NOT NULL COMMENT '证书版本',
    expire_at DATE NOT NULL COMMENT '到期日',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_certificate_code (certificate_code)
);

CREATE TABLE t_gateway_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    permission_code VARCHAR(64) NOT NULL COMMENT '权限编码',
    app_code VARCHAR(64) NOT NULL COMMENT '应用编码',
    scope VARCHAR(256) NOT NULL COMMENT '权限范围',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code)
);
