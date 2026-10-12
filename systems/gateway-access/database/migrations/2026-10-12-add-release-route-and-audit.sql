CREATE TABLE IF NOT EXISTS t_gateway_release_route (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    route_code VARCHAR(64) NOT NULL COMMENT '路由编码',
    gateway_code VARCHAR(64) NOT NULL COMMENT '网关编码',
    environment VARCHAR(32) NOT NULL COMMENT '环境',
    release_strategy VARCHAR(64) NOT NULL COMMENT '发布策略',
    traffic_percent INT NOT NULL COMMENT '流量百分比',
    release_window VARCHAR(128) NOT NULL COMMENT '发布窗口',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    status_type VARCHAR(32) NOT NULL COMMENT '状态样式',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_release_route_code (route_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关灰度发布路由表';

CREATE TABLE IF NOT EXISTS t_gateway_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_id VARCHAR(64) NOT NULL COMMENT '请求流水号',
    app_code VARCHAR(64) NOT NULL COMMENT '应用编码',
    gateway_code VARCHAR(64) NOT NULL COMMENT '网关编码',
    operation_type VARCHAR(64) NOT NULL COMMENT '操作类型',
    sign_type VARCHAR(64) NOT NULL COMMENT '签名算法',
    client_ip VARCHAR(64) NOT NULL COMMENT '客户端IP',
    result_status VARCHAR(32) NOT NULL COMMENT '结果状态',
    result_status_type VARCHAR(32) NOT NULL COMMENT '结果状态样式',
    risk_hint VARCHAR(255) NOT NULL COMMENT '风险提示',
    happened_at DATETIME NOT NULL COMMENT '发生时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关调用审计日志表';
